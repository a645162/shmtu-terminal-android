package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepositoryImpl @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val accountRepository: AccountRepository,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase
) : BillRepository {

    override fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>> {
        return billDbManager.getIdentityDatabase(identityId)
            .billDao().getAllBills()
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>> {
        return billDbManager.getIdentityDatabase(identityId)
            .billDao().getBillsByAccount(accountId)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun syncAccountBills(accountId: Long): SyncResult {
        val account = accountRepository.getAccountById(accountId) ?: return SyncResult(0, false, "Account not found")
        return syncAccountBillsUseCase(account)
    }

    override suspend fun syncIdentityBills(identityId: Long): SyncResult {
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        var totalNew = 0
        var hasError = false
        var errorMsg: String? = null

        for (account in accountList) {
            val result = syncAccountBillsUseCase(account)
            totalNew += result.newCount
            if (!result.success) {
                hasError = true
                errorMsg = result.errorMessage
            }
        }

        return SyncResult(totalNew, !hasError, errorMsg)
    }

    override suspend fun deleteBillsForAccount(accountId: Long, identityId: Long) {
        val identityDb = billDbManager.getIdentityDatabase(identityId)
        identityDb.billDao().deleteByAccountId(accountId)
    }
}

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }
