package cn.edu.shmtu.terminal.android.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.shmtu.terminal.android.MainActivity
import cn.edu.shmtu.terminal.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : Notifier {

    override val channelId: String = "shmtu_default_channel"
    override val channelName: String = "海大终端通知"
    override val channelDescription: String = "同步结果、新账单、点对点互传等通知"
    override val importance: Int = NotificationManager.IMPORTANCE_DEFAULT

    init {
        createChannel()
    }

    override suspend fun notify(
        title: String,
        body: String,
        type: NotificationType,
        deepLinkUri: String?,
        actions: List<NotificationAction>,
        extras: Map<String, String>
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (deepLinkUri != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri))
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        actions.forEach { action ->
            val intent = Intent(action.intentAction).apply {
                putExtra("payload", action.payload)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, action.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, action.label, pendingIntent)
        }

        val notification: Notification = builder.build()
        val notificationId = type.name.hashCode()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            channelId,
            channelName,
            importance
        ).apply {
            description = channelDescription
        }
        manager.createNotificationChannel(channel)
    }
}
