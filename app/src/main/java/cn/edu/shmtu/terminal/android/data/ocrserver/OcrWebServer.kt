package cn.edu.shmtu.terminal.android.data.ocrserver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import cn.edu.shmtu.cas.captcha.CaptchaOcrHelper
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.terminal.android.data.webserver.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 Android 端的 NCNN OCR 推理能力暴露为 RESTful HTTP 服务器。
 *
 * **懒加载**: 服务启动时不加载模型,首次 `POST /api/ocr` 收到请求时才触发
 * [NcnnModelLoader.ensureLoaded] (优先用 filesDir 里下载好的模型,
 * 失败时回退到 v1 内置 assets 模型,再失败才返回 503)。
 *
 * **协议对齐**: 兼容 Tauri 端 `shmtu-cas-rs` 的 `CaptchaOcrHttp` 客户端契约 —
 * 请求 `{"imageBase64": "..."}`,响应 `{"success": true, "expression": "3+5=8", "result": 8}`
 * 或 `{"success": false, "error": "..."}`。`/api/health` 返回模型加载状态。
 */
@Singleton
class OcrWebServer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: OcrServerSettings,
) {
    companion object {
        private const val TAG = "OcrWebServer"
        private const val TOKEN_BYTES = 16
        private const val SOCKET_READ_TIMEOUT_MS = 30_000
        // 首次模型加载给充足时间(后续请求都用热模型,毫秒级)
        private const val LAUNCH_LOAD_TIMEOUT_MS = 90_000L
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ncnn: SHMTU_NCNN = SHMTU_NCNN()
    /** 单实例模型加载互斥,避免并发首次请求重复加载。 */
    private val loadMutex = Mutex()
    /** 模型当前已加载的版本,用于避免无意义的二次加载与切换检测。 */
    @Volatile
    private var loadedVersion: SHMTU_NCNN_Model.ModelVersion? = null

    // 简易埋点
    private val totalRequests = AtomicLong(0)
    private val successRequests = AtomicLong(0)
    private val failedRequests = AtomicLong(0)
    private val totalResponseMs = AtomicLong(0)

    @Volatile
    private var innerServer: NanoHTTPD? = null
    @Volatile
    private var runningPort: Int = 0
    @Volatile
    private var currentToken: String = ""

    fun isRunning(): Boolean = innerServer?.isAlive == true
    fun getPort(): Int = runningPort
    fun getCurrentToken(): String = currentToken
    fun isModelLoaded(): Boolean = loadedVersion != null

    /**
     * 启动 HTTP 服务器。
     *
     * **不加载模型** — 模型在首次 `POST /api/ocr` 时才加载。
     *
     * @param port 监听端口
     * @param bindHost 绑定 IP 字符串 ("127.0.0.1" / "0.0.0.0" / 指定 IP),
     *                 默认 "0.0.0.0" (向后兼容)。
     */
    fun start(
        port: Int = settings.port(),
        bindHost: String = settings.resolvedBindAddress(),
    ): Result<Unit> {
        return try {
            if (innerServer?.isAlive == true) {
                Log.w(TAG, "OcrWebServer already running on port $runningPort")
                return Result.success(Unit)
            }
            currentToken = loadOrCreateToken()
            val server = object : NanoHTTPD(bindHost, port) {
                override fun serve(session: IHTTPSession): Response {
                    return handleRequest(session)
                }
            }
            server.start(SOCKET_READ_TIMEOUT_MS, false)
            innerServer = server
            runningPort = port
            Log.i(TAG, "OcrWebServer started on $bindHost:$port (lazy-load model, scope=${settings.scope()})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OcrWebServer on $bindHost:$port", e)
            Result.failure(e)
        }
    }

    fun stop() {
        try {
            innerServer?.stop()
            Log.i(TAG, "OcrWebServer stopped")
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        } finally {
            innerServer = null
            runningPort = 0
            runCatching {
                ncnn.releaseV2Model()
                ncnn.releaseModel()
            }
            loadedVersion = null
        }
    }

    fun shutdown() {
        stop()
        serverScope.cancel()
    }

    private fun loadOrCreateToken(): String {
        val existing = settings.authToken()
        if (existing.isNotBlank()) return existing
        val newToken = generateToken()
        settings.setAuthToken(newToken)
        return newToken
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ============== 请求路由 ==============

    private fun handleRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method
        return try {
            when {
                uri == "/api/health" && method == NanoHTTPD.Method.GET -> handleHealth()
                uri == "/api/info" && method == NanoHTTPD.Method.GET -> handleInfo()
                uri == "/api/status" && method == NanoHTTPD.Method.GET -> handleStatus()
                uri == "/api/ocr" && method == NanoHTTPD.Method.POST -> handleOcr(session)
                else -> jsonError(404, "Not Found: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleRequest error", e)
            jsonError(500, "Internal error: ${e.message}")
        }
    }

    private fun handleHealth(): NanoHTTPD.Response {
        val payload = JSONObject().apply {
            put("status", if (isModelLoaded()) "healthy" else "loading")
            put("models_loaded", isModelLoaded())
            put("model_version", loadedVersion?.name ?: settings.modelVersion())
            put("server", "android-shmtu-ocr")
        }
        return jsonResponse(200, payload)
    }

    private fun handleInfo(): NanoHTTPD.Response {
        val payload = JSONObject().apply {
            put("deviceName", android.os.Build.MODEL ?: "SHMTU Device")
            put("ipAddress", NetworkUtils.getLocalIpAddress(context))
            put("port", runningPort)
            put("token", currentToken)
            put("protocolVersion", "1.0")
            put("server", "android-shmtu-ocr")
            put("scope", settings.scope().name)
            put("bindAddress", settings.resolvedBindAddress())
        }
        return jsonResponse(200, payload)
    }

    private fun handleStatus(): NanoHTTPD.Response {
        val total = totalRequests.get()
        val payload = JSONObject().apply {
            put("status", if (isModelLoaded()) "healthy" else "loading")
            put("models_loaded", isModelLoaded())
            put("model_version", loadedVersion?.name ?: settings.modelVersion())
            put("v2_backbone", settings.v2Backbone())
            put("v2_precision", settings.v2Precision())
            put("scope", settings.scope().name)
            put("bind_address", settings.resolvedBindAddress())
            put("total_requests", total)
            put("success_count", successRequests.get())
            put("failure_count", failedRequests.get())
            put("avg_response_ms", if (total > 0) totalResponseMs.get().toDouble() / total else 0.0)
            put("queue_capacity", 16)
            put("pending_requests", 0)
        }
        return jsonResponse(200, payload)
    }

    /**
     * POST /api/ocr  body: {"imageBase64": "..."}
     * 响应: {"success": true, "expression": "3+5=8", "result": 8, "modelVersion": "V2"}
     */
    private fun handleOcr(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val start = SystemClock.elapsedRealtime()
        totalRequests.incrementAndGet()

        if (!isAuthorized(session)) {
            failedRequests.incrementAndGet()
            return jsonError(401, "Unauthorized - token invalid or missing")
        }

        val body = readBody(session)
        val req = try {
            JSONObject(body)
        } catch (e: Exception) {
            failedRequests.incrementAndGet()
            return jsonError(400, "Invalid JSON body")
        }

        val imageBase64 = req.optString("imageBase64", "").trim()
        if (imageBase64.isEmpty()) {
            failedRequests.incrementAndGet()
            return jsonError(400, "imageBase64 is empty")
        }

        // 支持客户端覆盖模型版本(默认采用服务端配置)
        val reqVersionOverride = req.optString("modelVersion", "").trim()
            .ifEmpty { req.optString("model_version", "").trim() }
        val versionStr = reqVersionOverride.ifEmpty { settings.modelVersion() }
        val version = SHMTU_NCNN_Model.ModelVersion.fromString(versionStr)
        val backbone = settings.v2Backbone()
        val precision = settings.v2Precision()

        val imageBytes = try {
            Base64.decode(imageBase64, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            failedRequests.incrementAndGet()
            return jsonError(400, "imageBase64 decode failed: ${e.message}")
        }
        if (imageBytes.isEmpty()) {
            failedRequests.incrementAndGet()
            return jsonError(400, "imageBase64 decoded to empty bytes")
        }

        val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: run {
                failedRequests.incrementAndGet()
                return jsonError(400, "image decode failed (not a valid bitmap)")
            }

        // 懒加载: 首次请求才加载,带锁防并发
        val loadResult = runBlocking {
            withTimeoutOrNull(LAUNCH_LOAD_TIMEOUT_MS) {
                ensureLoadedForRequest(version, backbone, precision)
            }
        }
        if (loadResult == null) {
            runCatching { bitmap.recycle() }
            failedRequests.incrementAndGet()
            return jsonError(503, "model load timeout (${LAUNCH_LOAD_TIMEOUT_MS / 1000}s)")
        }
        if (loadResult.isFailure) {
            runCatching { bitmap.recycle() }
            failedRequests.incrementAndGet()
            return jsonError(503, "model load failed: ${loadResult.exceptionOrNull()?.message}")
        }

        // 推理
        val predictResult = try {
            when (loadedVersion) {
                SHMTU_NCNN_Model.ModelVersion.V2 -> ncnn.predict_validate_code_v2(bitmap)
                SHMTU_NCNN_Model.ModelVersion.V1 -> ncnn.predict_validate_code(bitmap)
                else -> null
            }
        } catch (e: Throwable) {
            failedRequests.incrementAndGet()
            return jsonError(500, "predict failed: ${e.message}")
        } finally {
            runCatching { bitmap.recycle() }
        }

        val expression = CaptchaOcrHelper.buildExprString(predictResult?.copyToArrayOfAny())
        if (expression.isNullOrBlank()) {
            failedRequests.incrementAndGet()
            return jsonError(422, "OCR returned empty expression")
        }

        val answer = CaptchaOcrHelper.extractAnswer(expression)
        val elapsed = SystemClock.elapsedRealtime() - start
        totalResponseMs.addAndGet(elapsed)
        successRequests.incrementAndGet()

        val payload = JSONObject().apply {
            put("success", true)
            put("expression", expression)
            put("result", answer.toIntOrNull())
            put("modelVersion", loadedVersion!!.name)
            put("duration_ms", elapsed)
        }
        Log.i(TAG, "OCR ok expr=$expression answer=$answer (${elapsed}ms, ${loadedVersion?.name})")
        return jsonResponse(200, payload)
    }

    /**
     * 懒加载: 仅当模型尚未加载或 (当前 version 不一致) 时加载。
     */
    private suspend fun ensureLoadedForRequest(
        version: SHMTU_NCNN_Model.ModelVersion,
        backbone: String,
        precision: String,
    ): Result<Unit> = loadMutex.withLock {
        if (loadedVersion == version) return@withLock Result.success(Unit)

        if (loadedVersion != null) {
            when (loadedVersion) {
                SHMTU_NCNN_Model.ModelVersion.V1 -> runCatching { ncnn.releaseModel() }
                SHMTU_NCNN_Model.ModelVersion.V2 -> runCatching { ncnn.releaseV2Model() }
                null -> {}
            }
            loadedVersion = null
        }
        try {
            val ok = NcnnModelLoader.ensureLoaded(ncnn, context, version, useGpu = false, backbone, precision)
            if (!ok) {
                return@withLock Result.failure(IllegalStateException("ensureLoaded returned false"))
            }
            loadedVersion = version
            Log.i(TAG, "lazy-loaded NCNN model version=$version backbone=$backbone precision=$precision")
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun isAuthorized(session: NanoHTTPD.IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: session.headers["Authorization"]
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            val bearer = header.substringAfter("Bearer ").trim()
            if (bearer == currentToken) return true
        }
        val tokenParam = session.parameters["token"]?.firstOrNull()
        if (tokenParam != null && tokenParam == currentToken) return true
        return false
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (_: Exception) {
        }
        return files["postData"] ?: ""
    }

    private fun jsonResponse(status: Int, body: JSONObject): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.lookup(status) ?: NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            body.toString()
        )
    }

    private fun jsonError(status: Int, message: String): NanoHTTPD.Response {
        val payload = JSONObject().apply {
            put("success", false)
            put("error", message)
        }
        return jsonResponse(status, payload)
    }

    /**
     * Java `Object[]` → Kotlin `Array<Any?>` 的安全转换。
     *
     * SHMTU_NCNN.predict_validate_code_* 返回 `Object[]`,而 [CaptchaOcrHelper.buildExprString]
     * 期望 `Array<Any?>?`。直接 `as Array<Any?>?` 在 Java 数组的运行时类型上可能擦除失败,这里
     * 用逐元素构造保证类型安全。
     */
    private fun Array<out Any?>.copyToArrayOfAny(): Array<Any?> = Array(size) { this[it] }
}