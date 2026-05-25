package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cn.edu.shmtu.terminal.android.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Query("SELECT * FROM session_info WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: String): SessionEntity?
    
    @Query("SELECT * FROM session_info WHERE accountId = :accountId LIMIT 1")
    fun observeByAccountId(accountId: String): Flow<SessionEntity?>
    
    @Query("SELECT * FROM session_info WHERE isValid = 1")
    suspend fun getAllValid(): List<SessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long
    
    @Update
    suspend fun update(session: SessionEntity)
    
    @Query("UPDATE session_info SET isValid = 0 WHERE accountId = :accountId")
    suspend fun invalidate(accountId: String)
    
    @Query("DELETE FROM session_info WHERE accountId = :accountId")
    suspend fun delete(accountId: String)
    
    @Query("DELETE FROM session_info WHERE isValid = 0")
    suspend fun deleteInvalid()
    
    @Query("UPDATE session_info SET cookies = :cookies, loginTime = :loginTime WHERE accountId = :accountId")
    suspend fun updateCookies(accountId: String, cookies: String, loginTime: String?)
}
