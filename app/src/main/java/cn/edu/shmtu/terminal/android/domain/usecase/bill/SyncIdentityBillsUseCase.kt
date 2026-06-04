package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.cas.sync.AccountContext
import cn.edu.shmtu.cas.sync.SyncOptions
import cn.edu.shmtu.cas.sync.SyncProgress as LibSyncProgress
import cn.edu.shmtu.cas.sync.SyncRangePreset
import cn.edu.shmtu.cas.sync.syncAccountsParallel
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.data.sync.RoomBillStore
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.Purpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 增量同步身份下所有账号 - 包装 lib [syncAccountsParallel]
 *
 * **多账号并行**（用户要求）：
 * - 每个账号独立 [cn.edu.shmtu.cas.auth.EpayAuth] + [RoomBillStore]
 * - 内部用 `coroutineScope { async { syncAccount(...) } }` 并行
 * - 任一失败不影响其他，汇总到结果
 * - 进度回调按 accountId 区分（lib 内部已带 accountId 字段）
 */
class SyncIdentityBillsUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(identityId: Long): SyncResult = invoke(identityId, SyncRangePreset.Month) {}

    suspend operator fun invoke(
        identityId: Long,
        onProgress: (SyncProgress) -> Unit,
    ): SyncResult = invoke(identityId, SyncRangePreset.Month, onProgress)

    suspend operator fun invoke(
        identityId: Long,
        syncRange: SyncRangePreset,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult = withContext(Dispatchers.IO) {
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        if (accountList.isEmpty()) {
            return@withContext SyncResult(0, true)
        }

        val translated: (LibSyncProgress) -> Unit = { p -> onProgress(p.toDomain()) }

        val jobs = accountList.map { account ->
            cn.edu.shmtu.cas.sync.AccountSyncJob(
                context = AccountContext(
                    accountId = account.id.toString(),
                    accountLabel = account.label,
                ),
                auth = epayAdapter.getEpayAuth(account.id),
                store = RoomBillStore(
                    billDbManager = epayAdapter.billDbManager,
                    accountId = account.id,
                    studentId = account.userId,
                    identityId = account.identityId,
                ).also {
                    // 注入 Tauri 兼容的 TOML 规则,落库时即时算 category / position / room / building
                    it.classifier = epayAdapter.classifier
                    it.positionTranslator = epayAdapter.positionTranslator
                },
                resolver = null,        // 多账号并行场景用手动验证码（如需要可在更外层串行）
                options = SyncOptions.incremental(syncRange),
                fullSync = false,
            )
        }

        val summary = syncAccountsParallel(jobs, translated)

        // 手动验证码异常优先：并行场景 lib 内部把 ManualCaptchaRequiredException 包装成 failure，
        // 这里扫描 results 把第一个 CaptchaRequiredException 重新抛出给 ViewModel 处理
        val captchaFailure = summary.results.firstOrNull { r ->
            r.result.exceptionOrNull() is cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
        }
        if (captchaFailure != null) {
            throw captchaFailure.result.exceptionOrNull() as cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
        }
        // lib 端的 ManualCaptchaRequiredException 没被翻译（理论上不会发生，但兜底）
        val libCaptcha = summary.results.firstOrNull { r ->
            r.result.exceptionOrNull() is cn.edu.shmtu.cas.session.ManualCaptchaRequiredException
        }
        if (libCaptcha != null) {
            val e = libCaptcha.result.exceptionOrNull() as cn.edu.shmtu.cas.session.ManualCaptchaRequiredException
            val imgB64 = e.captchaImageBase64.ifBlank {
                android.util.Base64.encodeToString(e.captchaImageBytes, android.util.Base64.NO_WRAP)
            }
            val accId = libCaptcha.context.accountId.toLongOrNull() ?: 0L
            val accLabel = libCaptcha.context.accountLabel
            throw cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException(
                captchaImageBase64 = imgB64,
                execution = e.execution,
                accountId = accId,
                accountLabel = accLabel,
                syncRange = syncRange,
                isFullSync = false,
                purpose = Purpose.SYNC,
            )
        }

        // 后续：更新 lastSyncTime + loginStatus
        accountList.forEach { acc ->
            val r = summary.results.firstOrNull { it.context.accountId == acc.id.toString() }
            if (r?.result?.isSuccess == true) {
                accountRepository.updateLastSyncTime(acc.id)
                accountRepository.updateLoginStatus(acc.id, "LOGGED_IN")
            }
        }

        return@withContext SyncResult(
            newCount = summary.totalNewCount,
            success = summary.allSuccess,
            errorMessage = summary.results.firstOrNull { it.result.isFailure }?.result?.exceptionOrNull()?.message,
        )
    }
}

private fun LibSyncProgress.toDomain(): SyncProgress = SyncProgress(
    status = when (val s = this.status) {
        cn.edu.shmtu.cas.sync.SyncStatus.ProbingLogin -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.ProbingLogin
        cn.edu.shmtu.cas.sync.SyncStatus.GettingCaptcha -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.GettingCaptcha
        cn.edu.shmtu.cas.sync.SyncStatus.LoggingIn -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.LoggingIn
        is cn.edu.shmtu.cas.sync.SyncStatus.Syncing -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.Syncing(s.page, s.total, newCount)
        cn.edu.shmtu.cas.sync.SyncStatus.Persisting -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.Persisting(totalNewCount)
        cn.edu.shmtu.cas.sync.SyncStatus.Completed -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.Completed(totalNewCount)
        is cn.edu.shmtu.cas.sync.SyncStatus.Failed -> cn.edu.shmtu.terminal.android.domain.model.SyncStatus.Failed(s.error)
    },
    accountIndex = accountIndex,
    accountTotal = totalAccounts,
    accountLabel = currentAccount,
)
