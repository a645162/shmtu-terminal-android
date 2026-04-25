package cn.edu.shmtu.terminal.android.domain.usecase.auth

import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val epayAdapter: EpayAdapter
) {
    suspend fun testLoginStatus(accountId: Long): Boolean {
        return epayAdapter.testLoginStatus(accountId)
    }
}
