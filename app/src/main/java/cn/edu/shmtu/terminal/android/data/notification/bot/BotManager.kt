package cn.edu.shmtu.terminal.android.data.notification.bot

import android.util.Log
import cn.edu.shmtu.terminal.android.data.notification.NotificationConfig
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bot 路由器 - 根据 [NotificationConfig.webhookType] 派发到具体平台 notifier。
 *
 * 设计要点:
 *  - 早退: webhook 未启用 / 类型 NONE / URL 为空 → 直接返回 success (视为"无操作成功")
 *  - 失败隔离: 任何 webhook 失败不向上抛, 调用方无需 try/catch
 *  - 模板渲染: 在派发前用通用 {key} 占位符替换
 */
@Singleton
class BotManager @Inject constructor(
    feishuNotifier: FeishuBotNotifier,
    wechatNotifier: WechatWorkBotNotifier,
    customNotifier: CustomWebhookNotifier
) {
    private val notifiers: Map<WebhookType, BotWebhookNotifier> = mapOf(
        WebhookType.FEISHU to feishuNotifier,
        WebhookType.WECHAT_WORK to wechatNotifier,
        WebhookType.CUSTOM to customNotifier
    )

    /**
     * 转发一条消息到配置的 webhook 平台。
     *
     * @return 成功 → [Result.success]; 跳过/失败 → [Result.failure] (调用方决定是否记录)
     */
    suspend fun forward(
        config: NotificationConfig,
        title: String,
        content: String,
        vars: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.webhookEnabled) {
            Log.d(TAG, "forward: webhook 未启用, 跳过")
            return@withContext Result.success(Unit)
        }
        if (config.webhookType == WebhookType.NONE) {
            Log.d(TAG, "forward: webhook 类型为 NONE, 跳过")
            return@withContext Result.success(Unit)
        }
        if (config.webhookUrl.isBlank()) {
            Log.w(TAG, "forward: webhook URL 为空, 跳过")
            return@withContext Result.failure(IllegalArgumentException("webhook URL 为空"))
        }
        val notifier = notifiers[config.webhookType]
        if (notifier == null) {
            Log.w(TAG, "forward: 不支持的 webhook 类型 ${'$'}{config.webhookType}")
            return@withContext Result.failure(IllegalArgumentException("不支持的 webhook 类型: ${'$'}{config.webhookType}"))
        }
        val rendered = notifier.renderMessage(config.webhookMessageTemplate, vars)
        notifier.sendMessage(
            webhookUrl = config.webhookUrl,
            content = rendered,
            title = title,
            extras = vars
        ).also { result ->
            result.onFailure { e ->
                Log.w(TAG, "forward: ${'$'}{notifier.displayName} 失败: ${'$'}{e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BotManager"
    }
}
