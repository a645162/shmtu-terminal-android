package cn.edu.shmtu.terminal.android.data.p2p

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.shmtu.terminal.android.MainActivity
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.ui.settings.FeatureSettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class P2PForegroundService : Service() {

    @Inject
    lateinit var p2pManager: P2PManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var featureSettingsStore: FeatureSettingsStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch {
            p2pManager.status.collectLatest {
                refreshNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val deviceName = settingsDataStore.p2pDeviceNameFlowValue()
        val port = settingsDataStore.p2pPortFlowValue()
        p2pManager.configure(deviceName, port)
        p2pManager.startServer()
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        p2pManager.stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun refreshNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, P2PForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val p2pLine = "点对点同步: ${p2pManager.getNotificationSummary()}"
        val syncLine = if (featureSettingsStore.autoSyncEnabledValue()) {
            "自动同步: 开启 / ${featureSettingsStore.autoSyncIntervalValue()} 分钟"
        } else {
            "自动同步: 关闭"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("海大终端")
            .setContentText(p2pLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$p2pLine\n$syncLine"))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, "打开主程序", openIntent)
            .addAction(0, "关闭", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "海大终端后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示点对点同步与自动同步状态"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "p2p_foreground_service"
        const val NOTIFICATION_ID = 1101
        private const val ACTION_STOP = "cn.edu.shmtu.terminal.android.action.STOP_P2P_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, P2PForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, P2PForegroundService::class.java))
        }
    }
}
