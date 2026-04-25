package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(
        entity = IdentityEntity::class,
        parentColumns = ["id"],
        childColumns = ["identityId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("identityId")]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identityId: Long,
    val label: String,
    val userId: String,
    val accountType: String,
    val loginStatus: String,
    val lastSyncTime: Long? = null,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
