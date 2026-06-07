package cn.edu.shmtu.terminal.android.data.webserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Web Server 操作广播接收器
 *
 * 处理来自通知或外部的广播:
 * - ACTION_COPY_URL: 复制访问地址到剪贴板
 * - ACTION_OPEN_URL: 在浏览器中打开
 * - ACTION_STOP_SERVICE: 停止服务
 */
@AndroidEntryPoint
class WebServerActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var webServerSettings: SettingsDataStoreWebExt

    @Inject
    lateinit var webServer: BillWebServer

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WebServerService.ACTION_COPY_URL -> {
                val url = webServerSettings.webServerTokenValue()
                if (url.isNotBlank()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("WebServer URL", url))
                    Log.i(TAG, "URL copied via receiver: $url")
                }
            }
            WebServerService.ACTION_OPEN_URL -> {
                val url = webServerSettings.webServerTokenValue()
                if (url.isNotBlank()) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "openUrl failed", e)
                    }
                }
            }
            WebServerService.ACTION_STOP -> {
                WebServerService.stop(context)
            }
        }
    }

    companion object {
        private const val TAG = "WebServerActionReceiver"
    }
}
