package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.cas.sync.AccountContext
import cn.edu.shmtu.cas.sync.SyncProgress as LibSyncProgress
import cn.edu.shmtu.cas.sync.syncAccountsParallel
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.data.sync.RoomBillStore
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
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
    suspend operator fun invoke(identityId: Long): SyncResult = invoke(identityId) {}

    suspend operator fun invoke(identityId: Long, onProgress: (SyncProgress) -> Unit): SyncResult {
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        if (accountList.isEmpty()) {
            return SyncResult(0, true)
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
                ),
                resolver = null,        // 多账号并行场景用手动验证码（如需要可在更外层串行）
                range = null,           // 增量
            )
        }

        val summary = syncAccountsParallel(jobs, translated)

        // 后续：更新 lastSyncTime + loginStatus
        accountList.forEach { acc ->
            val r = summary.results.firstOrNull { it.context.accountId == acc.id.toString() }
            if (r?.result?.isSuccess == true) {
                accountRepository.updateLastSyncTime(acc.id)
                accountRepository.updateLoginStatus(acc.id, "LOGGED_IN")
            }
        }

        return SyncResult(
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
