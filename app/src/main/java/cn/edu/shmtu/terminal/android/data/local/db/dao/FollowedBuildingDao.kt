package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.FollowedBuildingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedBuildingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(building: FollowedBuildingEntity)

    @Query("DELETE FROM followed_buildings WHERE buildingNumber = :buildingNumber")
    suspend fun delete(buildingNumber: Int)

    @Query("SELECT * FROM followed_buildings ORDER BY displayOrder ASC")
    fun getAll(): Flow<List<FollowedBuildingEntity>>

    @Query("SELECT COUNT(*) > 0 FROM followed_buildings WHERE buildingNumber = :buildingNumber")
    suspend fun isFollowed(buildingNumber: Int): Boolean
}
