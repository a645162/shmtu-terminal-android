package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val secureStorage: SecureStorage
) : AccountRepository {

    override fun getAccountsByIdentity(identityId: Long): Flow<List<Account>> {
        return accountDao.getAccountsByIdentity(identityId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getById(id)?.toDomain()
    }

    override suspend fun addAccount(identityId: Long, label: String, userId: String, accountType: String): Long {
        return accountDao.insert(
            cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity(
                identityId = identityId,
                label = label,
                userId = userId,
                accountType = accountType,
                loginStatus = "LOGGED_OUT"
            )
        )
    }

    override suspend fun deleteAccount(id: Long) {
        accountDao.deleteById(id)
    }

    override suspend fun updateAccount(id: Long, label: String, userId: String) {
        accountDao.updateAccount(id, label, userId)
    }

    override suspend fun updateLoginStatus(id: Long, status: String) {
        accountDao.updateLoginStatus(id, status)
    }

    override suspend fun updateLastSyncTime(id: Long) {
        accountDao.updateLastSyncTime(id, System.currentTimeMillis())
    }

    override fun getPassword(accountId: Long): String? {
        return secureStorage.getPassword(accountId)
    }

    override fun savePassword(accountId: Long, password: String) {
        secureStorage.savePassword(accountId, password)
    }

    override fun removePassword(accountId: Long) {
        secureStorage.removePassword(accountId)
    }
}

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }
