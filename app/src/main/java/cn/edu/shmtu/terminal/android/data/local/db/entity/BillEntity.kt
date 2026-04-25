package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    indices = [
        Index("accountId"),
        Index("dateTimeStrFormat"),
        Index(value = ["accountId", "transactionNo"], unique = true)
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val accountLabel: String,
    val dateStr: String,
    val timeStr: String,
    val dateTimeStrFormat: String,
    val type: String,
    val transactionNo: String,
    val targetUser: String,
    val money: String,
    val method: String,
    val status: String
)
