package cn.edu.shmtu.terminal.android.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * 列表字段的 Room TypeConverter。
 * Room 不直接支持 List<String>，用 ASCII Unit Separator (0x1F) 拼接，
 * 内部出现的 0x1F 替换为 0x1E（Record Separator）作为转义。
 */
class BillListConverters {
    @TypeConverter
    fun fromList(list: List<String>?): String? =
        list?.joinToString("") { it.replace("", "") }

    @TypeConverter
    fun toList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }
            ?.split("")
            ?.map { it.replace("", "") }
}

@Entity(
    tableName = "bills",
    indices = [
        Index("accountId"),
        Index("dateTimeStrFormat"),
        Index(value = ["accountId", "transactionNo"], unique = true)
    ]
)
@TypeConverters(BillListConverters::class)
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
    val building: String? = null,

    // ============== 账单合并字段 ==============
    // 原始订单号列表：合并后存所有原始 transactionNo；未合并时存 [transactionNo]
    val mergedTransactionNos: List<String> = emptyList(),
    // 原始时间列表：合并后存所有原始 dateTimeStrFormat；未合并时存 [dateTimeStrFormat]
    val mergedDateTimes: List<String> = emptyList(),
    // 是否是合并账单
    val isMerged: Boolean = false,
    // 包含的原始账单数
    val mergedBillCount: Int = 1
)
