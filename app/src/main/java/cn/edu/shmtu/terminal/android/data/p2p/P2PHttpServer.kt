package cn.edu.shmtu.terminal.android.data.p2p

import android.content.Context
import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.webserver.ApiResponse
import cn.edu.shmtu.terminal.android.data.webserver.NetworkUtils
import cn.edu.shmtu.terminal.android.domain.usecase.export.TransferArchiveService
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2P 配对事件，通过 SharedFlow 通知 P2PManager
 */
data class P2PPairEvent(
    val sessionId: String,
    val remoteDeviceName: String,
    val remoteIps: List<String>,
    val remotePort: Int,
    val peerKey: String
)

/**
 * P2P 传输事件，通过 SharedFlow 通知 P2PManager
 */
data class P2PHttpTransferEvent(
    val sessionId: String,
    val deviceName: String,
    val data: ByteArray,
    val billCount: Int,
    val checksum: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is P2PHttpTransferEvent) return false
        return sessionId == other.sessionId &&
            deviceName == other.deviceName &&
            data.contentEquals(other.data) &&
            billCount == other.billCount &&
            checksum == other.checksum
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + billCount
        result = 31 * result + checksum.hashCode()
        return result
    }
}

/**
 * P2P 配对会话数据结构
 */
data class P2PHttpSession(
    val sessionId: String,
    val peerKey: String,
    val remoteDeviceName: String,
    val remoteIps: List<String>,
    val remotePort: Int,
    val pairCode: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 基于 NanoHTTPD 的独立 P2P HTTP 服务器。
 * 和 BillWebServer 完全解耦，运行在独立端口（默认 19827）。
 *
 * API 端点:
 * - POST /api/p2p/discover — 设备发现，无需认证
 * - POST /api/p2p/pair — 配对请求，验证 pairCode，成功后生成 peerKey
 * - POST /api/p2p/transfer — 上传加密 ZIP，需 P2P-Key 认证
 * - GET  /api/p2p/transfer/{sessionId} — 下载对方账单 ZIP，需 P2P-Key 认证
 */
@Singleton
class P2PHttpServer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val transferArchiveService: TransferArchiveService
) {
    companion object {
        private const val TAG = "P2PHttpServer"
        const val DEFAULT_PORT = 19827
        private const val PEER_KEY_LENGTH = 32

        // 密钥字符集：大写字母 + 数字，去掉易混淆的 I/O/0/1
        private const val KEY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var innerServer: NanoHTTPD? = null

    @Volatile
    private var runningPort: Int = 0

    // 每次启动时由 P2PManager 传入的配对码
    @Volatile
    private var currentPairCode: String = ""

    // 配对会话存储（按 peerKey 索引，用于认证）
    private val p2pSessions = ConcurrentHashMap<String, P2PHttpSession>()

    // 配对事件流
    private val _pairEvents = MutableSharedFlow<P2PPairEvent>(extraBufferCapacity = 16)
    val pairEvents: SharedFlow<P2PPairEvent> = _pairEvents.asSharedFlow()

    // 传输事件流
    private val _transferEvents = MutableSharedFlow<P2PHttpTransferEvent>(extraBufferCapacity = 16)
    val transferEvents: SharedFlow<P2PHttpTransferEvent> = _transferEvents.asSharedFlow()

    fun isRunning(): Boolean = innerServer?.isAlive == true
    fun getPort(): Int = runningPort

    /**
     * 设置当前配对码，供 P2PManager 在启动服务时调用。
     * 每次启动配对码可能不同。
     */
    fun setPairCode(code: String) {
        currentPairCode = code
    }

    /**
     * 启动 P2P HTTP 服务器。
     * @param port 监听端口，默认 19827
     * @param pairCode 本次启动使用的配对码
     */
    fun start(port: Int = DEFAULT_PORT, pairCode: String = ""): Result<Unit> {
        return try {
            if (innerServer?.isAlive == true) {
                Log.w(TAG, "P2PHttpServer already running on port $runningPort")
                return Result.success(Unit)
            }

            if (pairCode.isNotBlank()) {
                currentPairCode = pairCode
            }

            val server = object : NanoHTTPD("0.0.0.0", port) {
                override fun serve(session: IHTTPSession): Response {
                    return handleRequest(session)
                }
            }
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            innerServer = server
            runningPort = port
            Log.i(TAG, "P2PHttpServer started on 0.0.0.0:$port, pairCode=$currentPairCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start P2PHttpServer", e)
            Result.failure(e)
        }
    }

    /**
     * 停止 P2P HTTP 服务器，清理所有 session。
     */
    fun stop() {
        try {
            innerServer?.stop()
            Log.i(TAG, "P2PHttpServer stopped")
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        } finally {
            innerServer = null
            runningPort = 0
            p2pSessions.clear()
        }
    }

    fun shutdown() {
        stop()
        serverScope.cancel()
    }

    // ============== 请求处理 ==============

    private fun handleRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method
        return try {
            when {
                uri == "/api/p2p/discover" && method == NanoHTTPD.Method.POST ->
                    handleDiscover(session)

                uri == "/api/p2p/pair" && method == NanoHTTPD.Method.POST ->
                    handlePair(session)

                uri == "/api/p2p/transfer" && method == NanoHTTPD.Method.POST ->
                    if (isP2PKeyAuthorized(session)) {
                        handleTransferUpload(session)
                    } else {
                        jsonError(401, "Unauthorized - P2P-Key invalid or missing")
                    }

                uri.startsWith("/api/p2p/transfer/") && method == NanoHTTPD.Method.GET ->
                    if (isP2PKeyAuthorized(session)) {
                        handleTransferDownload(uri)
                    } else {
                        jsonError(401, "Unauthorized - P2P-Key invalid or missing")
                    }

                else -> jsonError(404, "Not Found: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleRequest error", e)
            jsonError(500, "Internal error: ${e.message}")
        }
    }

    // ============== POST /api/p2p/discover ==============

    /**
     * 设备发现端点。无需认证。
     * 请求体: {"deviceName": "Phone B"}
     * 响应: {"success": true, "data": {"deviceName": "Phone A", "ips": [...], "port": 19827, "pairCode": "ABCDEF"}}
     */
    private fun handleDiscover(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val body = readBody(session)
        val requestDeviceName = try {
            val jsonElement = p2pJson.parseToJsonElement(body).jsonObject
            jsonElement["deviceName"]?.jsonPrimitive?.contentOrNull ?: ""
        } catch (_: Exception) {
            ""
        }

        val deviceName = settingsDataStore.p2pDeviceNameFlowValue()
            .ifBlank { android.os.Build.MODEL ?: "SHMTU Device" }
        val ips = getLocalIPs()
        val port = runningPort
        val pairCode = currentPairCode

        val data = P2PRestDiscoverData(
            deviceName = deviceName,
            ips = ips,
            port = port,
            pairCode = pairCode
        )
        Log.i(TAG, "Discover from '$requestDeviceName', returning deviceName=$deviceName port=$port pairCode=$pairCode")
        return jsonResponse(200, ApiResponse.success(data))
    }

    // ============== POST /api/p2p/pair ==============

    /**
     * 配对请求端点。验证 pairCode，成功后为对方生成专属密钥(peerKey)。
     * 请求体: {"pairCode": "ABCDEF", "deviceName": "Phone B", "listenPort": 19827, "listenIps": [...]}
     * 成功响应: {"success": true, "data": {"sessionId": "uuid...", "deviceName": "Phone A", "peerKey": "..."}}
     * 失败响应(403): {"success": false, "error": "配对码不正确"}
     */
    private fun handlePair(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val body = readBody(session)
        val request = try {
            p2pJson.decodeFromString<P2PRestPairRequest>(body)
        } catch (e: Exception) {
            return jsonError(400, "Invalid request body: ${e.message}")
        }

        if (request.pairCode.isBlank()) {
            return jsonError(400, "pairCode is required")
        }

        // 验证配对码
        if (!request.pairCode.equals(currentPairCode, ignoreCase = true)) {
            Log.w(TAG, "Pair rejected: invalid pairCode from '${request.deviceName}'")
            return jsonError(403, "配对码不正确")
        }

        // 生成唯一 sessionId 和 peerKey
        val sessionId = UUID.randomUUID().toString()
        val peerKey = generatePeerKey()

        // 创建配对会话
        val p2pSession = P2PHttpSession(
            sessionId = sessionId,
            peerKey = peerKey,
            remoteDeviceName = request.deviceName,
            remoteIps = request.listenIps,
            remotePort = request.listenPort,
            pairCode = request.pairCode
        )
        p2pSessions[peerKey] = p2pSession

        val myDeviceName = settingsDataStore.p2pDeviceNameFlowValue()
            .ifBlank { android.os.Build.MODEL ?: "SHMTU Device" }

        // 发出配对事件
        serverScope.launch {
            _pairEvents.emit(
                P2PPairEvent(
                    sessionId = sessionId,
                    remoteDeviceName = request.deviceName,
                    remoteIps = request.listenIps,
                    remotePort = request.listenPort,
                    peerKey = peerKey
                )
            )
        }

        Log.i(TAG, "Pair accepted: session=$sessionId device=${request.deviceName} peerKey=$peerKey")

        val data = P2PRestPairResponseData(
            sessionId = sessionId,
            deviceName = myDeviceName
        )
        // 在响应中额外包含 peerKey（P2PRestPairResponseData 不含 peerKey，手动构建）
        val dataMap = mapOf(
            "sessionId" to sessionId,
            "deviceName" to myDeviceName,
            "peerKey" to peerKey
        )
        return jsonResponse(200, ApiResponse.success(dataMap))
    }

    // ============== POST /api/p2p/transfer ==============

    /**
     * 上传加密 ZIP。需要 P2P-Key 认证。
     * 请求: multipart/form-data，包含 file(二进制ZIP) + sessionId + billCount
     * Header: Authorization: P2P-Key <peerKey>
     * 成功响应: {"success": true, "data": {"received": true, "billCount": 50, "checksum": "sha256..."}}
     */
    private fun handleTransferUpload(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return jsonError(400, "Failed to parse request body: ${e.message}")
        }

        val sessionId = session.parameters["sessionId"]?.firstOrNull()
            ?: return jsonError(400, "sessionId is required")
        val billCountStr = session.parameters["billCount"]?.firstOrNull() ?: "0"
        val billCount = billCountStr.toIntOrNull() ?: 0

        // 获取上传的文件数据
        val tempFilePath = files["file"]
        if (tempFilePath.isNullOrBlank()) {
            return jsonError(400, "file is required")
        }

        val fileData = try {
            java.io.File(tempFilePath).readBytes()
        } catch (e: Exception) {
            return jsonError(400, "Failed to read uploaded file: ${e.message}")
        }

        if (fileData.isEmpty()) {
            return jsonError(400, "Uploaded file is empty")
        }

        // 查找对应的 session 以获取设备名
        val peerKey = extractP2PKey(session)
        val p2pSession = p2pSessions[peerKey]
        val deviceName = p2pSession?.remoteDeviceName ?: "Unknown"

        val checksum = shortSha256(fileData)
        Log.i(TAG, "Transfer upload: session=$sessionId bytes=${fileData.size} billCount=$billCount checksum=$checksum")

        // 发出传输事件供 P2PManager 订阅处理
        serverScope.launch {
            _transferEvents.emit(
                P2PHttpTransferEvent(
                    sessionId = sessionId,
                    deviceName = deviceName,
                    data = fileData.copyOf(),
                    billCount = billCount,
                    checksum = checksum
                )
            )
        }

        val data = P2PRestTransferResponseData(
            received = true,
            billCount = billCount,
            checksum = checksum
        )
        return jsonResponse(200, ApiResponse.success(data))
    }

    // ============== GET /api/p2p/transfer/{sessionId} ==============

    /**
     * 下载对方账单 ZIP。需要 P2P-Key 认证。
     * Header: Authorization: P2P-Key <peerKey>
     * 响应: application/octet-stream 二进制流
     */
    private fun handleTransferDownload(uri: String): NanoHTTPD.Response {
        val sessionId = uri.removePrefix("/api/p2p/transfer/").substringBefore('?').trim('/')
        if (sessionId.isEmpty()) {
            return jsonError(400, "sessionId is required")
        }

        // 验证会话存在
        val sessionExists = p2pSessions.values.any { it.sessionId == sessionId }
        if (!sessionExists) {
            return jsonError(404, "Pair session not found: $sessionId")
        }

        // 导出加密归档
        val pairCode = currentPairCode
        val archive = try {
            runBlocking { transferArchiveService.buildEncryptedArchiveBytes(pairCode, null) }
        } catch (e: Exception) {
            Log.e(TAG, "Transfer download: export failed", e)
            return jsonError(500, "Failed to export archive: ${e.message}")
        }

        if (archive.bytes.isEmpty()) {
            return jsonError(404, "No bill data available for export")
        }

        Log.i(TAG, "Transfer download: session=$sessionId bytes=${archive.bytes.size} billCount=${archive.billCount}")

        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/octet-stream",
            ByteArrayInputStream(archive.bytes),
            archive.bytes.size.toLong()
        )
    }

    // ============== 认证 ==============

    /**
     * 从 Authorization header 取 P2P-Key xxx，在 p2pSessions 中查找匹配的 session。
     */
    private fun isP2PKeyAuthorized(session: NanoHTTPD.IHTTPSession): Boolean {
        val peerKey = extractP2PKey(session) ?: return false
        return p2pSessions.containsKey(peerKey)
    }

    /**
     * 从请求 header 中提取 P2P-Key。
     * 格式: Authorization: P2P-Key <peerKey>
     */
    private fun extractP2PKey(session: NanoHTTPD.IHTTPSession): String? {
        val header = session.headers["authorization"] ?: session.headers["Authorization"]
            ?: return null
        if (header.startsWith("P2P-Key ", ignoreCase = true)) {
            return header.substringAfter("P2P-Key ").trim().ifBlank { null }
        }
        return null
    }

    // ============== 密钥生成 ==============

    /**
     * 使用 Java SecureRandom 生成32字符的密钥。
     * 字符集：大写字母 + 数字，去掉易混淆字符（I/O/0/1）。
     */
    private fun generatePeerKey(): String {
        val random = SecureRandom()
        return (1..PEER_KEY_LENGTH)
            .map { KEY_CHARS[random.nextInt(KEY_CHARS.length)] }
            .joinToString("")
    }

    // ============== 辅助方法 ==============

    private fun getLocalIPs(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    val hostAddress = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && !hostAddress.contains(":")) {
                        ips.add(hostAddress)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ips.ifEmpty { listOf(NetworkUtils.getLocalIpAddress(context)) }
    }

    private fun shortSha256(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
        }
        return files["postData"] ?: ""
    }

    private fun jsonResponse(status: Int, body: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.lookup(status) ?: NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            body
        )
    }

    private fun jsonError(status: Int, message: String): NanoHTTPD.Response {
        return jsonResponse(status, ApiResponse.error(message))
    }
}
