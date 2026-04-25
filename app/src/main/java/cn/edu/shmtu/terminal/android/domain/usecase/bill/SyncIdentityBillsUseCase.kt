package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SyncIdentityBillsUseCase @Inject constructor(
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(identityId: Long): SyncResult {
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
}
