package cn.edu.shmtu.terminal.android.data.p2p

import android.content.Context
import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.export.ImportDataUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Enumeration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for P2P networking.
 * Manages server lifecycle, sessions, bill export/import, and QR payload generation.
 */
@Singleton
class P2PManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val sessionStore: P2PSessionStore,
    private val billRepository: BillRepository,
    private val billDbManager: BillDatabaseManager,
    private val identityRepository: IdentityRepository,
    private val importDataUseCase: ImportDataUseCase
) {

    data class PendingIncomingTransfer(
        val sessionId: String,
        val transferId: String,
        val totalSize: Long,
        val billCount: Int
    )

    private val tag = "P2PManager"

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val server = P2PServer(scope)

    private val _status = MutableStateFlow(P2PStatus())
    val status: StateFlow<P2PStatus> = _status.asStateFlow()

    private val _pairRequests = MutableStateFlow<List<P2PPairRequest>>(emptyList())
    val pairRequests: StateFlow<List<P2PPairRequest>> = _pairRequests.asStateFlow()

    private val _transferProgress = MutableStateFlow<List<P2PTransferProgress>>(emptyList())
    val transferProgress: StateFlow<List<P2PTransferProgress>> = _transferProgress.asStateFlow()

    // SharedFlow for pending import events (replaces polling)
    private val _pendingImport = MutableSharedFlow<P2PPendingImport>(extraBufferCapacity = 1)
    val pendingImport: SharedFlow<P2PPendingImport> = _pendingImport.asSharedFlow()

    // Thread-safe session storage
    private val sessions = ConcurrentHashMap<String, P2PSession>()

    // Active client connections — keyed by sessionId so sendBills can reuse them
    private val activeClients = ConcurrentHashMap<String, P2PClient>()
    private val pendingIncomingTransfers = ConcurrentHashMap<String, PendingIncomingTransfer>()

    // Encryption keys per session — zeroed on disconnect for forward secrecy
    private val encryptionKeys = ConcurrentHashMap<String, ByteArray>()

    private var currentInfo: P2PInfo? = null
    private var serverJob: Job? = null

    private var deviceName: String = android.os.Build.MODEL ?: "SHMTU Device"
    private var serverPort: Int = P2PProtocol.DEFAULT_PORT

    init {
        sessionStore.loadSessions().forEach { session ->
            sessions[session.sessionId] = session
        }
        _status.value = _status.value.copy(
            sessions = sessions.values.sortedByDescending { it.createdAt }
        )

        server.setCallback(object : P2PServerCallback {
            override fun onPairRequest(
                remoteAddr: String,
                deviceName: String,
                pairCode: String,
                reconnectIps: List<String>,
                reconnectPort: Int?
            ) {
                Log.d(tag, "Pair request from $remoteAddr: device=$deviceName")
                val request = P2PPairRequest(
                    remoteAddr = remoteAddr,
                    remoteDevice = deviceName,
                    pairCode = pairCode,
                    reconnectIps = reconnectIps,
                    reconnectPort = reconnectPort
                )
                _pairRequests.value = _pairRequests.value + request
            }

            override fun onTransferReceived(
                sessionId: String,
                fileName: String,
                data: ByteArray,
                billCount: Int
            ) {
                Log.i(tag, "Transfer received: session=$sessionId file=$fileName bytes=${data.size} bills=$billCount")
                // Emit pending import event so UI can choose target identity
                // Copy the data to prevent mutation of the received buffer
                _pendingImport.tryEmit(
                    P2PPendingImport(
                        sessionId = sessionId,
                        fileName = fileName,
                        data = data.copyOf(),
                        billCount = billCount
                    )
                )
                updateTransferProgress(
                    sessionId,
                    fileName,
                    data.size.toLong(),
                    data.size.toLong(),
                    TransferDirection.RECEIVE,
                    stage = TransferStage.COMPLETED,
                    status = TransferStatus.SUCCESS,
                    detail = "接收完成，等待导入"
                )
            }

            override fun onTransferProgress(
                sessionId: String,
                fileName: String,
                bytesTransferred: Long,
                totalBytes: Long
            ) {
                updateTransferProgress(
                    sessionId,
                    fileName,
                    bytesTransferred,
                    totalBytes,
                    TransferDirection.RECEIVE,
                    stage = if (bytesTransferred >= totalBytes && totalBytes > 0) TransferStage.VERIFYING else TransferStage.TRANSFERRING,
                    status = TransferStatus.RUNNING,
                    detail = if (bytesTransferred >= totalBytes && totalBytes > 0) "数据接收完成，正在校验" else "正在接收数据"
                )
            }

            override fun onPairAccepted(
                sessionId: String,
                remoteAddr: String,
                remoteDeviceName: String,
                pairCode: String,
                reconnectIps: List<String>,
                reconnectPort: Int?
            ) {
                val existing = sessions[sessionId]
                if (existing != null) {
                    sessions[sessionId] = existing.copy(
                        remoteAddr = remoteAddr,
                        remoteDevice = remoteDeviceName,
                        pairCode = pairCode,
                        reconnectIps = reconnectIps,
                        reconnectPort = reconnectPort,
                        isConnected = true
                    )
                    _status.value = _status.value.copy(sessions = sessions.values.toList())
                    persistSessions()
                    return
                }
                val session = P2PSession(
                    sessionId = sessionId,
                    remoteDevice = remoteDeviceName,
                    remoteAddr = remoteAddr,
                    remotePort = serverPort,
                    pairCode = pairCode,
                    reconnectIps = reconnectIps,
                    reconnectPort = reconnectPort,
                    isLocallyInitiated = false,
                    isPaired = true,
                    isConnected = true
                )
                sessions[sessionId] = session
                persistSessions()
                _status.value = _status.value.copy(sessions = sessions.values.toList())
            }

            override fun findTrustedSession(
                remoteAddr: String,
                remoteDeviceName: String,
                pairCode: String
            ): String? = findTrustedSessionId(remoteAddr, remoteDeviceName, pairCode)

            override fun takePendingIncomingTransfer(
                sessionId: String,
                transferId: String
            ): PendingIncomingTransfer? = this@P2PManager.takePendingIncomingTransfer(sessionId, transferId)

            override fun onClientConnected(remoteAddr: String) {
                Log.d(tag, "Client connected: $remoteAddr")
            }

            override fun onClientDisconnected(remoteAddr: String) {
                Log.d(tag, "Client disconnected: $remoteAddr")
                val disconnectedSessions = sessions.values
                    .filter { it.remoteAddr == remoteAddr && !it.isLocallyInitiated }
                    .map { it.sessionId }

                if (disconnectedSessions.isEmpty()) {
                    return
                }

                disconnectedSessions.forEach { sessionId ->
                    sessions[sessionId]?.let { session ->
                        sessions[sessionId] = session.copy(isConnected = false)
                        maybeScheduleAutoReconnect(sessionId, sessions[sessionId]!!)
                    }
                    clearEncryptionKey(sessionId)
                    removeActiveClient(sessionId)
                }
                persistSessions()
                _status.value = _status.value.copy(sessions = sessions.values.toList())
            }

            override fun onError(message: String) {
                Log.e(tag, "Server error: $message")
            }
        })
    }

    /**
     * Configure the P2P manager.
     */
    fun configure(deviceName: String, port: Int) {
        this.deviceName = deviceName.ifBlank { android.os.Build.MODEL ?: "SHMTU Device" }
        this.serverPort = port
    }

    fun getNotificationSummary(): String {
        val info = currentInfo
        val connectedCount = sessions.values.count { it.isConnected }
        return if (info != null) {
            "${info.deviceName} · 端口 ${info.port} · 已连接 $connectedCount 台"
        } else {
            "${deviceName} · 端口 $serverPort"
        }
    }

    /**
     * Start the P2P server.
     */
    fun startServer() {
        if (_status.value.isRunning) {
            Log.w(tag, "Server already running")
            return
        }

        val info = P2PInfo(
            deviceName = deviceName,
            ips = getLocalIPs(),
            port = serverPort
        )
        currentInfo = info

        _status.value = P2PStatus(isRunning = true, sessions = sessions.values.toList(), info = info)

        serverJob = scope.launch {
            server.start(
                port = info.port,
                deviceName = info.deviceName,
                expectedPairCode = info.pairCode
            )
            // Server stopped
            _status.value = _status.value.copy(isRunning = false)
        }

        Log.i(tag, "Server started: deviceName=${info.deviceName} port=${info.port}")
    }

    /**
     * Stop the P2P server.
     */
    fun stopServer() {
        server.stop()
        serverJob?.cancel()
        serverJob = null
        clearAllEncryptionKeys()
        clearAllActiveClients()
        currentInfo = null
        sessions.replaceAll { _, session -> session.copy(isConnected = false) }
        persistSessions()
        _status.value = P2PStatus(isRunning = false, sessions = sessions.values.toList(), info = null)
        _pairRequests.value = emptyList()
        Log.i(tag, "Server stopped")
    }

    /**
     * Accept a pending pair request.
     */
    suspend fun acceptPairRequest(remoteAddr: String): Boolean = withContext(Dispatchers.IO) {
        val request = _pairRequests.value.find { it.remoteAddr == remoteAddr }
        if (request == null) {
            Log.w(tag, "acceptPairRequest ignored, no UI request for $remoteAddr")
            return@withContext false
        }
        val sessionId = UUID.randomUUID().toString()
        val accepted = server.acceptPair(remoteAddr, sessionId)

        if (accepted) {
            _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
            Log.i(tag, "acceptPairRequest succeeded for $remoteAddr, session=$sessionId")
            return@withContext true
        }

        Log.e(tag, "acceptPairRequest failed for $remoteAddr")
        false
    }

    /**
     * Reject a pending pair request.
     */
    suspend fun rejectPairRequest(remoteAddr: String): Boolean = withContext(Dispatchers.IO) {
        val rejected = server.rejectPair(remoteAddr)
        if (rejected) {
            _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
            Log.i(tag, "rejectPairRequest succeeded for $remoteAddr")
            return@withContext true
        }
        Log.e(tag, "rejectPairRequest failed for $remoteAddr")
        false
    }

    /**
     * Connect to a remote peer, pair, and negotiate encryption.
     */
    suspend fun connectToPeer(
        host: String,
        port: Int,
        pairCode: String
    ): Result<P2PSession> = withContext(Dispatchers.IO) {
        ensureServerRunning()
        val client = P2PClient()
        try {
            val connectResult = client.connect(host, port)
            if (connectResult.isFailure) {
                return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("连接失败"))
            }

            val pairResult = client.sendPairRequest(
                deviceName = deviceName,
                pairCode = pairCode,
                listenPort = currentInfo?.port,
                listenIps = getLocalIPs()
            )
            if (pairResult.isFailure) {
                client.close()
                return@withContext Result.failure(pairResult.exceptionOrNull() ?: Exception("配对失败"))
            }

            val acceptPayload = pairResult.getOrNull()!!

            // Negotiate encryption — mandatory
            val encryptResult = client.negotiateEncryption(pairCode)
            if (encryptResult.isFailure) {
                client.close()
                return@withContext Result.failure(encryptResult.exceptionOrNull() ?: Exception("加密协商失败"))
            }

            val session = P2PSession(
                sessionId = acceptPayload.sessionId,
                remoteDevice = acceptPayload.deviceName,
                remoteAddr = host,
                remotePort = port,
                pairCode = pairCode,
                reconnectIps = getLocalIPs(),
                reconnectPort = currentInfo?.port,
                isLocallyInitiated = true,
                isPaired = true,
                isConnected = true
            )
            sessions[session.sessionId] = session
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())

            // Store the active client and its encryption key for reuse
            activeClients[session.sessionId] = client
            client.sessionId = session.sessionId
            client.clientCallback = createClientCallback()
            client.encryptionKey?.let { key ->
                encryptionKeys[session.sessionId] = key.copyOf()
            }

            // Start heartbeat for this client connection
            client.startHeartbeat(scope)
            client.startReceiving(scope) {
                handleClientConnectionClosed(session.sessionId)
            }

            Log.i(tag, "Paired with ${session.remoteDevice} at $host, session=${session.sessionId}")
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Connect to peer failed", e)
            client.close()
            Result.failure(e)
        }
    }

    /**
     * Send bills to a paired peer identified by session ID.
     * Reuses an active client connection if available; otherwise creates a new one.
     */
    suspend fun sendBills(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = sessions[sessionId]
            ?: return@withContext Result.failure(Exception("会话不存在"))

        try {
            val exportData = exportAllBills()
            if (exportData.isEmpty()) {
                return@withContext Result.failure(Exception("没有可发送的账单数据"))
            }

            // Try to reuse an existing client connection
            val existingClient = activeClients[sessionId]
            val client: P2PClient
            val isNewConnection: Boolean

            if (existingClient != null) {
                client = existingClient
                isNewConnection = false
            } else if (!session.isLocallyInitiated) {
                markTransferStage(
                    sessionId = sessionId,
                    direction = TransferDirection.SEND,
                    stage = TransferStage.WAITING_REMOTE_ACCEPT,
                    totalBytes = exportData.size.toLong(),
                    detail = "正在等待对方接受"
                )
                val result = server.sendTransfer(
                    sessionId = sessionId,
                    data = exportData,
                    billCount = parseBillCount(exportData),
                    onProgress = { transferred, total ->
                        updateTransferProgress(sessionId, "bills_export.json", transferred, total, TransferDirection.SEND)
                    }
                )
                if (result.isSuccess) {
                    updateTransferProgress(
                        sessionId,
                        "bills_export.json",
                        exportData.size.toLong(),
                        exportData.size.toLong(),
                        TransferDirection.SEND,
                        stage = TransferStage.COMPLETED,
                        status = TransferStatus.SUCCESS,
                        detail = "发送完成"
                    )
                } else {
                    markTransferFailed(
                        sessionId = sessionId,
                        direction = TransferDirection.SEND,
                        detail = result.exceptionOrNull()?.message ?: "发送失败",
                        totalBytes = exportData.size.toLong()
                    )
                }
                return@withContext result
            } else {
                // No active connection — create a new one
                client = P2PClient()
                isNewConnection = true

                val connectResult = client.connect(session.remoteAddr, session.remotePort)
                if (connectResult.isFailure) {
                    return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("连接失败"))
                }

                // Re-pair with the remote
                val pairCode = session.pairCode
                    ?: return@withContext Result.failure(Exception("当前会话缺少配对码，无法重新建立发送连接"))
                val pairResult = client.sendPairRequest(
                    deviceName = deviceName,
                    pairCode = pairCode,
                    listenPort = currentInfo?.port,
                    listenIps = getLocalIPs()
                )
                if (pairResult.isFailure) {
                    client.close()
                    return@withContext Result.failure(Exception("配对失败"))
                }

                // Negotiate encryption
                val encryptResult = client.negotiateEncryption(pairCode)
                if (encryptResult.isFailure) {
                    client.close()
                    return@withContext Result.failure(encryptResult.exceptionOrNull() ?: Exception("加密协商失败"))
                }

                // Store the active client and its encryption key
                activeClients[sessionId] = client
                client.sessionId = sessionId
                client.clientCallback = createClientCallback()
                client.encryptionKey?.let { key ->
                    encryptionKeys[sessionId] = key.copyOf()
                }

            }

            // Use transferId (UUID) separate from sessionId for the Rust-aligned protocol
            val transferId = UUID.randomUUID().toString()
            markTransferStage(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                stage = TransferStage.WAITING_REMOTE_ACCEPT,
                totalBytes = exportData.size.toLong(),
                detail = "正在等待对方接受"
            )

            // Avoid racing on the same socket input stream while waiting for transfer accept/reject.
            client.stopReceiving(notifyDisconnected = false)

            // Send transfer offer
            val offerResult = client.sendTransferOffer(
                transferId = transferId,
                totalSize = exportData.size.toLong(),
                billCount = parseBillCount(exportData)
            )

            if (offerResult.isFailure || offerResult.getOrNull() != true) {
                if (isNewConnection) {
                    client.close()
                    activeClients.remove(sessionId, client)
                    clearEncryptionKey(sessionId)
                }
                if (!isNewConnection) {
                    client.startReceiving(scope) {
                        handleClientConnectionClosed(sessionId)
                    }
                }
                markTransferFailed(
                    sessionId = sessionId,
                    direction = TransferDirection.SEND,
                    detail = offerResult.exceptionOrNull()?.message ?: "传输被拒绝",
                    totalBytes = exportData.size.toLong()
                )
                return@withContext Result.failure(Exception("传输被拒绝"))
            }

            markTransferStage(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                stage = TransferStage.OPENING_CHANNEL,
                totalBytes = exportData.size.toLong(),
                detail = "正在建立传输通道"
            )

            // Send data with progress tracking
            val sendResult = client.sendTransferData(
                transferId = transferId,
                data = exportData,
                host = session.remoteAddr,
                port = session.remotePort,
                pairCode = session.pairCode ?: "",
                onProgress = { transferred, total ->
                    updateTransferProgress(
                        sessionId,
                        "bills_export.json",
                        transferred,
                        total,
                        TransferDirection.SEND,
                        stage = if (transferred >= total && total > 0) TransferStage.VERIFYING else TransferStage.TRANSFERRING,
                        status = TransferStatus.RUNNING,
                        detail = if (transferred >= total && total > 0) "数据发送完成，等待对方校验" else "正在发送数据"
                    )
                }
            )

            // Only disconnect if this was a fresh connection; keep persistent connections alive
            if (isNewConnection) {
                client.disconnect()
                activeClients.remove(sessionId, client)
                clearEncryptionKey(sessionId)
            } else {
                client.startReceiving(scope) {
                    handleClientConnectionClosed(sessionId)
                }
            }

            if (sendResult.isSuccess) {
                updateTransferProgress(
                    sessionId,
                    "bills_export.json",
                    exportData.size.toLong(),
                    exportData.size.toLong(),
                    TransferDirection.SEND,
                    stage = TransferStage.COMPLETED,
                    status = TransferStatus.SUCCESS,
                    detail = "发送完成"
                )
            } else {
                markTransferFailed(
                    sessionId = sessionId,
                    direction = TransferDirection.SEND,
                    detail = sendResult.exceptionOrNull()?.message ?: "发送失败",
                    totalBytes = exportData.size.toLong()
                )
            }

            sendResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Send bills failed", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnect a paired session. Stops heartbeat, closes the client, and
     * zeroizes the encryption key.
     */
    fun disconnectSession(sessionId: String) {
        val session = sessions[sessionId] ?: return
        if (!session.isConnected) {
            sessions.remove(sessionId)
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())
            return
        }
        removeActiveClient(sessionId)
        clearEncryptionKey(sessionId)
        sessions[sessionId] = session.copy(isConnected = false)
        persistSessions()
        _status.value = _status.value.copy(sessions = sessions.values.toList())
    }

    suspend fun reconnectSession(sessionId: String): Result<P2PSession> = withContext(Dispatchers.IO) {
        val session = sessions[sessionId]
            ?: return@withContext Result.failure(Exception("会话不存在"))
        if (session.isConnected) {
            return@withContext Result.success(session)
        }

        ensureServerRunning()

        val pairCode = session.pairCode
            ?: return@withContext Result.failure(Exception("当前会话缺少配对码，无法重连"))

        val reconnectTarget = resolveReconnectTarget(session)
            ?: return@withContext Result.failure(Exception("当前会话缺少可用的重连地址"))

        val client = P2PClient()
        try {
            val connectResult = client.connect(reconnectTarget.first, reconnectTarget.second)
            if (connectResult.isFailure) {
                return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("连接失败"))
            }

            val pairResult = client.sendPairRequest(
                deviceName = deviceName,
                pairCode = pairCode,
                listenPort = currentInfo?.port,
                listenIps = getLocalIPs()
            )
            if (pairResult.isFailure) {
                client.close()
                return@withContext Result.failure(pairResult.exceptionOrNull() ?: Exception("配对失败"))
            }

            val encryptResult = client.negotiateEncryption(pairCode)
            if (encryptResult.isFailure) {
                client.close()
                return@withContext Result.failure(encryptResult.exceptionOrNull() ?: Exception("加密协商失败"))
            }

            activeClients[sessionId] = client
            client.sessionId = sessionId
            client.clientCallback = createClientCallback()
            client.encryptionKey?.let { key ->
                encryptionKeys[sessionId] = key.copyOf()
            }
            client.startHeartbeat(scope)
            client.startReceiving(scope) {
                handleClientConnectionClosed(sessionId)
            }

            val updated = session.copy(
                isConnected = true,
                remoteAddr = reconnectTarget.first,
                remotePort = reconnectTarget.second
            )
            sessions[sessionId] = updated
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            client.close()
            Result.failure(e)
        }
    }

    /**
     * Import received bills into the specified identity's database.
     */
    suspend fun importBills(data: ByteArray, targetIdentityId: Long): Result<Int> {
        return try {
            val result = importDataUseCase.importFromBytes(data, targetIdentityId)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Import bills failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get all identities available as import targets.
     */
    suspend fun getIdentitiesForImport() = identityRepository.getAllIdentities().first()

    /**
     * Get local IPv4 addresses by enumerating network interfaces.
     */
    fun getLocalIPs(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces: Enumeration<NetworkInterface>? = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (intf in Collections.list(interfaces)) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val addresses: Enumeration<InetAddress>? = intf.inetAddresses
                    if (addresses != null) {
                        for (addr in Collections.list(addresses)) {
                            val hostAddress = addr.hostAddress ?: continue
                            if (!addr.isLoopbackAddress && !hostAddress.contains(":")) {
                                ips.add(hostAddress)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to enumerate network interfaces", e)
        }
        return ips.ifEmpty { listOf("127.0.0.1") }
    }

    /**
     * Generate QR payload JSON string using kotlinx.serialization.
     */
    fun generateQRPayloadJson(): String {
        val info = currentInfo ?: return ""
        val payload = info.toQRPayload()
        return p2pJson.encodeToString(QRPayload.serializer(), payload)
    }

    /**
     * Parse a QR payload JSON string using kotlinx.serialization.
     */
    fun parseQRPayloadJson(jsonStr: String): QRPayload? {
        return try {
            p2pJson.decodeFromString<QRPayload>(jsonStr)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse QR payload", e)
            null
        }
    }

    /**
     * Select the best IP from the QR payload that is on the same subnet as the local device.
     * Returns [IPSelectionResult] with the selected IP and whether it's on the same subnet.
     */
    fun selectBestIp(remoteIps: List<String>): IPSelectionResult {
        val localIps = getLocalIPs()
        val localIp = localIps.firstOrNull { !it.startsWith("127.") }

        // Try same subnet first
        if (localIp != null) {
            val localPrefix = localIp.substringBeforeLast(".")
            val sameSubnet = remoteIps.firstOrNull { it.startsWith("$localPrefix.") }
            if (sameSubnet != null) {
                return IPSelectionResult(ip = sameSubnet, sameSubnet = true)
            }
        }

        // No same-subnet match — pick the first non-loopback, but warn user
        val fallback = remoteIps.firstOrNull { !it.startsWith("127.") } ?: remoteIps.firstOrNull()
        return IPSelectionResult(ip = fallback, sameSubnet = false)
    }

    /**
     * Result of IP selection from QR scan: the chosen IP + whether it's on the same subnet.
     */
    data class IPSelectionResult(
        val ip: String?,
        val sameSubnet: Boolean
    )

    // ============================================================================
    // Encryption key lifecycle
    // ============================================================================

    /**
     * Clear (zeroize and remove) the encryption key for a session.
     */
    fun clearEncryptionKey(sessionId: String) {
        encryptionKeys[sessionId]?.fill(0)
        encryptionKeys.remove(sessionId)
    }

    /**
     * Clear (zeroize and remove) all encryption keys.
     */
    fun clearAllEncryptionKeys() {
        encryptionKeys.values.forEach { it.fill(0) }
        encryptionKeys.clear()
    }

    // ============================================================================
    // Active client lifecycle
    // ============================================================================

    /**
     * Remove and close an active client connection for a session.
     */
    private fun removeActiveClient(sessionId: String) {
        activeClients[sessionId]?.apply {
            stopReceiving()
            stopHeartbeat()
            close()
        }
        activeClients.remove(sessionId)
        pendingIncomingTransfers.entries.removeIf { (_, pending) -> pending.sessionId == sessionId }
    }

    /**
     * Close and remove all active client connections.
     */
    private fun clearAllActiveClients() {
        activeClients.values.forEach { client ->
            client.stopReceiving()
            client.stopHeartbeat()
            client.close()
        }
        activeClients.clear()
    }

    private fun handleClientConnectionClosed(sessionId: String) {
        val existingClient = activeClients[sessionId] ?: return
        if (activeClients.remove(sessionId, existingClient)) {
            clearEncryptionKey(sessionId)
            pendingIncomingTransfers.entries.removeIf { (_, pending) -> pending.sessionId == sessionId }
            sessions[sessionId]?.let { session ->
                sessions[sessionId] = session.copy(isConnected = false)
                _status.value = _status.value.copy(sessions = sessions.values.toList())
                maybeScheduleAutoReconnect(sessionId, sessions[sessionId]!!)
            }
            persistSessions()
            Log.i(tag, "Active client connection closed for session=$sessionId")
        }
    }

    private fun persistSessions() {
        sessionStore.saveSessions(sessions.values)
    }

    private fun maybeScheduleAutoReconnect(sessionId: String, session: P2PSession) {
        if (!settingsDataStore.getP2PAutoReconnectNow()) {
            return
        }
        if (!session.canReconnect) {
            return
        }
        scope.launch {
            kotlinx.coroutines.delay(3_000L)
            val latest = sessions[sessionId] ?: return@launch
            if (latest.isConnected) {
                return@launch
            }
            val result = reconnectSession(sessionId)
            if (result.isSuccess) {
                Log.i(tag, "Auto reconnect succeeded for session=$sessionId")
            } else {
                Log.w(tag, "Auto reconnect failed for session=$sessionId: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun resolveReconnectTarget(session: P2PSession): Pair<String, Int>? {
        if (session.isLocallyInitiated) {
            return session.remoteAddr to session.remotePort
        }

        val reconnectPort = session.reconnectPort ?: return null
        val selection = selectBestIp(session.reconnectIps)
        val reconnectIp = selection.ip ?: session.reconnectIps.firstOrNull() ?: return null
        return reconnectIp to reconnectPort
    }

    private fun ensureServerRunning() {
        if (!_status.value.isRunning || currentInfo == null) {
            startServer()
        }
    }

    private fun createClientCallback(): P2PClientCallback = object : P2PClientCallback {
        override fun onTransferOffer(sessionId: String, transferId: String, totalSize: Long, billCount: Int): Boolean {
            pendingIncomingTransfers["$sessionId:$transferId"] = PendingIncomingTransfer(
                sessionId = sessionId,
                transferId = transferId,
                totalSize = totalSize,
                billCount = billCount
            )
            markTransferStage(
                sessionId = sessionId,
                direction = TransferDirection.RECEIVE,
                stage = TransferStage.OPENING_CHANNEL,
                totalBytes = totalSize,
                detail = "对方已开始发送，等待传输通道建立"
            )
            return true
        }
    }

    fun takePendingIncomingTransfer(sessionId: String, transferId: String): PendingIncomingTransfer? {
        return pendingIncomingTransfers.remove("$sessionId:$transferId")
    }

    private fun findTrustedSessionId(
        remoteAddr: String,
        remoteDeviceName: String,
        pairCode: String
    ): String? {
        return sessions.values
            .asSequence()
            .filter { session ->
                session.isPaired &&
                    !session.pairCode.isNullOrBlank() &&
                    session.pairCode.equals(pairCode, ignoreCase = true) &&
                    session.remoteDevice == remoteDeviceName
            }
            .maxByOrNull { session ->
                when {
                    session.remoteAddr == remoteAddr -> 3
                    session.reconnectIps.contains(remoteAddr) -> 2
                    else -> 1
                }
            }
            ?.sessionId
    }

    // ============================================================================
    // Private helpers
    // ============================================================================

    /**
     * Export all bills from Room DB to a JSON byte array matching the protocol format.
     */
    private suspend fun exportAllBills(): ByteArray {
        // Build the export JSON using kotlinx.serialization JsonArray
        val billsList = mutableListOf<String>()
        var totalBillCount = 0

        try {
            val identities = identityRepository.getAllIdentities().first()
            for (identity in identities) {
                val bills = billRepository.getBillsForIdentity(identity.id).first()
                for (bill in bills) {
                    // Build each bill as a JSON object string
                    val billJson = buildBillJson(
                        dateTimeFormatted = bill.dateTimeStrFormat,
                        itemType = bill.type,
                        number = bill.transactionNo,
                        targetUser = bill.targetUser,
                        moneyStr = bill.money,
                        money = bill.money.toDoubleOrNull(),
                        method = bill.method,
                        statusStr = bill.status,
                        category = bill.category ?: "",
                        building = bill.building ?: "",
                        room = bill.room ?: "",
                        position = bill.position ?: "",
                        accountLabel = bill.accountLabel
                    )
                    billsList.add(billJson)
                    totalBillCount++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Export bills failed", e)
        }

        val exportTime = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date())

        val json = """{"export_time":"$exportTime","bill_count":$totalBillCount,"bills":[${billsList.joinToString(",")}],"source":"p2p_transfer"}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun buildBillJson(
        dateTimeFormatted: String,
        itemType: String,
        number: String,
        targetUser: String,
        moneyStr: String,
        money: Double?,
        method: String,
        statusStr: String,
        category: String,
        building: String,
        room: String,
        position: String,
        accountLabel: String
    ): String {
        val moneyValue = money?.let { "\"$it\"" } ?: "null"
        // Escape special characters in strings for safe JSON embedding
        return """{"date_time_formatted":${escapeJson(dateTimeFormatted)},"item_type":${escapeJson(itemType)},"number":${escapeJson(number)},"target_user":${escapeJson(targetUser)},"money_str":${escapeJson(moneyStr)},"money":$moneyValue,"method":${escapeJson(method)},"status_str":${escapeJson(statusStr)},"category":${escapeJson(category)},"building":${escapeJson(building)},"room":${escapeJson(room)},"position":${escapeJson(position)},"account_label":${escapeJson(accountLabel)},"is_combined":false}"""
    }

    private fun escapeJson(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun parseBillCount(data: ByteArray): Int {
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(String(data, Charsets.UTF_8))
            json.jsonObject["bill_count"]?.jsonPrimitive?.intOrNull ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Lifecycle management
    /**
     * Cancel all coroutines and release resources.
     * This should be called when the application is being destroyed,
     * NOT from ViewModel.onCleared (P2PManager is a @Singleton).
     */
    fun destroy() {
        stopServer()
        scope.cancel()
        Log.i(tag, "P2PManager destroyed")
    }

    private fun updateTransferProgress(
        sessionId: String,
        fileName: String,
        bytesTransferred: Long,
        totalBytes: Long,
        direction: TransferDirection,
        stage: TransferStage = if (bytesTransferred >= totalBytes && totalBytes > 0) {
            TransferStage.COMPLETED
        } else {
            TransferStage.TRANSFERRING
        },
        status: TransferStatus = if (bytesTransferred >= totalBytes && totalBytes > 0) {
            TransferStatus.SUCCESS
        } else {
            TransferStatus.RUNNING
        },
        detail: String? = null
    ) {
        val current = _transferProgress.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.sessionId == sessionId }
        val progress = P2PTransferProgress(
            sessionId = sessionId,
            fileName = fileName,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            direction = direction,
            stage = stage,
            status = status,
            detail = detail
        )
        if (existingIndex >= 0) {
            current[existingIndex] = progress
        } else {
            current.add(progress)
        }
        _transferProgress.value = current
    }

    private fun markTransferStage(
        sessionId: String,
        fileName: String = "bills_export.json",
        direction: TransferDirection,
        stage: TransferStage,
        bytesTransferred: Long = 0L,
        totalBytes: Long = 0L,
        detail: String? = null
    ) {
        updateTransferProgress(
            sessionId = sessionId,
            fileName = fileName,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            direction = direction,
            stage = stage,
            status = TransferStatus.RUNNING,
            detail = detail
        )
    }

    private fun markTransferFailed(
        sessionId: String,
        fileName: String = "bills_export.json",
        direction: TransferDirection,
        detail: String,
        bytesTransferred: Long = 0L,
        totalBytes: Long = 0L
    ) {
        updateTransferProgress(
            sessionId = sessionId,
            fileName = fileName,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            direction = direction,
            stage = TransferStage.FAILED,
            status = TransferStatus.FAILED,
            detail = detail
        )
    }

    private fun removeTransferProgress(sessionId: String) {
        _transferProgress.value = _transferProgress.value.filterNot { it.sessionId == sessionId }
    }
}

/**
 * Holds pending import data from a P2P transfer, waiting for user to choose target identity.
 */
data class P2PPendingImport(
    val sessionId: String,
    val fileName: String,
    val data: ByteArray,
    val billCount: Int
)
