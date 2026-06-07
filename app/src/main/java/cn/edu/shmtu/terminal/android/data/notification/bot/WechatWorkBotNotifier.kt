package cn.edu.shmtu.terminal.android.data.notification.bot

import android.util.Log
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * 企业微信群机器人 - 使用 Markdown 消息格式。
 * 文档: https://developer.work.weixin.qq.com/document/path/91770
 */
@Singleton
class WechatWorkBotNotifier @Inject constructor(
    private val okHttpClient: OkHttpClient
) : BotWebhookNotifier() {
    override val type: WebhookType = WebhookType.WECHAT_WORK
    override val displayName: String = "企业微信群机器人"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun sendMessage(
        webhookUrl: String,
        content: String,
        title: String?,
        extras: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 企业微信 Markdown 消息: 把 title 拼到 markdown 顶部保证视觉一致
            val markdownBody = buildString {
                if (!title.isNullOrBlank()) append("## ${'$'}title\n\n")
                append(content)
            }
            val payload = buildJsonObject {
                put("msgtype", "markdown")
                putJsonObject("markdown") {
                    put("content", markdownBody)
                }
                putJsonArray("mentioned_list") {}
                putJsonArray("mentioned_mobile_list") {}
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
                    Log.w(TAG, "企业微信返回 ${'$'}{response.code}: ${'$'}errBody")
                    Result.failure(RuntimeException("企业微信返回 ${'$'}{response.code}: ${'$'}errBody"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "企业微信 webhook 发送失败: ${'$'}{e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "WechatWorkBotNotifier"
    }
}
