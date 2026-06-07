package cn.edu.shmtu.terminal.android

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.edu.shmtu.terminal.android.data.p2p.P2PForegroundService
import cn.edu.shmtu.terminal.android.data.remote.SessionExpirationWorker
import cn.edu.shmtu.terminal.android.data.sync.AutoSyncStatusNotifier
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import cn.edu.shmtu.terminal.android.data.sync.PeriodicBillSyncWorker
import cn.edu.shmtu.terminal.android.data.webserver.WebServerService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SHMTUTerminalApp : Application() {

    @Inject
    lateinit var billRulesManager: BillRulesManager

    @Inject
    lateinit var autoSyncStatusNotifier: AutoSyncStatusNotifier

    /**
     * 全局后台协程作用域: 供应用级异步任务(如 GitHub 规则拉取)使用。
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动定期 session 过期检查（默认 10 分钟间隔，±1 分钟浮动）
        SessionExpirationWorker.schedule(this, intervalMinutes = 10)
        Log.i("SHMTUTerminalApp", "Session 过期检查已启动")

        // 启动时异步从 GitHub 拉取账单规则文件
        appScope.launch {
            try {
                val result = billRulesManager.ensureLocalFiles()
                Log.i("SHMTUTerminalApp", "账单规则本地补齐: $result")
            } catch (e: Exception) {
                Log.w("SHMTUTerminalApp", "ensureLocalFiles failed: ${e.message}")
            }
        }

        // 调度 WorkManager 定时账单同步 (对齐 Tauri AutoSyncService)
        // 注意: 直接 inject FeatureSettingsStore 会触发 KSP 跨包解析 bug,
        // 所以这里用 SharedPreferences 同步读 enabled + interval, 然后通过 InputData 传给 Worker。
        appScope.launch {
            try {
                val sp = getSharedPreferences("feature_settings", MODE_PRIVATE)
                val enabled = sp.getBoolean("auto_sync_enabled", false)
                if (!enabled) return@launch
                val intervalMin = sp.getInt("auto_sync_interval", 60).coerceAtLeast(15)
                val inputData = androidx.work.Data.Builder()
                    .putBoolean(PeriodicBillSyncWorker.KEY_ENABLED, true)
                    .putInt(PeriodicBillSyncWorker.KEY_INTERVAL, intervalMin)
                    .build()
                val req = PeriodicWorkRequestBuilder<PeriodicBillSyncWorker>(intervalMin.toLong(), TimeUnit.MINUTES)
                    .setInputData(inputData)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(this@SHMTUTerminalApp).enqueueUniquePeriodicWork(
                    PeriodicBillSyncWorker.NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req
                )
                Log.i("SHMTUTerminalApp", "定时账单同步已调度: 间隔 ${intervalMin} 分钟")
            } catch (e: Exception) {
                Log.w("SHMTUTerminalApp", "schedule periodic sync failed: ${e.message}")
            }
        }

        autoSyncStatusNotifier.refresh()

        appScope.launch {
            try {
                val sp = getSharedPreferences("app_settings", MODE_PRIVATE)
                val autoStartP2P = sp.getBoolean("p2p_auto_start_server", false)
                if (autoStartP2P) {
                    P2PForegroundService.start(this@SHMTUTerminalApp)
                }
                val autoStartWeb = sp.getBoolean("remote_auto_start_web", false)
                if (autoStartWeb) {
                    WebServerService.start(this@SHMTUTerminalApp)
                }
            } catch (e: Exception) {
                Log.w("SHMTUTerminalApp", "start foreground service failed: ${e.message}")
            }
        }
    }
}
