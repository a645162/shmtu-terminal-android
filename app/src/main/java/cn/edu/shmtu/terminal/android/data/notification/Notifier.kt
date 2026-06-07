package cn.edu.shmtu.terminal.android.data.notification

import android.app.NotificationManager
import kotlinx.serialization.Serializable

/**
 * 抽象通知器接口
 * 不同实现：SystemNotifier（系统通知）、FeishuBotNotifier、WechatWorkBotNotifier
 */
interface Notifier {
    val channelId: String
    val channelName: String
    val channelDescription: String
    val importance: Int

    suspend fun notify(
        title: String,
        body: String,
        type: NotificationType,
        deepLinkUri: String? = null,
        actions: List<NotificationAction> = emptyList(),
        extras: Map<String, String> = emptyMap()
    )
}

@Serializable
enum class NotificationType {
    SYNC_COMPLETE,
    NEW_BILLS_FOUND,
    P2P_TRANSFER,
    P2P_PAIR_REQUEST,
    PERSISTENT_STATUS,
    FOREGROUND_SERVICE
}

@Serializable
data class NotificationAction(
    val id: String,
    val label: String,
    val intentAction: String,
    val payload: String? = null
)
