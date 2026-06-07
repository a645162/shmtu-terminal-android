package cn.edu.shmtu.terminal.android.data.p2p

import android.content.Context
import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.domain.usecase.export.ImportDataUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.export.ArchiveImportReport
import cn.edu.shmtu.terminal.android.domain.usecase.export.TransferArchiveService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central coordinator for P2P networking.
 * Manages server lifecycle, sessions, bill export/import, and QR payload generation.
 *
 * All P2P communication uses RESTful HTTP calls via P2PHttpServer instead of
 * BillWebServer. Pairing, discovery, and transfer are all HTTP-based.
 * Authentication uses P2P-Key (peerKey) instead of Bearer tokens.
 */
@Singleton
class P2PManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val sessionStore: P2PSessionStore,
    private val importDataUseCase: ImportDataUseCase,
    private val transferArchiveService: TransferArchiveService,
    private val p2pHttpServer: P2PHttpServer,
    private val okHttpClient: OkHttpClient
) {

    data class PendingIncomingTransfer(
        val sessionId: String,
        val transferId: String,
        val totalSize: Long,
        val itemCount: Int
    )

    private val tag = "P2PManager"

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // P2P-dedicated OkHttpClient with appropriate timeouts for P2P operations
    private val p2pHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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

    private val pendingIncomingTransfers = ConcurrentHashMap<String, PendingIncomingTransfer>()

    private var currentInfo: P2PInfo? = null
    private var serverJob: Job? = null

    private var deviceName: String = android.os.Build.MODEL ?: "SHMTU Device"

    /** P2P port from SettingsDataStore (default 19827) */
    private var p2pPort: Int = DEFAULT_P2P_PORT

    // Map from P2PPairRequest.remoteAddr (used as UI key) to P2PHttpServer's sessionId
    // This bridges the gap between the UI (which uses remoteAddr) and P2PHttpServer (which uses sessionId)
    private val pairRequestSessionMap = ConcurrentHashMap<String, String>()

    // Subscriptions to P2PHttpServer event flows
    private var transferEventJob: Job? = null
    private var pairEventJob: Job? = null

    init {
        sessionStore.loadSessions().forEach { session ->
            sessions[session.sessionId] = session
        }
        _status.value = _status.value.copy(
            sessions = sessions.values.sortedByDescending { it.createdAt }
        )

        // Subscribe to P2PHttpServer transfer events (incoming file uploads)
        transferEventJob = scope.launch {
            p2pHttpServer.transferEvents.collect { event ->
                handleIncomingTransferEvent(event)
            }
        }

        // Subscribe to P2PHttpServer pair events (incoming pair requests)
        pairEventJob = scope.launch {
            p2pHttpServer.pairEvents.collect { event ->
                handleIncomingPairEvent(event)
            }
        }
    }

    /**
     * Configure the P2P manager.
     */
    fun configure(deviceName: String, port: Int) {
        this.deviceName = deviceName.ifBlank { android.os.Build.MODEL ?: "SHMTU Device" }
        this.p2pPort = port
    }

    fun getNotificationSummary(): String {
        val info = currentInfo
        val connectedCount = sessions.values.count { it.isConnected }
        return if (info != null) {
            "${info.deviceName} · 端口 ${info.port} · 已连接 $connectedCount 台"
        } else {
            "${deviceName} · 端口 $p2pPort"
        }
    }

    /**
     * Start the P2P server (P2PHttpServer RESTful HTTP service).
     */
    fun startServer() {
        if (_status.value.isRunning) {
            Log.w(tag, "Server already running")
            return
        }

        // Ensure P2PHttpServer is running
        if (!p2pHttpServer.isRunning()) {
            val startResult = p2pHttpServer.start(p2pPort)
            if (startResult.isFailure) {
                Log.e(tag, "Failed to start P2PHttpServer", startResult.exceptionOrNull())
                return
            }
        }

        // Use the actual port P2PHttpServer is running on
        val actualPort = p2pHttpServer.getPort().takeIf { it > 0 } ?: p2pPort

        val info = P2PInfo(
            deviceName = deviceName,
            ips = getLocalIPs(),
            port = actualPort
        )
        currentInfo = info

        // 同步配对码到 P2PHttpServer，确保 QR 码显示的配对码和 HTTP API 验证的一致
        p2pHttpServer.setPairCode(info.pairCode)

        _status.value = P2PStatus(isRunning = true, sessions = sessions.values.toList(), info = info)

        Log.i(tag, "Server started: deviceName=${info.deviceName} port=${info.port} pairCode=${info.pairCode} (P2PHttpServer)")
    }

    /**
     * Stop the P2P server.
     */
    fun stopServer() {
        p2pHttpServer.stop()
        serverJob?.cancel()
        serverJob = null
        currentInfo = null
        sessions.replaceAll { _, session -> session.copy(isConnected = false) }
        persistSessions()
        _status.value = P2PStatus(isRunning = false, sessions = sessions.values.toList(), info = null)
        _pairRequests.value = emptyList()
        pairRequestSessionMap.clear()
        Log.i(tag, "Server stopped")
    }

    /**
     * Accept a pending pair request.
     * The pair was already accepted by P2PHttpServer when it emitted the event;
     * here we just create the P2PSession for the UI.
     */
    suspend fun acceptPairRequest(remoteAddr: String): Boolean = withContext(Dispatchers.IO) {
        val request = _pairRequests.value.find { it.remoteAddr == remoteAddr }
        if (request == null) {
            Log.w(tag, "acceptPairRequest ignored, no UI request for $remoteAddr")
            return@withContext false
        }

        // Look up the P2PHttpServer sessionId for this remote address
        val webSessionId = pairRequestSessionMap[remoteAddr]
            ?: UUID.randomUUID().toString()

        _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
        pairRequestSessionMap.remove(remoteAddr)

        // Create a P2PSession for the accepted pair
        val session = P2PSession(
            sessionId = webSessionId,
            remoteDevice = request.remoteDevice,
            remoteAddr = remoteAddr,
            remotePort = request.reconnectPort ?: p2pPort,
            pairCode = request.pairCode,
            peerKey = null, // Passive side doesn't need peerKey to call the initiator's API
            reconnectIps = request.reconnectIps,
            reconnectPort = request.reconnectPort,
            isLocallyInitiated = false,
            isPaired = true,
            isConnected = true
        )
        sessions[session.sessionId] = session
        persistSessions()
        _status.value = _status.value.copy(sessions = sessions.values.toList())

        Log.i(tag, "acceptPairRequest succeeded for $remoteAddr, session=$webSessionId")
        true
    }

    /**
     * Reject a pending pair request.
     * Note: P2PHttpServer auto-accepts pair requests, so rejection is UI-only.
     */
    suspend fun rejectPairRequest(remoteAddr: String): Boolean = withContext(Dispatchers.IO) {
        val request = _pairRequests.value.find { it.remoteAddr == remoteAddr }
        if (request == null) {
            Log.w(tag, "rejectPairRequest ignored, no UI request for $remoteAddr")
            return@withContext false
        }

        // Clear from UI regardless
        _pairRequests.value = _pairRequests.value.filter { it.remoteAddr != remoteAddr }
        pairRequestSessionMap.remove(remoteAddr)

        Log.i(tag, "rejectPairRequest: cleared from UI for $remoteAddr")
        true
    }

    /**
     * Connect to a remote peer via RESTful HTTP calls:
     * 1. POST /api/p2p/discover - discover the remote device
     * 2. POST /api/p2p/pair - pair with the remote device using pairCode, obtain peerKey
     * 3. Create P2PSession on success (with peerKey)
     */
    suspend fun connectToPeer(
        host: String,
        port: Int,
        pairCode: String
    ): Result<P2PSession> = withContext(Dispatchers.IO) {
        ensureServerRunning()
        try {
            // Step 1: Discover the remote device
            val baseUrl = "http://$host:$port"

            val discoverRequest = Request.Builder()
                .url("$baseUrl/api/p2p/discover")
                .post("{\"deviceName\":\"$deviceName\"}".toRequestBody("application/json".toMediaType()))
                .build()

            val discoverResponse = try {
                p2pHttpClient.newCall(discoverRequest).execute()
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("发现设备失败: ${e.message}"))
            }

            if (!discoverResponse.isSuccessful) {
                val code = discoverResponse.code
                discoverResponse.close()
                return@withContext Result.failure(Exception("发现设备失败: HTTP $code"))
            }

            val discoverBody = discoverResponse.body?.string() ?: run {
                return@withContext Result.failure(Exception("发现设备返回空响应"))
            }

            // Parse discover response
            val discoverData = parseApiResponseData(discoverBody)
                ?: return@withContext Result.failure(Exception("解析发现设备响应失败"))

            val remoteDeviceName = discoverData["deviceName"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val remoteIps = try {
                (discoverData["ips"] as? JsonArray)?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val remotePairCode = discoverData["pairCode"]?.jsonPrimitive?.contentOrNull ?: ""

            Log.i(tag, "Discovered device: name=$remoteDeviceName port=$port pairCode=$remotePairCode")

            // Step 2: Pair with the remote device
            val pairRequestBody = p2pJson.encodeToString(
                P2PRestPairRequest.serializer(),
                P2PRestPairRequest(
                    pairCode = pairCode,
                    deviceName = deviceName,
                    listenPort = currentInfo?.port ?: p2pPort,
                    listenIps = getLocalIPs()
                )
            )

            val pairRequest = Request.Builder()
                .url("$baseUrl/api/p2p/pair")
                .post(pairRequestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val pairResponse = try {
                p2pHttpClient.newCall(pairRequest).execute()
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("配对请求失败: ${e.message}"))
            }

            if (!pairResponse.isSuccessful) {
                val code = pairResponse.code
                pairResponse.close()
                val reason = when (code) {
                    403 -> "配对码不正确"
                    else -> "配对失败: HTTP $code"
                }
                return@withContext Result.failure(Exception(reason))
            }

            val pairBody = pairResponse.body?.string() ?: run {
                return@withContext Result.failure(Exception("配对返回空响应"))
            }

            val pairData = parseApiResponseData(pairBody)
                ?: return@withContext Result.failure(Exception("解析配对响应失败"))

            val sessionId = pairData["sessionId"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext Result.failure(Exception("配对响应缺少 sessionId"))

            val pairedDeviceName = pairData["deviceName"]?.jsonPrimitive?.contentOrNull ?: remoteDeviceName

            // Extract peerKey from the pair response
            val peerKey = pairData["peerKey"]?.jsonPrimitive?.contentOrNull
            if (peerKey.isNullOrBlank()) {
                Log.w(tag, "Pair response missing peerKey, transfer may fail")
            }

            // Step 3: Create P2PSession with peerKey
            val session = P2PSession(
                sessionId = sessionId,
                remoteDevice = pairedDeviceName,
                remoteAddr = host,
                remotePort = port,
                pairCode = pairCode,
                peerKey = peerKey,
                reconnectIps = remoteIps,
                reconnectPort = port,
                isLocallyInitiated = true,
                isPaired = true,
                isConnected = true
            )
            sessions[session.sessionId] = session
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())

            Log.i(tag, "Paired with ${session.remoteDevice} at $host:$port, session=${session.sessionId}, peerKey=${peerKey?.take(8)}...")
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Connect to peer failed", e)
            Result.failure(e)
        }
    }

    /**
     * Send the encrypted ZIP archive to a paired peer via RESTful HTTP POST.
     * Uses P2P-Key authentication (peerKey) instead of Bearer token.
     * Uses multipart upload to POST /api/p2p/transfer.
     */
    suspend fun sendBills(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = sessions[sessionId]
            ?: return@withContext Result.failure(Exception("会话不存在"))

        try {
            val pairCode = session.pairCode
                ?: return@withContext Result.failure(Exception("当前会话缺少配对码"))
            val peerKey = session.peerKey
                ?: return@withContext Result.failure(Exception("当前会话缺少对端密钥(peerKey)，无法认证"))
            val archive = exportAllBills(pairCode)
            val exportData = archive.bytes
            if (exportData.isEmpty()) {
                return@withContext Result.failure(Exception("没有可发送的账单数据"))
            }

            val baseUrl = "http://${session.remoteAddr}:${session.remotePort}"
            val totalSize = exportData.size.toLong()

            markTransferStage(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                stage = TransferStage.PREPARING,
                totalBytes = totalSize,
                detail = "正在准备发送数据"
            )

            // Build multipart request body
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sessionId", sessionId)
                .addFormDataPart("billCount", archive.billCount.toString())
                .addFormDataPart(
                    "file",
                    TRANSFER_FILE_NAME,
                    exportData.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            // Wrap with progress tracking
            val requestBody = ProgressRequestBody(
                delegate = multipartBody,
                totalBytes = totalSize,
                onProgress = { bytesWritten ->
                    updateTransferProgress(
                        sessionId,
                        TRANSFER_FILE_NAME,
                        bytesWritten,
                        totalSize,
                        TransferDirection.SEND,
                        stage = if (bytesWritten >= totalSize && totalSize > 0) TransferStage.VERIFYING else TransferStage.TRANSFERRING,
                        status = TransferStatus.RUNNING,
                        detail = if (bytesWritten >= totalSize && totalSize > 0) "数据发送完成，等待对方确认" else "正在发送数据"
                    )
                }
            )

            val request = Request.Builder()
                .url("$baseUrl/api/p2p/transfer")
                .post(requestBody)
                .header("Authorization", "P2P-Key $peerKey")
                .build()

            markTransferStage(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                stage = TransferStage.TRANSFERRING,
                totalBytes = totalSize,
                detail = "正在发送数据"
            )

            val response = try {
                p2pHttpClient.newCall(request).execute()
            } catch (e: Exception) {
                markTransferFailed(
                    sessionId = sessionId,
                    direction = TransferDirection.SEND,
                    detail = "发送失败: ${e.message}",
                    totalBytes = totalSize
                )
                return@withContext Result.failure(Exception("发送失败: ${e.message}"))
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                val reason = when (code) {
                    401 -> "认证失败，peerKey无效，请重新配对"
                    404 -> "对方未找到配对会话，请重新配对"
                    else -> "发送失败: HTTP $code"
                }
                markTransferFailed(
                    sessionId = sessionId,
                    direction = TransferDirection.SEND,
                    detail = reason,
                    totalBytes = totalSize
                )
                return@withContext Result.failure(Exception(reason))
            }

            val responseBody = response.body?.string()
            response.close()

            // Parse the transfer response to confirm receipt
            val transferResult = parseApiResponseData(responseBody ?: "")
            val checksum = transferResult?.get("checksum")?.jsonPrimitive?.contentOrNull ?: ""
            Log.i(tag, "Transfer complete: session=$sessionId bytes=$totalSize checksum=$checksum")

            updateTransferProgress(
                sessionId,
                TRANSFER_FILE_NAME,
                totalSize,
                totalSize,
                TransferDirection.SEND,
                stage = TransferStage.COMPLETED,
                status = TransferStatus.SUCCESS,
                detail = "发送完成"
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Send bills failed", e)
            markTransferFailed(
                sessionId = sessionId,
                direction = TransferDirection.SEND,
                detail = e.message ?: "发送失败",
                totalBytes = 0L
            )
            Result.failure(e)
        }
    }

    /**
     * Disconnect a paired session.
     */
    fun disconnectSession(sessionId: String) {
        val session = sessions[sessionId] ?: return
        if (!session.isConnected) {
            sessions.remove(sessionId)
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())
            return
        }
        sessions[sessionId] = session.copy(isConnected = false)
        persistSessions()
        _status.value = _status.value.copy(sessions = sessions.values.toList())
    }

    /**
     * Reconnect a session via RESTful HTTP calls (discover + pair).
     * On success, the session's peerKey is updated from the pair response.
     */
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

        try {
            val baseUrl = "http://${reconnectTarget.first}:${reconnectTarget.second}"

            // Discover
            val discoverRequest = Request.Builder()
                .url("$baseUrl/api/p2p/discover")
                .post("{\"deviceName\":\"$deviceName\"}".toRequestBody("application/json".toMediaType()))
                .build()

            val discoverResponse = try {
                p2pHttpClient.newCall(discoverRequest).execute()
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("重连发现设备失败: ${e.message}"))
            }

            if (!discoverResponse.isSuccessful) {
                discoverResponse.close()
                return@withContext Result.failure(Exception("重连发现设备失败: HTTP ${discoverResponse.code}"))
            }
            discoverResponse.close()

            // Pair
            val pairRequestBody = p2pJson.encodeToString(
                P2PRestPairRequest.serializer(),
                P2PRestPairRequest(
                    pairCode = pairCode,
                    deviceName = deviceName,
                    listenPort = currentInfo?.port ?: p2pPort,
                    listenIps = getLocalIPs()
                )
            )

            val pairRequest = Request.Builder()
                .url("$baseUrl/api/p2p/pair")
                .post(pairRequestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val pairResponse = try {
                p2pHttpClient.newCall(pairRequest).execute()
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("重连配对失败: ${e.message}"))
            }

            if (!pairResponse.isSuccessful) {
                pairResponse.close()
                return@withContext Result.failure(Exception("重连配对失败: HTTP ${pairResponse.code}"))
            }

            // Parse pair response to get new peerKey
            val pairBody = pairResponse.body?.string()
            pairResponse.close()

            val newPeerKey = pairBody?.let { body ->
                parseApiResponseData(body)?.get("peerKey")?.jsonPrimitive?.contentOrNull
            }

            val updated = session.copy(
                isConnected = true,
                remoteAddr = reconnectTarget.first,
                remotePort = reconnectTarget.second,
                peerKey = newPeerKey ?: session.peerKey
            )
            sessions[sessionId] = updated
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Reconnect session failed", e)
            Result.failure(e)
        }
    }

    /**
     * Import a received ZIP archive using the paired session's pair code.
     */
    suspend fun importBills(sessionId: String, data: ByteArray): Result<ArchiveImportReport> {
        return try {
            val payloadDigest = shortSha256(data)
            val parsedBillCount = parseBillCount(data)
            val session = sessions[sessionId]
                ?: return Result.failure(Exception("会话不存在"))
            val pairCode = session.pairCode
                ?: return Result.failure(Exception("当前会话缺少配对码"))
            Log.i(
                tag,
                "Import requested: session=$sessionId bytes=${data.size} parsedItems=$parsedBillCount digest=$payloadDigest"
            )
            val result = importDataUseCase.importFromBytesDetailed(data, pairCode)
            if (result.isSuccess) {
                val report = result.getOrNull()
                Log.i(
                    tag,
                    "Import finished: session=$sessionId digest=$payloadDigest bills=${report?.billCount} summary=${report?.summary}"
                )
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Import bills failed", e)
            Result.failure(e)
        }
    }

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
     * The port field contains the P2P port (default 19827) for RESTful communication.
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
    // P2PHttpServer event handlers
    // ============================================================================

    /**
     * Handle incoming pair events from P2PHttpServer.
     * When a remote device calls POST /api/p2p/pair, P2PHttpServer emits a P2PPairEvent.
     * We map it to the UI model (P2PPairRequest) and surface it to the user.
     */
    private fun handleIncomingPairEvent(event: P2PPairEvent) {
        Log.d(tag, "Pair event from P2PHttpServer: session=${event.sessionId} device=${event.remoteDeviceName} peerKey=${event.peerKey.take(8)}...")

        // New pair request — add to UI list
        val uiRequest = P2PPairRequest(
            remoteAddr = event.sessionId, // Use sessionId as key since we don't have IP in REST model
            remoteDevice = event.remoteDeviceName,
            pairCode = currentInfo?.pairCode ?: "",
            reconnectIps = event.remoteIps,
            reconnectPort = event.remotePort
        )

        // Check if we already have a request for this session
        val existing = _pairRequests.value.any { it.remoteAddr == event.sessionId }
        if (!existing) {
            _pairRequests.value = _pairRequests.value + uiRequest
            pairRequestSessionMap[event.sessionId] = event.sessionId
            Log.i(tag, "New pair request surfaced to UI: device=${event.remoteDeviceName} sessionId=${event.sessionId}")
        }
    }

    /**
     * Handle incoming transfer events from P2PHttpServer.
     * This replaces the old BillWebServer transfer event handling.
     */
    private suspend fun handleIncomingTransferEvent(event: P2PHttpTransferEvent) {
        val sessionId = event.sessionId
        val data = event.data
        val billCount = event.billCount
        val checksum = event.checksum

        val payloadDigest = shortSha256(data)
        val parsedBillCount = parseBillCount(data)
        Log.i(
            tag,
            "Transfer received (P2PHttpServer): session=$sessionId bytes=${data.size} bills=$billCount parsedBills=$parsedBillCount digest=$payloadDigest checksum=$checksum"
        )

        // Find or create a session for this transfer
        val existingSession = sessions[sessionId]
        if (existingSession == null) {
            // Create a session from the transfer event info
            val session = P2PSession(
                sessionId = sessionId,
                remoteDevice = event.deviceName,
                remoteAddr = "remote",
                remotePort = p2pPort,
                pairCode = null,
                isLocallyInitiated = false,
                isPaired = true,
                isConnected = true
            )
            sessions[sessionId] = session
            persistSessions()
            _status.value = _status.value.copy(sessions = sessions.values.toList())
        }

        _pendingImport.tryEmit(
            P2PPendingImport(
                sessionId = sessionId,
                fileName = TRANSFER_FILE_NAME,
                data = data.copyOf(),
                itemCount = billCount
            )
        )
        updateTransferProgress(
            sessionId,
            TRANSFER_FILE_NAME,
            data.size.toLong(),
            data.size.toLong(),
            TransferDirection.RECEIVE,
            stage = TransferStage.COMPLETED,
            status = TransferStatus.SUCCESS,
            detail = "接收完成，等待导入"
        )
    }

    // ============================================================================
    // Private helpers
    // ============================================================================

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
        // Also make sure P2PHttpServer itself is running
        if (!p2pHttpServer.isRunning()) {
            p2pHttpServer.start(p2pPort)
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

    /**
     * Parse the ApiResponse JSON to extract the "data" field as a JsonObject.
     * Returns null if parsing fails.
     */
    private fun parseApiResponseData(body: String): kotlinx.serialization.json.JsonObject? {
        return try {
            val responseJson = p2pJson.parseToJsonElement(body).jsonObject
            val success = responseJson["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            if (!success) {
                val error = responseJson["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                Log.e(tag, "API response error: $error")
                return null
            }
            val dataStr = responseJson["data"]?.jsonPrimitive?.contentOrNull ?: return null
            p2pJson.parseToJsonElement(dataStr).jsonObject
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse API response", e)
            null
        }
    }

    /**
     * Export all identities/accounts/bills to an encrypted transfer archive.
     */
    private suspend fun exportAllBills(pairCode: String): TransferArchiveService.ArchivePayload {
        return try {
            transferArchiveService.buildEncryptedArchiveBytes(pairCode, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Export archive failed", e)
            TransferArchiveService.ArchivePayload(
                bytes = ByteArray(0),
                identityCount = 0,
                accountCount = 0,
                billCount = 0,
                encrypted = true
            )
        }
    }

    private fun parseBillCount(data: ByteArray): Int {
        return try {
            if (transferArchiveService.isEncryptedArchive(data)) {
                0
            } else {
                val zip = java.util.zip.ZipInputStream(data.inputStream())
                zip.use { zis ->
                    generateSequence { zis.nextEntry }
                        .firstOrNull { it.name == "manifest.json" }
                        ?.let {
                            val root = org.json.JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                            val identities = root.optJSONArray("identities") ?: return@let 0
                            var total = 0
                            for (i in 0 until identities.length()) {
                                total += identities.optJSONObject(i)?.optJSONArray("bills")?.length() ?: 0
                            }
                            total
                        } ?: 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun shortSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    // Lifecycle management
    /**
     * Cancel all coroutines and release resources.
     * This should be called when the application is being destroyed,
     * NOT from ViewModel.onCleared (P2PManager is a @Singleton).
     */
    fun destroy() {
        stopServer()
        transferEventJob?.cancel()
        pairEventJob?.cancel()
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
        fileName: String = TRANSFER_FILE_NAME,
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
        fileName: String = TRANSFER_FILE_NAME,
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

    // ============================================================================
    // Progress-tracking OkHttp RequestBody wrapper
    // ============================================================================

    /**
     * An OkHttp RequestBody that wraps another and reports upload progress
     * via a callback. Reports progress at the beginning and completion of the write.
     *
     * Note: Fine-grained byte-level progress tracking with okio requires ForwardingSink
     * which has compatibility issues across okio versions. For P2P transfers (typically
     * small ZIP archives), start/complete progress is sufficient.
     */
    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val totalBytes: Long,
        private val onProgress: (bytesWritten: Long) -> Unit
    ) : RequestBody() {

        override fun contentType() = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun writeTo(sink: okio.BufferedSink) {
            onProgress(0L)
            delegate.writeTo(sink)
            sink.flush()
            onProgress(totalBytes)
        }
    }
}

/**
 * Holds pending import data from a P2P transfer, waiting for user to choose target identity.
 */
data class P2PPendingImport(
    val sessionId: String,
    val fileName: String,
    val data: ByteArray,
    val itemCount: Int
)

private const val TRANSFER_FILE_NAME = "shmtu_transfer.zip"
private const val DEFAULT_P2P_PORT = 19827
