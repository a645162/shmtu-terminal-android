package cn.edu.shmtu.terminal.android.data.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 云备份定时任务（WorkManager）
 *
 * 根据 SettingsDataStore.cloudBackupAuto* 配置周期性执行。
 */
@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val manager: CloudBackupManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "CloudBackupWorker"

    override suspend fun doWork(): Result {
        return try {
            val providerId = settingsDataStore.getCloudBackupProviderId() ?: run {
                Log.w(tag, "No cloud backup provider configured, skip")
                return Result.success()
            }
            val enabled = settingsDataStore.getCloudBackupAutoEnabledValue()
            if (!enabled) {
                Log.i(tag, "Cloud backup auto disabled, skip")
                return Result.success()
            }
            val password = settingsDataStore.getCloudBackupAutoPassword().ifBlank { null }
            Log.i(tag, "Starting scheduled cloud backup to $providerId")
            val result = manager.backupNow(providerId, password)
            if (result.isSuccess) {
                Log.i(tag, "Scheduled cloud backup succeeded")
                Result.success()
            } else {
                Log.w(tag, "Scheduled cloud backup failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(tag, "CloudBackupWorker failed", e)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "cloud_backup_worker"
    }
}
