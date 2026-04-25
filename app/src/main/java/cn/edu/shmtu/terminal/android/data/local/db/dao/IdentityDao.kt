package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(identity: IdentityEntity): Long

    @Query("SELECT * FROM identities ORDER BY displayOrder ASC")
    fun getAllIdentities(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getById(id: Long): IdentityEntity?

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(identity: IdentityEntity)
}
