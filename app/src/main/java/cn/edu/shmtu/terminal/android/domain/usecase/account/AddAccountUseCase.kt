package cn.edu.shmtu.terminal.android.domain.usecase.account

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import javax.inject.Inject

class AddAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(identityId: Long, label: String, userId: String, password: String): Long {
        val accountId = accountRepository.addAccount(
            identityId = identityId,
            label = label,
            userId = userId,
            accountType = "EPAY"
        )
        if (accountId > 0) {
            accountRepository.savePassword(accountId, password)
        }
        return accountId
    }
}
