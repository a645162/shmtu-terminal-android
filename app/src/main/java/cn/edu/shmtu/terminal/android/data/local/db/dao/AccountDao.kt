package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE identityId = :identityId ORDER BY displayOrder ASC")
    fun getAccountsByIdentity(identityId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts WHERE identityId = :identityId")
    suspend fun deleteByIdentityId(identityId: Long)

    @Query("UPDATE accounts SET loginStatus = :status WHERE id = :id")
    suspend fun updateLoginStatus(id: Long, status: String)

    @Query("UPDATE accounts SET lastSyncTime = :time WHERE id = :id")
    suspend fun updateLastSyncTime(id: Long, time: Long)

    @Query("UPDATE accounts SET label = :label, userId = :userId WHERE id = :id")
    suspend fun updateAccount(id: Long, label: String, userId: String)

    @Query("SELECT identityId, COUNT(*) as count FROM accounts GROUP BY identityId")
    fun getAccountCounts(): Flow<List<AccountCount>>

    @Delete
    suspend fun delete(account: AccountEntity)
}

data class AccountCount(
    val identityId: Long,
    val count: Int
)
