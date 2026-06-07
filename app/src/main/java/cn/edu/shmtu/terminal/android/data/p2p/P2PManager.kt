package cn.edu.shmtu.terminal.android.data.p2p

import android.content.Context
import android.util.Log
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
    private val billRepository: BillRepository,
    private val billDbManager: BillDatabaseManager,
    private val identityRepository: IdentityRepository,
    private val importDataUseCase: ImportDataUseCase
) {

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

    // Encryption keys per session — zeroed on disconnect for forward secrecy
    private val encryptionKeys = ConcurrentHashMap<String, ByteArray>()

    private var currentInfo: P2PInfo? = null
    private var serverJob: Job? = null

    private var deviceName: String = android.os.Build.MODEL ?: "SHMTU Device"
    private var serverPort: Int = P2PProtocol.DEFAULT_PORT

    init {
        server.setCallback(object : P2PServerCallback {
            override fun onPairRequest(remoteAddr: String, deviceName: String, pairCode: String) {
                Log.d(tag, "Pair request from $remoteAddr: device=$deviceName")
                val request = P2PPairRequest(
                    remoteAddr = remoteAddr,
                    remoteDevice = deviceName,
                    pairCode = pairCode
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
                updateTransferProgress(sessionId, fileName, data.size.toLong(), data.size.toLong(), TransferDirection.RECEIVE)
            }

            override fun onClientConnected(remoteAddr: String) {
                Log.d(tag, "Client connected: $remoteAddr")
            }

            override fun onClientDisconnected(remoteAddr: String) {
                Log.d(tag, "Client disconnected: $remoteAddr")
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

        _status.value = P2PStatus(isRunning = true, sessions = emptyList(), info = info)

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
        sessions.clear()
        clearAllEncryptionKeys()
        clearAllActiveClients()
        currentInfo = null
        _status.value = P2PStatus(isRunning = false, sessions = emptyList(), info = null)
        _pairRequests.value = emptyList()
        Log.i(tag, "Server stopped")
    }

    /**
     * Accept a pending pair request.
     */
    fun acceptPairRequest(remoteAddr: String) {
        val request = _pairRequests.value.find { it.remoteAddr == remoteAddr } ?: return
        val sessionId = UUID.randomUUID().toString()
        val accepted = server.acceptPair(remoteAddr, sessionId)

        if (accepted) {
            val session = P2PSession(
                sessionId = sessionId,
                remoteDevice = request.remoteDevice,
                remoteAddr = remoteAddr,
                remotePort = serverPort,
                isPaired = true
            )
            sessions[sessionId] = session
            _status.value = _status.value.copy(sessions = sessions.values.toList())
        }

        _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
    }

    /**
     * Reject a pending pair request.
     */
    fun rejectPairRequest(remoteAddr: String) {
        server.rejectPair(remoteAddr)
        _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
    }

    /**
     * Connect to a remote peer, pair, and negotiate encryption.
     */
    suspend fun connectToPeer(
        host: String,
        port: Int,
        pairCode: String
    ): Result<P2PSession> = withContext(Dispatchers.IO) {
        val client = P2PClient()
        try {
            val connectResult = client.connect(host, port)
            if (connectResult.isFailure) {
                return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("连接失败"))
            }

            val pairResult = client.sendPairRequest(deviceName, pairCode)
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
                isPaired = true
            )
            sessions[session.sessionId] = session
            _status.value = _status.value.copy(sessions = sessions.values.toList())

            // Store the active client and its encryption key for reuse
            activeClients[session.sessionId] = client
            client.encryptionKey?.let { key ->
                encryptionKeys[session.sessionId] = key.copyOf()
            }

            // Start heartbeat for this client connection
            client.startHeartbeat(scope)

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
            } else {
                // No active connection — create a new one
                client = P2PClient()
                isNewConnection = true

                val connectResult = client.connect(session.remoteAddr, session.remotePort)
                if (connectResult.isFailure) {
                    return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("连接失败"))
                }

                // Re-pair with the remote
                val pairResult = client.sendPairRequest(deviceName, currentInfo?.pairCode ?: "")
                if (pairResult.isFailure) {
                    client.close()
                    return@withContext Result.failure(Exception("配对失败"))
                }

                // Negotiate encryption
                val encryptResult = client.negotiateEncryption(currentInfo?.pairCode ?: "")
                if (encryptResult.isFailure) {
                    client.close()
                    return@withContext Result.failure(encryptResult.exceptionOrNull() ?: Exception("加密协商失败"))
                }

                // Store the active client and its encryption key
                activeClients[sessionId] = client
                client.encryptionKey?.let { key ->
                    encryptionKeys[sessionId] = key.copyOf()
                }

                // Start heartbeat
                client.startHeartbeat(scope)
            }

            // Use transferId (UUID) separate from sessionId for the Rust-aligned protocol
            val transferId = UUID.randomUUID().toString()

            // Send transfer offer
            val offerResult = client.sendTransferOffer(
                transferId = transferId,
                totalSize = exportData.size.toLong(),
                billCount = parseBillCount(exportData)
            )

            if (offerResult.isFailure || offerResult.getOrNull() != true) {
                if (isNewConnection) client.close()
                return@withContext Result.failure(Exception("传输被拒绝"))
            }

            // Send data with progress tracking
            val sendResult = client.sendTransferData(
                transferId = transferId,
                data = exportData,
                onProgress = { transferred, total ->
                    updateTransferProgress(sessionId, "bills_export.json", transferred, total, TransferDirection.SEND)
                }
            )

            // Only disconnect if this was a fresh connection; keep persistent connections alive
            if (isNewConnection) {
                client.disconnect()
                removeActiveClient(sessionId)
            }

            if (sendResult.isSuccess) {
                updateTransferProgress(sessionId, "bills_export.json", exportData.size.toLong(), exportData.size.toLong(), TransferDirection.SEND)
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
        removeActiveClient(sessionId)
        clearEncryptionKey(sessionId)
        sessions.remove(sessionId)
        _status.value = _status.value.copy(sessions = sessions.values.toList())
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
            stopHeartbeat()
            close()
        }
        activeClients.remove(sessionId)
    }

    /**
     * Close and remove all active client connections.
     */
    private fun clearAllActiveClients() {
        activeClients.values.forEach { client ->
            client.stopHeartbeat()
            client.close()
        }
        activeClients.clear()
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
        direction: TransferDirection
    ) {
        val current = _transferProgress.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.sessionId == sessionId }
        val progress = P2PTransferProgress(
            sessionId = sessionId,
            fileName = fileName,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            direction = direction
        )
        if (existingIndex >= 0) {
            current[existingIndex] = progress
        } else {
            current.add(progress)
        }
        _transferProgress.value = current
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
