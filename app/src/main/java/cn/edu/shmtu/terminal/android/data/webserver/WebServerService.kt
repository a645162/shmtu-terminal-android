package cn.edu.shmtu.terminal.android.data.webserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.shmtu.terminal.android.MainActivity
import cn.edu.shmtu.terminal.android.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Web 服务器前台服务
 *
 * 启动时:
 * 1. 加载端口/token 配置
 * 2. 启动 BillWebServer
 * 3. 注册为前台服务(显示运行通知)
 *
 * 停止时停止 WebServer 并关闭服务。
 */
@AndroidEntryPoint
class WebServerService : Service() {

    @Inject
    lateinit var webServer: BillWebServer

    @Inject
    lateinit var webServerSettings: SettingsDataStoreWebExt

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("", 0, ""))
        Log.i(TAG, "WebServerService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COPY_URL -> {
                copyUrlToClipboard()
                return START_STICKY
            }
            ACTION_OPEN_URL -> {
                openUrlInBrowser()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val port = webServerSettings.webServerPortValue().coerceIn(1024, 65535)
        val result = webServer.start(port)
        if (result.isSuccess) {
            val url = "http://${NetworkUtils.getLocalIpAddress(this)}:${webServer.getPort()}/?token=${webServer.getCurrentToken()}"
            webServerSettings.setWebServerToken(url)
            webServerSettings.setWebServerEnabled(true)
            refreshNotification(url)
            Log.i(TAG, "WebServerService started, url=$url")
        } else {
            val error = result.exceptionOrNull()?.message ?: "unknown"
            Log.e(TAG, "Failed to start web server: $error")
            refreshNotification("启动失败: $error", isError = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
        Log.i(TAG, "WebServerService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopServer() {
        try {
            webServer.stop()
            webServerSettings.setWebServerEnabled(false)
        } catch (e: Exception) {
            Log.w(TAG, "stopServer failed", e)
        }
    }

    private fun copyUrlToClipboard() {
        val url = webServerSettings.webServerTokenValue()
        if (url.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("WebServer URL", url))
        Log.i(TAG, "URL copied to clipboard")
    }

    private fun openUrlInBrowser() {
        val url = webServerSettings.webServerTokenValue()
        if (url.isBlank()) return
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.w(TAG, "openUrlInBrowser failed", e)
        }
    }

    fun refreshNotification(url: String = webServerSettings.webServerTokenValue(), isError: Boolean = false) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(url, webServer.getPort(), isError = isError))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification denied", e)
        }
    }

    private fun buildNotification(url: String, port: Int, placeholder: String = "", isError: Boolean = false): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val copyIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WebServerService::class.java).apply { action = ACTION_COPY_URL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openBrowserIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WebServerService::class.java).apply { action = ACTION_OPEN_URL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, WebServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayUrl = url.ifBlank { placeholder.ifBlank { "WebServer starting..." } }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("账单服务运行中")
            .setContentText(displayUrl)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayUrl))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .addAction(0, "复制 URL", copyIntent)
            .addAction(0, "浏览器打开", openBrowserIntent)
            .addAction(0, "停止服务", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "账单 Web 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻通知,显示账单服务的访问地址"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WebServerService"
        const val CHANNEL_ID = "webserver_persistent"
        const val NOTIFICATION_ID = 2201
        const val ACTION_COPY_URL = "cn.edu.shmtu.terminal.android.action.WEBSERVER_COPY_URL"
        const val ACTION_OPEN_URL = "cn.edu.shmtu.terminal.android.action.WEBSERVER_OPEN_URL"
        const val ACTION_STOP = "cn.edu.shmtu.terminal.android.action.WEBSERVER_STOP"

        fun start(context: Context) {
            val intent = Intent(context, WebServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebServerService::class.java))
        }
    }
}
