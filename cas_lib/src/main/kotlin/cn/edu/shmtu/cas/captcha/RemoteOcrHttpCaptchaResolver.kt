package cn.edu.shmtu.cas.captcha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.logging.Logger

/**
 * 远程 HTTP OCR 验证码解析器（新 RESTful API）
 *
 * 对齐 Rust 版本的 ocr_http.rs：
 * - POST {base_url}/api/ocr  Body: {"imageBase64": "<base64>"}
 * - Response: {"success": bool, "expression": "12+34=", "result": 46, "error": "..."}
 * - 超时 10s，重试 retryTimes 次
 * - 健康检查 GET {base_url}/api/health
 */
class RemoteOcrHttpCaptchaResolver(
    private val baseUrl: String,
    private val retryTimes: Int = 3
) : CaptchaResolver {

    private companion object {
        val log = Logger.getLogger(RemoteOcrHttpCaptchaResolver::class.java.name)
        val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    data class OcrRequest(val imageBase64: String)

    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        var lastException: Exception? = null
        repeat(retryTimes) { attempt ->
            try {
                val result = doResolve(imageData)
                if (result.isSuccess) return result
                lastException = Exception(result.exceptionOrNull()?.message ?: "OCR 识别失败")
            } catch (e: Exception) {
                lastException = e
            }
            log.warning("[RemoteOcrHttp] resolve: attempt ${attempt + 1}/$retryTimes failed: ${lastException?.message}")
        }
        return Result.failure(lastException ?: Exception("OCR 识别失败"))
    }

    private suspend fun doResolve(imageData: ByteArray): Result<CaptchaAnswer> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/ocr")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doOutput = true

                val base64Image = Base64.getEncoder().encodeToString(imageData)
                val requestBody = json.encodeToString(OcrRequest(base64Image))
                connection.outputStream.use { os ->
                    os.write(requestBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }

                val response = connection.inputStream.bufferedReader().readText()
                val responseJson = json.parseToJsonElement(response).jsonObject

                if (responseJson["success"]?.jsonPrimitive?.booleanOrNull != true) {
                    val error = responseJson["error"]?.jsonPrimitive?.contentOrNull ?: "OCR 识别失败"
                    return@withContext Result.failure(Exception(error))
                }

                val expression = responseJson["expression"]?.jsonPrimitive?.contentOrNull ?: ""
                if (expression.isNotEmpty()) {
                    log.info("[RemoteOcrHttp] resolve: expression='$expression'")
                    Result.success(CaptchaAnswer(expression, CaptchaAnswerKind.EXPRESSION))
                } else {
                    Result.failure(Exception("OCR 返回空表达式"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 健康检查，GET {base_url}/api/health
     */
    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
