package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.cas.parser.PersonAccountInfo
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountsByIdentity(identityId: Long): Flow<List<Account>>
    suspend fun getAccountById(id: Long): Account?
    suspend fun getAllAccounts(): List<Account>
    suspend fun addAccount(identityId: Long, label: String, userId: String, accountType: String): Long
    suspend fun deleteAccount(id: Long)
    suspend fun updateAccount(id: Long, label: String, userId: String)
    suspend fun updateLoginStatus(id: Long, status: String)
    suspend fun updateLastSyncTime(id: Long)
    fun getPassword(accountId: Long): String?
    fun savePassword(accountId: Long, password: String)
    fun removePassword(accountId: Long)

    /** 一卡通个人账户详情 - 缓存读取 (按 accountId) */
    suspend fun getCachedPersonAccount(accountId: Long): PersonAccount?
    fun observeCachedPersonAccount(accountId: Long): Flow<PersonAccount?>

    /** 批量观察 - 一次拿某个 identity 下所有账号的缓存 */
    fun observeCachedPersonAccounts(accountIds: List<Long>): Flow<List<PersonAccount>>

    /** 保存一卡通个人账户详情到本地 Room 缓存 */
    suspend fun savePersonAccount(accountId: Long, info: PersonAccountInfo)

    /** 删除账号时清理缓存 */
    suspend fun deleteCachedPersonAccount(accountId: Long)
}
