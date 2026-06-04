package cn.edu.shmtu.terminal.android.domain.model

data class BillItem(
    val id: Long,
    val accountId: Long,
    val accountLabel: String,
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
    // 对齐 Tauri BillClassifier.classify.type_label / BillCategory 内部 key(如 "canteen"/"bath"),
    // 在 RoomBillStore.merge 落库时计算并写入, 供 SQL 维度按 category 直接 group by。
    val category: String? = null,
    // 对齐 Tauri ClassifiedStatisticsItem.building 字段。
    val building: String? = null
)
