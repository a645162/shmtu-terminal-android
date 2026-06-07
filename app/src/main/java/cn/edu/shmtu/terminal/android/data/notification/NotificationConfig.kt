package cn.edu.shmtu.terminal.android.data.notification

import kotlinx.serialization.Serializable

@Serializable
data class NotificationConfig(
    val syncCompleteEnabled: Boolean = true,
    val newBillsFoundEnabled: Boolean = true,
    val p2pTransferEnabled: Boolean = true,
    val p2pPairRequestEnabled: Boolean = true,
    val persistentStatusEnabled: Boolean = true,

    val useHeadsUp: Boolean = true,
    val silentOnNight: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,

    val newBillThresholdAmount: Double = 0.0,

    val webhookEnabled: Boolean = false,
    val webhookType: WebhookType = WebhookType.NONE,
    val webhookUrl: String = "",
    val webhookMessageTemplate: String = "【海大账单】{time} 消费 {amount} 元 @ {merchant}"
)

@Serializable
enum class WebhookType {
    NONE, FEISHU, WECHAT_WORK, CUSTOM
}
