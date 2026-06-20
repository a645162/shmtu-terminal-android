package cn.edu.shmtu.terminal.android.data.ocrserver

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
import cn.edu.shmtu.terminal.android.data.webserver.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * OCR 推理服务器前台服务
 *
 * 启动时:
 * 1. 启动 OcrWebServer (懒加载模型,首次 POST /api/ocr 才加载)
 * 2. 注册为前台服务 (显示运行通知 + URL)
 *
 * 停止时停止 OcrWebServer 并关闭服务。
 */
@AndroidEntryPoint
class OcrServerService : Service() {

    @Inject
    lateinit var ocrWebServer: OcrWebServer

    @Inject
    lateinit var ocrServerSettings: OcrServerSettings

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("", 0, isError = false))
        Log.i(TAG, "OcrServerService onCreate")
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
        val port = ocrServerSettings.port().coerceIn(1024, 65535)
        val result = ocrWebServer.start(port)
        if (result.isSuccess) {
            val url = buildAccessUrl()
            refreshNotification(url)
            Log.i(TAG, "OcrServerService started, url=$url")
        } else {
            val error = result.exceptionOrNull()?.message ?: "unknown"
            Log.e(TAG, "Failed to start ocr server: $error")
            refreshNotification("启动失败: $error", isError = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
        Log.i(TAG, "OcrServerService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopServer() {
        try {
            ocrWebServer.stop()
            ocrServerSettings.setEnabled(false)
        } catch (e: Exception) {
            Log.w(TAG, "stopServer failed", e)
        }
    }

    private fun buildAccessUrl(): String {
        val ip = NetworkUtils.getLocalIpAddress(this)
        val token = ocrWebServer.getCurrentToken()
        return "http://$ip:${ocrWebServer.getPort()}/?token=$token"
    }

    private fun copyUrlToClipboard() {
        val url = buildAccessUrl()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("OcrServer URL", url))
        Log.i(TAG, "URL copied to clipboard")
    }

    private fun openUrlInBrowser() {
        val url = buildAccessUrl()
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.w(TAG, "openUrlInBrowser failed", e)
        }
    }

    fun refreshNotification(url: String = buildAccessUrl(), isError: Boolean = false) {
        try {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(url, ocrWebServer.getPort(), isError = isError)
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification denied", e)
        }
    }

    private fun buildNotification(url: String, port: Int, isError: Boolean = false): Notification {
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
            Intent(this, OcrServerService::class.java).apply { action = ACTION_COPY_URL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openBrowserIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OcrServerService::class.java).apply { action = ACTION_OPEN_URL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, OcrServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayUrl = url.ifBlank { if (isError) "OCR 服务启动失败" else "OCR 服务启动中..." }
        val title = if (isError) "OCR 推理服务启动失败" else "OCR 推理服务运行中"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
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
            "OCR 推理服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻通知,显示 OCR 推理服务的访问地址"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "OcrServerService"
        const val CHANNEL_ID = "ocr_server_persistent"
        const val NOTIFICATION_ID = 2301
        const val ACTION_COPY_URL = "cn.edu.shmtu.terminal.android.action.OCRSERVER_COPY_URL"
        const val ACTION_OPEN_URL = "cn.edu.shmtu.terminal.android.action.OCRSERVER_OPEN_URL"
        const val ACTION_STOP = "cn.edu.shmtu.terminal.android.action.OCRSERVER_STOP"

        fun start(context: Context) {
            val intent = Intent(context, OcrServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OcrServerService::class.java))
        }
    }
}