package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * 增量同步单账号账单 - 支持细粒度进度回调
 * 对齐 Rust 版 incremental_sync_account
 */
class SyncAccountBillsUseCase @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository
) {
    /**
     * 增量同步（无进度回调，保留向后兼容）
     */
    suspend operator fun invoke(account: Account): SyncResult {
        return invoke(account) {}
    }

    /**
     * 增量同步（带进度回调）
     *
     * 对齐 Rust 版的每页进度回调：
     * - SyncStatus::ProbingLogin -> 探测登录
     * - SyncStatus::Syncing { page, total } -> 正在同步第 N/M 页
     * - SyncStatus::Persisting -> 持久化
     * - SyncStatus::Completed -> 完成
     */
    suspend operator fun invoke(account: Account, onProgress: (SyncProgress) -> Unit): SyncResult {
        try {
            val accountDb = billDbManager.getAccountDatabase(account.userId)
            val identityDb = billDbManager.getIdentityDatabase(account.identityId)
            val isFirstSync = accountDb.billDao().getCount() == 0

            onProgress(SyncProgress(
                status = SyncStatus.ProbingLogin,
                accountLabel = account.label
            ))

            var pageNo = 1
            val allNewBills = mutableListOf<BillEntity>()
            var shouldContinue = true
            var pageCount = 1

            while (shouldContinue) {
                val result = epayAdapter.fetchBillPage(account.id, pageNo)

                if (result.isFailure) {
                    onProgress(SyncProgress(status = SyncStatus.Failed(result.exceptionOrNull()?.message ?: "未知错误")))
                    return SyncResult(allNewBills.size, false, result.exceptionOrNull()?.message ?: "未知错误")
                }

                val html = result.getOrNull() ?: ""

                if (html == "SESSION_EXPIRED") {
                    accountRepository.updateLoginStatus(account.id, "LOGGED_OUT")
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

                if (isFirstSync) {
                    allNewBills.addAll(pageBills)
                    shouldContinue = pageNo < pageCount
                } else {
                    for (bill in pageBills) {
                        if (accountDb.billDao().existsByTransactionNo(bill.transactionNo)) {
                            shouldContinue = false
                            break
                        }
                        allNewBills.add(bill)
                    }
                    if (shouldContinue) shouldContinue = pageNo < pageCount
                }

                // 发送每页进度 - 对齐 Rust 版 SyncStatus::Syncing { page, total }
                onProgress(SyncProgress(
                    status = SyncStatus.Syncing(
                        page = pageNo,
                        total = pageCount,
                        newCount = allNewBills.size
                    ),
                    accountLabel = account.label
                ))

                pageNo++
            }

            if (allNewBills.isNotEmpty()) {
                onProgress(SyncProgress(
                    status = SyncStatus.Persisting(totalNew = allNewBills.size),
                    accountLabel = account.label
                ))
                accountDb.billDao().insertAll(allNewBills)
                identityDb.billDao().insertAll(allNewBills)
            }

            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")

            onProgress(SyncProgress(
                status = SyncStatus.Completed(totalNew = allNewBills.size),
                accountLabel = account.label
            ))

            return SyncResult(newCount = allNewBills.size)
        } catch (e: Exception) {
            onProgress(SyncProgress(status = SyncStatus.Failed(e.message ?: "未知错误")))
            return SyncResult(0, false, e.message)
        }
    }
}
