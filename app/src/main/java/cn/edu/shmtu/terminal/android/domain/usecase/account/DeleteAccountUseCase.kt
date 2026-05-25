package cn.edu.shmtu.terminal.android.domain.usecase.account

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val billDbManager: BillDatabaseManager
) {
    suspend operator fun invoke(accountId: Long, identityId: Long) {
        // 获取账号信息
        val account = accountRepository.getAccountById(accountId)
        
        if (account != null) {
            // 删除身份数据库中该账号的账单 (identity_{identityId}.sqlite)
            billDbManager.getIdentityDatabase(identityId).billDao().deleteByAccountId(accountId)
            
            // 删除账号数据库 (account_{studentId}.sqlite)
            billDbManager.deleteAccountDatabase(account.userId)
        }

        // 删除账号记录
        accountRepository.deleteAccount(accountId)
        
        // 删除密码
        accountRepository.removePassword(accountId)
    }
}
