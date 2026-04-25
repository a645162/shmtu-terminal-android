package cn.edu.shmtu.terminal.android.domain.usecase.account

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val billDbManager: BillDatabaseManager
) {
    suspend operator fun invoke(accountId: Long, identityId: Long) {
        val identityDb = billDbManager.getIdentityDatabase(identityId)
        identityDb.billDao().deleteByAccountId(accountId)

        billDbManager.deleteAccountDatabase(accountId)

        accountRepository.deleteAccount(accountId)

        accountRepository.removePassword(accountId)
    }
}
