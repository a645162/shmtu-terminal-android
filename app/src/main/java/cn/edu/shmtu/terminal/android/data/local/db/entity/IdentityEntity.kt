package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
