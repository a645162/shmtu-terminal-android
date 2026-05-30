package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 全量同步单个账号（清除旧数据 + 清除旧 session 后重新同步）
 * 对齐 Tauri 的 full_sync_account 命令
 */
class FullSyncAccountBillsUseCase @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(account: Account): SyncResult {
        return invoke(account) {}
    }

    suspend operator fun invoke(account: Account, onProgress: (SyncProgress) -> Unit): SyncResult {
        try {
            onProgress(SyncProgress(
                status = SyncStatus.ProbingLogin,
                accountLabel = account.label
            ))

            // 全量更新：清除旧 session，强制重新登录
            sessionRepository.invalidateSession(account.userId)

            // 全量更新：清除旧账单数据
            val accountDb = billDbManager.getAccountDatabase(account.userId)
            val identityDb = billDbManager.getIdentityDatabase(account.identityId)
            accountDb.billDao().deleteByAccountId(account.id)
            identityDb.billDao().deleteByAccountId(account.id)

            // 重新同步所有页
            var pageNo = 1
            val allBills = mutableListOf<BillEntity>()
            var shouldContinue = true
            var pageCount = 1

            while (shouldContinue) {
                val result = epayAdapter.fetchBillPage(account.id, pageNo)

                if (result.isFailure) {
                    onProgress(SyncProgress(status = SyncStatus.Failed(result.exceptionOrNull()?.message ?: "未知错误")))
                    return SyncResult(allBills.size, false, result.exceptionOrNull()?.message ?: "未知错误")
                }

                val html = result.getOrNull() ?: ""

                if (html == "SESSION_EXPIRED") {
                    onProgress(SyncProgress(status = SyncStatus.Failed("Session expired, need re-login")))
                    return SyncResult(0, false, "Session expired, need re-login")
                }

                pageCount = epayAdapter.getPageCount(html)
                val pageBills = epayAdapter.parseBillList(html).map { map ->
                    BillEntity(
                        accountId = account.id,
                        accountLabel = account.label,
                        dateStr = map["dateStr"] ?: "",
                        timeStr = map["timeStr"] ?: "",
                        dateTimeStrFormat = map["dateTimeStrFormat"] ?: "",
                        type = map["type"] ?: "",
                        transactionNo = map["number"] ?: "",
                        targetUser = map["targetUser"] ?: "",
                        money = map["money"] ?: "",
                        method = map["method"] ?: "",
                        status = map["status"] ?: ""
                    )
                }
                allBills.addAll(pageBills)
                shouldContinue = pageNo < pageCount

                onProgress(SyncProgress(
                    status = SyncStatus.Syncing(
                        page = pageNo,
                        total = pageCount,
                        newCount = allBills.size
                    ),
                    accountLabel = account.label
                ))

                pageNo++
            }

            if (allBills.isNotEmpty()) {
                onProgress(SyncProgress(
                    status = SyncStatus.Persisting(totalNew = allBills.size),
                    accountLabel = account.label
                ))
                accountDb.billDao().insertAll(allBills)
                identityDb.billDao().insertAll(allBills)
            }

            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")

            onProgress(SyncProgress(
                status = SyncStatus.Completed(totalNew = allBills.size),
                accountLabel = account.label
            ))

            return SyncResult(newCount = allBills.size)
        } catch (e: Exception) {
            onProgress(SyncProgress(status = SyncStatus.Failed(e.message ?: "未知错误")))
            return SyncResult(0, false, e.message)
        }
    }
}

/**
 * 全量同步身份下所有账号（清除旧数据 + 清除旧 session 后重新同步）
 * 对齐 Tauri 的 full_sync 命令
 */
class FullSyncIdentityBillsUseCase @Inject constructor(
    private val fullSyncAccountBillsUseCase: FullSyncAccountBillsUseCase,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(identityId: Long): SyncResult {
        return invoke(identityId) {}
    }

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
                    accountTotal = total
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
