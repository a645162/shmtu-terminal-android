package cn.edu.shmtu.terminal.android

import android.app.Application
import android.util.Log
import cn.edu.shmtu.terminal.android.data.remote.SessionExpirationWorker
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SHMTUTerminalApp : Application() {

    @Inject
    lateinit var billRulesManager: BillRulesManager

    /**
     * 全局后台协程作用域: 供应用级异步任务(如 GitHub 规则拉取)使用。
     * 不需要 lifecycle-process 依赖, SupervisorJob 让单任务失败不影响其他任务。
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 启动定期 session 过期检查（默认 10 分钟间隔，±1 分钟浮动）
        SessionExpirationWorker.schedule(this, intervalMinutes = 10)
        Log.i("SHMTUTerminalApp", "Session 过期检查已启动")

        // 启动时异步从 GitHub 拉取账单规则文件 (rules/type/position/schedule.toml),
        // 缺失时补齐, 写盘到 filesDir/bill/。与 Tauri `DatabaseFileManager.ensure_local_files` 行为一致。
        // 不阻塞主线程, 失败时由后续 EpayAdapter / BillRepositoryImpl 懒加载走 assets 回退。
        appScope.launch {
            try {
                val result = billRulesManager.ensureLocalFiles()
                Log.i("SHMTUTerminalApp", "账单规则本地补齐: $result")
            } catch (e: Exception) {
                Log.w("SHMTUTerminalApp", "ensureLocalFiles failed: ${e.message}")
            }
        }
    }
}
