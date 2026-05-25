package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.SessionRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
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
        try {
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

            while (shouldContinue) {
                val result = epayAdapter.fetchBillPage(account.id, pageNo)

                if (result.isFailure) {
                    return SyncResult(allBills.size, false, result.exceptionOrNull()?.message ?: "未知错误")
                }

                val html = result.getOrNull() ?: ""

                if (html == "SESSION_EXPIRED") {
                    return SyncResult(0, false, "Session expired, need re-login")
                }

                val pageCount = epayAdapter.getPageCount(html)
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
                pageNo++
            }

            if (allBills.isNotEmpty()) {
                accountDb.billDao().insertAll(allBills)
                identityDb.billDao().insertAll(allBills)
            }

            accountRepository.updateLastSyncTime(account.id)
            accountRepository.updateLoginStatus(account.id, "LOGGED_IN")

            return SyncResult(newCount = allBills.size)
        } catch (e: Exception) {
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
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        var totalNew = 0
        var hasError = false
        var errorMsg: String? = null

        for (account in accountList) {
            val result = fullSyncAccountBillsUseCase(account)
            totalNew += result.newCount
            if (!result.success) {
                hasError = true
                errorMsg = result.errorMessage
            }
        }

        return SyncResult(totalNew, !hasError, errorMsg)
    }
}
