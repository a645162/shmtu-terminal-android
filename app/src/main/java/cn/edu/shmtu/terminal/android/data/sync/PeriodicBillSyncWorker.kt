package cn.edu.shmtu.terminal.android.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

/**
 * 定时账单同步 Worker - 对齐 Tauri `AutoSyncService`。
 *
 * 配置由 [SHMTUTerminalApp] 在启动时读 [FeatureSettingsStore] 后, 用
 * [androidx.work.PeriodicWorkRequest.Builder.setInputData] 注入 enabled/interval。
 * Worker 内部**不**直接引用 [FeatureSettingsStore] 以避免 KSP 跨包解析 bug。
 */
@HiltWorker
class PeriodicBillSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase,
    private val identityRepository: IdentityRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = inputData.getBoolean(KEY_ENABLED, false)
        if (!enabled) {
            Log.d(TAG, "doWork: skipped (disabled at run time)")
            return Result.success()
        }
        return try {
            val currentIdentityId = identityRepository.getCurrentIdentityId().firstOrNull()
            if (currentIdentityId == null) {
                Log.w(TAG, "skip: no current identity")
                return Result.success()
            }
            Log.d(TAG, "doWork: identityId=$currentIdentityId")
            val r = syncIdentityBillsUseCase(currentIdentityId)
            if (r.success) {
                Log.d(TAG, "doWork: synced ${r.newCount} bills")
                Result.success()
            } else {
                Log.w(TAG, "doWork: failed: ${r.errorMessage}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork crashed", e)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "periodic_bill_sync"
        private const val TAG = "PeriodicBillSync"
        const val KEY_ENABLED = "key_enabled"
        const val KEY_INTERVAL = "key_interval_min"
    }
}
