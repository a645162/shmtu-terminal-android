package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "followed_buildings")
data class FollowedBuildingEntity(
    @PrimaryKey val buildingNumber: Int,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
