package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.cas.parser.PersonAccountInfo
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.PersonAccountDao
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val secureStorage: SecureStorage,
    private val personAccountDao: PersonAccountDao,
) : AccountRepository {

    override fun getAccountsByIdentity(identityId: Long): Flow<List<Account>> {
        return accountDao.getAccountsByIdentity(identityId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getById(id)?.toDomain()
    }

    override suspend fun getAllAccounts(): List<Account> {
        return accountDao.getAllAccounts().map { it.toDomain() }
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

    override suspend fun getCachedPersonAccount(accountId: Long): PersonAccount? {
        return personAccountDao.getByAccountId(accountId)?.toDomain()
    }

    override fun observeCachedPersonAccount(accountId: Long): Flow<PersonAccount?> {
        return personAccountDao.getByAccountIdFlow(accountId).map { it?.toDomain() }
    }

    override fun observeCachedPersonAccounts(accountIds: List<Long>): Flow<List<PersonAccount>> {
        if (accountIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return personAccountDao.getByAccountIdsFlow(accountIds).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun savePersonAccount(accountId: Long, info: PersonAccountInfo) {
        val entity = EntityMappers.run { info.toEntity(accountId) }
        personAccountDao.upsert(entity)
    }

    override suspend fun deleteCachedPersonAccount(accountId: Long) {
        personAccountDao.deleteByAccountId(accountId)
    }
}

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.PersonAccountEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }
