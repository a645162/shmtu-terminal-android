package cn.edu.shmtu.terminal.android.data.autosync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.notification.NotificationConfig
import cn.edu.shmtu.terminal.android.data.notification.NotificationType
import cn.edu.shmtu.terminal.android.data.notification.SystemNotifier
import cn.edu.shmtu.terminal.android.data.notification.bot.BotManager
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 自动同步 Worker - Doze 合规的定时同步入口。
 *
 * 设计要点:
 *  - WorkManager 自身已 Doze 合规: 在 Doze 期间会延后到维护窗口执行
 *  - 显式要求网络连接, 节省电量和流量
 *  - 同步后根据 [NotificationConfig] 决定是否触发系统通知 / 群机器人转发
 *  - 失败/异常不向上抛, 统一返回 [Result.retry] 由 WorkManager 调度重试
 */
@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase,
    private val identityRepository: IdentityRepository,
    private val settingsDataStore: SettingsDataStore,
    private val systemNotifier: SystemNotifier,
    private val botManager: BotManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = settingsDataStore.getNotificationConfig()
        return try {
            val currentIdentityId = identityRepository.getCurrentIdentityId().firstOrNull()
            if (currentIdentityId == null) {
                Log.w(TAG, "doWork: 跳过, 没有当前 identity")
                return Result.success()
            }
            Log.d(TAG, "doWork: identityId=$currentIdentityId")
            val syncResult = syncIdentityBillsUseCase(currentIdentityId)
            if (syncResult.success) {
                Log.d(TAG, "doWork: 同步成功, 新增 ${'$'}{syncResult.newCount} 条")
                if (syncResult.newCount > 0) {
                    handleNewBills(syncResult.newCount, config)
                }
                Result.success()
            } else {
                Log.w(TAG, "doWork: 同步失败: ${'$'}{syncResult.errorMessage}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork crashed", e)
            // 默认重试; WorkManager 内部尊重 backoff policy
            Result.retry()
        }
    }

    /**
     * 处理新账单通知:
     *  - 系统通知 (受 newBillsFoundEnabled / useHeadsUp / newBillThresholdAmount 控制)
     *  - Webhook 转发 (受 webhookEnabled 控制, 失败不阻塞)
     */
    private suspend fun handleNewBills(newCount: Int, config: NotificationConfig) {
        if (!config.newBillsFoundEnabled) return

        val title = "海大账单: 新增 ${'$'}newCount 条"
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val body = buildString {
            append("同步时间: ${'$'}now\n")
            append("新增笔数: ${'$'}newCount")
            if (config.newBillThresholdAmount > 0) {
                append("\n大额阈值: ${'$'}{config.newBillThresholdAmount} 元")
            }
        }

        // 1. 系统通知
        try {
            systemNotifier.notify(
                title = title,
                body = body,
                type = NotificationType.NEW_BILLS_FOUND,
                deepLinkUri = null,
                actions = emptyList(),
                extras = mapOf("newCount" to newCount.toString())
            )
        } catch (e: Exception) {
            Log.w(TAG, "系统通知失败: ${'$'}{e.message}")
        }

        // 2. Webhook 转发 (BotManager 内部已 try/catch)
        if (config.webhookEnabled) {
            val vars = mapOf(
                "time" to now,
                "amount" to "(见系统通知)",
                "merchant" to "批量 ${'$'}newCount 条",
                "newCount" to newCount.toString()
            )
            botManager.forward(
                config = config,
                title = title,
                content = body,
                vars = vars
            )
        }
    }

    companion object {
        const val WORK_NAME = "auto_sync_v2"
        private const val TAG = "AutoSyncWorker"

        /**
         * 调度自动同步任务。
         * @param intervalMinutes 同步间隔, WorkManager 强制下限 15 分钟 (Doze 维护窗口).
         */
        fun schedule(context: Context, intervalMinutes: Long) {
            val safeInterval = intervalMinutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(
                safeInterval, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "AutoSyncWorker scheduled: interval=${'$'}safeInterval min")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "AutoSyncWorker cancelled")
        }
    }
}
