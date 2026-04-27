package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccountsByIdentity(identityId: Long): Flow<List<Account>>
    suspend fun getAccountById(id: Long): Account?
    suspend fun addAccount(identityId: Long, label: String, userId: String, accountType: String): Long
    suspend fun deleteAccount(id: Long)
    suspend fun updateAccount(id: Long, label: String, userId: String)
    suspend fun updateLoginStatus(id: Long, status: String)
    suspend fun updateLastSyncTime(id: Long)
    fun getPassword(accountId: Long): String?
    fun savePassword(accountId: Long, password: String)
    fun removePassword(accountId: Long)
}
