package cn.edu.shmtu.terminal.android.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import cn.edu.shmtu.terminal.android.MainActivity
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.ui.settings.FeatureSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoSyncStatusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: FeatureSettingsStore
) {
    fun refresh() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!store.autoSyncPersistentNotificationValue()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        createChannel(manager)
        val enabled = store.autoSyncEnabledValue()
        val interval = store.autoSyncIntervalValue()
        val content = if (enabled) {
            "自动同步已开启，每 $interval 分钟检查一次"
        } else {
            "自动同步已关闭"
        }
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("海大终端自动同步")
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        1,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "自动同步状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示自动同步服务当前状态"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "auto_sync_status"
        private const val NOTIFICATION_ID = 1102
    }
}
