package cn.edu.shmtu.terminal.android.domain.usecase.auth

import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import javax.inject.Inject

class CheckLoginStatusUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(accountId: Long): Result<Boolean> {
        val result = epayAdapter.testLoginStatus(accountId)
        
        if (result.isSuccess) {
            val isLoggedIn = result.getOrNull() == true
            val status = if (isLoggedIn) "LOGGED_IN" else "LOGGED_OUT"
            accountRepository.updateLoginStatus(accountId, status)
        }
        
        return result
    }
}
