package cn.edu.shmtu.terminal.android.data.notification

import cn.edu.shmtu.terminal.android.data.notification.bot.BotWebhookNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Webhook 通知协调器 - 根据 [NotificationConfig.webhookType] 分发到对应的 BotNotifier。
 */
@Singleton
class WebhookNotifier @Inject constructor(
    private val feishu: cn.edu.shmtu.terminal.android.data.notification.bot.FeishuBotNotifier,
    private val wechatWork: cn.edu.shmtu.terminal.android.data.notification.bot.WechatWorkBotNotifier,
    private val custom: cn.edu.shmtu.terminal.android.data.notification.bot.CustomWebhookNotifier
) {
    private fun pick(type: WebhookType): BotWebhookNotifier = when (type) {
        WebhookType.FEISHU -> feishu
        WebhookType.WECHAT_WORK -> wechatWork
        WebhookType.CUSTOM -> custom
        WebhookType.NONE -> custom
    }

    suspend fun send(
        webhookType: WebhookType,
        url: String,
        message: String,
        title: String
    ): Result<Unit> {
        if (webhookType == WebhookType.NONE || url.isBlank()) {
            return Result.failure(IllegalStateException("Webhook 未启用或 URL 为空"))
        }
        return pick(webhookType).sendMessage(
            webhookUrl = url,
            content = message,
            title = title
        )
    }
}
