package cn.edu.shmtu.terminal.android.data.notification.bot

import android.util.Log
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 飞书群机器人 - 使用 interactive 卡片消息格式。
 * 文档: https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
 */
@Singleton
class FeishuBotNotifier @Inject constructor(
    private val okHttpClient: OkHttpClient
) : BotWebhookNotifier() {
    override val type: WebhookType = WebhookType.FEISHU
    override val displayName: String = "飞书群机器人"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendMessage(
        webhookUrl: String,
        content: String,
        title: String?,
        extras: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("msg_type", "interactive")
                putJsonObject("card") {
                    putJsonObject("header") {
                        put("title", title ?: "海大账单通知")
                    }
                    putJsonArray("elements") {
                        addJsonObject {
                            put("tag", "div")
                            put("text", content)
                        }
                    }
                }
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
                    Log.w(TAG, "飞书返回 ${'$'}{response.code}: ${'$'}errBody")
                    Result.failure(RuntimeException("飞书返回 ${'$'}{response.code}: ${'$'}errBody"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "飞书 webhook 发送失败: ${'$'}{e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FeishuBotNotifier"
    }
}
