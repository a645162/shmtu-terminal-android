package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import javax.inject.Inject

class SyncAccountBillsUseCase @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(account: Account): SyncResult {
        try {
            val accountDb = billDbManager.getAccountDatabase(account.id)
            val identityDb = billDbManager.getIdentityDatabase(account.identityId)
            val isFirstSync = accountDb.billDao().getCount() == 0

            var pageNo = 1
            val allNewBills = mutableListOf<BillEntity>()
            var shouldContinue = true

            while (shouldContinue) {
                val result = epayAdapter.fetchBillPage(account.id, pageNo)

                if (result.first == 302) {
                    accountRepository.updateLoginStatus(account.id, "LOGGED_OUT")
                    return SyncResult(0, false, "Session expired, need re-login")
                }

                if (result.first != 200) {
                    return SyncResult(allNewBills.size, false, "HTTP ${result.first}")
                }

                val html = result.second
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
                pageNo++
            }

            if (allNewBills.isNotEmpty()) {
                accountDb.billDao().insertAll(allNewBills)
                identityDb.billDao().insertAll(allNewBills)
            }

            accountRepository.updateLastSyncTime(account.id)

            return SyncResult(newCount = allNewBills.size)
        } catch (e: Exception) {
            return SyncResult(0, false, e.message)
        }
    }
}
