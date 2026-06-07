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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat
import cn.edu.shmtu.terminal.android.MainActivity
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class P2PForegroundService : Service() {

    @Inject
    lateinit var p2pManager: P2PManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("P2P 互传服务正在运行"))
        serviceScope.launch {
            p2pManager.status.collectLatest {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(p2pManager.getNotificationSummary()))
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
        startForeground(NOTIFICATION_ID, buildNotification(p2pManager.getNotificationSummary()))
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        p2pManager.stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(content: String): Notification {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("海大终端 P2P")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, "停止服务", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "P2P 后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持 P2P 服务在后台持续可用"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "p2p_foreground_service"
        private const val NOTIFICATION_ID = 1101
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
