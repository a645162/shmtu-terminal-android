package cn.edu.shmtu.terminal.android.domain.usecase.auth

import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import javax.inject.Inject

class CheckLoginStatusUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(accountId: Long): Boolean {
        val isLoggedIn = epayAdapter.testLoginStatus(accountId)
        val status = if (isLoggedIn) "LOGGED_IN" else "LOGGED_OUT"
        accountRepository.updateLoginStatus(accountId, status)
        return isLoggedIn
    }
}
