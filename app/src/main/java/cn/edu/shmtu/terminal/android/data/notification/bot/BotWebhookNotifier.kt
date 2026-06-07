package cn.edu.shmtu.terminal.android.data.notification.bot

import cn.edu.shmtu.terminal.android.data.notification.WebhookType

/**
 * 抽象 Webhook 通知器基类
 * 子类实现 [sendMessage] 即可对接不同平台 (飞书/企业微信/自定义)。
 *
 * 线程模型: 子类的 sendMessage 应自行切换到 IO 调度器 (withContext(Dispatchers.IO))
 *           因为调用方在 Worker 协程中, 直接调用会阻塞主线程。
 */
abstract class BotWebhookNotifier {
    abstract val type: WebhookType
    abstract val displayName: String

    /**
     * 发送消息到 webhook URL。
     * 返回 [Result.success] 表示 HTTP 调用成功 (2xx),
     * 返回 [Result.failure] 包含平台错误信息或网络异常。
     */
    abstract suspend fun sendMessage(
        webhookUrl: String,
        content: String,
        title: String? = null,
        extras: Map<String, String> = emptyMap()
    ): Result<Unit>

    /**
     * 用 {key} 占位符渲染消息模板。
     * 子类若平台支持额外变量 (如 @用户名), 可在 [sendMessage] 内自行处理。
     */
    open fun renderMessage(template: String, vars: Map<String, String>): String {
        var result = template
        vars.forEach { (k, v) -> result = result.replace("{$k}", v) }
        return result
    }
}
