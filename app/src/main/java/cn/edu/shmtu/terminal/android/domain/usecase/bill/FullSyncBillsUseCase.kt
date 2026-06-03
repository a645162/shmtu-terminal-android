package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.cas.sync.SyncOptions
import cn.edu.shmtu.cas.sync.SyncProgress as LibSyncProgress
import cn.edu.shmtu.cas.sync.SyncRangePreset
import cn.edu.shmtu.cas.sync.fullSync
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.data.sync.RoomBillStore
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 全量同步单个账号（清除旧数据 + 清除旧 session 后重新同步）
 * 包装 lib [fullSync]：`SyncOptions.clearBeforeMerge=true` 触发 [RoomBillStore.clear]
 */
class FullSyncAccountBillsUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(account: Account): SyncResult = invoke(account) {}

    suspend operator fun invoke(account: Account, onProgress: (SyncProgress) -> Unit): SyncResult {
        val auth = epayAdapter.getEpayAuth(account.id)
        val store = RoomBillStore(
            billDbManager = epayAdapter.billDbManager,
            accountId = account.id,
            studentId = account.userId,
            identityId = account.identityId,
        )

        // 全量更新：先清 session 强制重登
        epayAdapter.invalidateSession(account.id)

        return try {
            val libResult = fullSync(
                auth = auth,
                store = store,
                options = SyncOptions.full(SyncRangePreset.All),
                onProgress = { p -> onProgress(p.toDomain()) },
            )
            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
            SyncResult(
                newCount = libResult.getOrNull()?.newCount ?: 0,
                success = libResult.isSuccess,
                errorMessage = libResult.exceptionOrNull()?.message,
            )
        } catch (e: Exception) {
            onProgress(SyncProgress(
                status = cn.edu.shmtu.terminal.android.domain.model.SyncStatus.Failed(e.message ?: e.javaClass.simpleName),
                accountLabel = account.label,
            ))
            SyncResult(0, false, e.message)
        }
    }
}

/**
 * 全量同步身份下所有账号
 * 包装 lib [syncAccountsParallel]（range=All 走全量路径）
 */
class FullSyncIdentityBillsUseCase @Inject constructor(
    private val fullSyncAccountBillsUseCase: FullSyncAccountBillsUseCase,
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(identityId: Long): SyncResult = invoke(identityId) {}

    suspend operator fun invoke(identityId: Long, onProgress: (SyncProgress) -> Unit): SyncResult {
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        val total = accountList.size
        var totalNew = 0
        var hasError = false
        var errorMsg: String? = null

        accountList.forEachIndexed { index, account ->
            val result = fullSyncAccountBillsUseCase(account) { progress ->
                onProgress(progress.copy(
                    accountIndex = index,
                    accountTotal = total,
                ))
            }
            totalNew += result.newCount
            if (!result.success) {
                hasError = true
                errorMsg = result.errorMessage
            }
        }

        return SyncResult(totalNew, !hasError, errorMsg)
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
