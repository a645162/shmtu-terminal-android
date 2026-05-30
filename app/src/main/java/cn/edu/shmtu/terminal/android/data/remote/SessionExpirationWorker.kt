package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.domain.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Session 过期检查 Worker
 * 定期检查所有有效 session，检测到过期则立即标记为无效，不再继续检查
 *
 * 对齐 Tauri 的 SessionExpirationService 和 Desktop 的 SessionExpirationService
 */
@HiltWorker
class SessionExpirationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SessionExpirationWorker"
        private const val WORK_NAME = "session_expiration_check"

        /**
         * 调度定期 session 过期检查
         * @param intervalMinutes 检查间隔（分钟），默认 10
         */
        fun schedule(context: Context, intervalMinutes: Int = 10) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SessionExpirationWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES,
                1, TimeUnit.MINUTES // flex interval: ±1 分钟浮动
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag("session_expiration")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE, // 更新已有任务
                    workRequest
                )

            Log.i(TAG, "已调度 session 过期检查 | Interval=${intervalMinutes}分钟 | Flex=±1分钟")
        }

        /**
         * 取消定期 session 过期检查
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "已取消 session 过期检查")
        }

        /**
         * 立即执行一次检查
         */
        fun checkNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SessionExpirationWorker>()
                .addTag("session_expiration_manual")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "已提交立即检查")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "开始检查 session 状态")

        try {
            // 获取所有有效 session
            val validSessions = sessionRepository.getAllValidSessions()
            Log.i(TAG, "获取到 ${validSessions.size} 个有效 session")

            if (validSessions.isEmpty()) {
                Log.i(TAG, "无有效 session，跳过检查")
                return@withContext Result.success()
            }

            var validCount = 0
            var expiredCount = 0

            for (session in validSessions) {
                val result = checkSession(session)
                if (result.isValid) {
                    validCount++
                } else {
                    expiredCount++
                    // 一旦标记为过期，不再继续检查
                    if (result.wasInvalidated) {
                        Log.i(TAG, "检测到过期 session 已标记为无效 | AccountId=${session.accountId}")
                    }
                }
            }

            Log.i(TAG, "检查完成 | Total=${validSessions.size} | Valid=$validCount | Expired=$expiredCount")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "检查异常: ${e.message}", e)
            // 出错后不重试（由下次调度的 PeriodicWork 重新处理）
            Result.success()
        }
    }

    /**
     * 检查单个 session 是否过期
     * @return SessionCheckResult
     */
    private suspend fun checkSession(session: cn.edu.shmtu.terminal.android.domain.model.Session): SessionCheckResult {
        return try {
            // 解密 cookies
            val cookiesJson = sessionRepository.getDecryptedCookies(session.accountId)
            if (cookiesJson.isNullOrBlank()) {
                Log.d(TAG, "无法解密 cookies | AccountId=${session.accountId}")
                return SessionCheckResult(isValid = false, wasInvalidated = true)
            }

            // 创建 EpayAuth 并恢复 session
            val epayAuth = EpayAuth()
            val restoreResult = epayAuth.restoreSession(cookiesJson)
            if (restoreResult.isFailure) {
                Log.w(TAG, "恢复 session 失败 | AccountId=${session.accountId} | Error=${restoreResult.exceptionOrNull()?.message}")
                sessionRepository.invalidateSession(session.accountId)
                return SessionCheckResult(isValid = false, wasInvalidated = true)
            }

            // 探测登录状态
            val probeResult = epayAuth.probeLogin()
            if (probeResult.isFailure) {
                Log.e(TAG, "探测登录状态失败 | AccountId=${session.accountId} | Error=${probeResult.exceptionOrNull()?.message}")
                return SessionCheckResult(isValid = false, wasInvalidated = false)
            }

            val probe = probeResult.getOrThrow()
            return when (probe) {
                is SessionProbe.AlreadyLoggedIn -> {
                    Log.d(TAG, "Session 有效 | AccountId=${session.accountId}")
                    SessionCheckResult(isValid = true, wasInvalidated = false)
                }
                is SessionProbe.NeedLogin -> {
                    // Session 已过期，标记为无效
                    Log.i(TAG, "检测到过期 session，正在标记为无效 | AccountId=${session.accountId}")
                    sessionRepository.invalidateSession(session.accountId)
                    SessionCheckResult(isValid = false, wasInvalidated = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查 session 异常 | AccountId=${session.accountId} | Error=${e.message}", e)
            SessionCheckResult(isValid = false, wasInvalidated = false)
        }
    }
}

data class SessionCheckResult(
    val isValid: Boolean,
    val wasInvalidated: Boolean
)
