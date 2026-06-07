package cn.edu.shmtu.terminal.android.data.notification.bot

import android.util.Log
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自定义 Webhook - 直接 POST 简单 JSON `{title, content, timestamp, extras}`。
 * 兼容任何支持接收 JSON POST 的通用 webhook 接收端 (如 n8n / webhook.site / 自建中转)。
 */
@Singleton
class CustomWebhookNotifier @Inject constructor(
    private val okHttpClient: OkHttpClient
) : BotWebhookNotifier() {
    override val type: WebhookType = WebhookType.CUSTOM
    override val displayName: String = "自定义 Webhook"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendMessage(
        webhookUrl: String,
        content: String,
        title: String?,
        extras: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("title", title ?: "海大账单通知")
                put("content", content)
                put("timestamp", System.currentTimeMillis())
                // 把 extras 展平到顶层, 方便接收端直接读字段
                extras.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
            val request = Request.Builder()
                .url(webhookUrl)
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errBody = response.body?.string()?.take(500) ?: ""
                    Log.w(TAG, "自定义 webhook 返回 ${'$'}{response.code}: ${'$'}errBody")
                    Result.failure(RuntimeException("自定义 webhook 返回 ${'$'}{response.code}: ${'$'}errBody"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "自定义 webhook 发送失败: ${'$'}{e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CustomWebhookNotifier"
    }
}
