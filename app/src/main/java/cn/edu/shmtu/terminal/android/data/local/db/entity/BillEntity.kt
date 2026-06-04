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
    val status: String,
    val position: String? = null,
    val room: String? = null,
    val notes: String? = null,
    // 对齐 Tauri BillClassifier.classify.type_label,落库时由 RoomBillStore 即时计算写入,
    // 供 SQL 维度按 category 直接 group by(避免每次统计都重新跑 classifier)。
    val category: String? = null,
    // 对齐 Tauri ClassifiedStatisticsItem: 用于按位置 group by。
    val building: String? = null
)
