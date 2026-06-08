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
    val building: String? = null,

    // ============== 账单合并字段 ==============
    // 原始订单号列表：合并后存所有原始 transactionNo
    val mergedTransactionNos: List<String> = emptyList(),
    // 原始时间列表：合并后存所有原始 dateTimeStrFormat
    val mergedDateTimes: List<String> = emptyList(),
    // 是否是合并账单
    val isMerged: Boolean = false,
    // 包含的原始账单数
    val mergedBillCount: Int = 1
)

fun BillItem.resolvedPlace(): String? {
    val resolvedBuilding = building?.takeIf { it.isNotBlank() }
        ?: position?.takeIf { it.isNotBlank() }
    val resolvedRoom = room?.takeIf { it.isNotBlank() }
    return listOfNotNull(resolvedBuilding, resolvedRoom)
        .joinToString("/")
        .ifBlank { null }
}

fun BillItem.displayTitle(preferParsed: Boolean = true): String {
    return if (preferParsed) {
        resolvedPlace() ?: type.ifBlank { "未分类交易" }
    } else {
        type.ifBlank { resolvedPlace() ?: "未分类交易" }
    }
}

fun BillItem.displaySubtitle(): String = targetUser.ifBlank { "未知商户/位置" }

/**
 * 合并后账单的时间范围（首尾合并语义：start=min, end=max）
 */
fun BillItem.effectiveTimeRange(): Pair<String, String> {
    if (mergedDateTimes.isEmpty()) return dateTimeStrFormat to dateTimeStrFormat
    val sorted = mergedDateTimes.sorted()
    return sorted.first() to sorted.last()
}

/**
 * 合并后账单的所有原始订单号
 */
fun BillItem.effectiveTransactionNos(): List<String> =
    if (mergedTransactionNos.isEmpty()) listOf(transactionNo) else mergedTransactionNos
