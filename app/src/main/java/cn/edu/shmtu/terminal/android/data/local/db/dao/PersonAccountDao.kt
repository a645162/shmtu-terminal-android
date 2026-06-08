package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.PersonAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonAccountEntity): Long

    @Query("SELECT * FROM person_accounts WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: Long): PersonAccountEntity?

    @Query("SELECT * FROM person_accounts WHERE accountId = :accountId")
    fun getByAccountIdFlow(accountId: Long): Flow<PersonAccountEntity?>

    @Query("SELECT * FROM person_accounts WHERE accountId IN (:accountIds)")
    fun getByAccountIdsFlow(accountIds: List<Long>): Flow<List<PersonAccountEntity>>

    @Query("DELETE FROM person_accounts WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Long)
}
