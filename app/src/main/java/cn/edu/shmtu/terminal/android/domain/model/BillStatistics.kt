package cn.edu.shmtu.terminal.android.domain.model

// ==================== 对齐 Rust 版 statistics data structures ====================

/**
 * 统计汇总 - 对齐 Rust 版 StatisticsSummary
 */
data class StatisticsSummary(
    val totalExpense: Double,
    val totalIncome: Double,
    val netExpense: Double,
    val dailyAverage: Double,
    val expenseCount: Int,
    val incomeCount: Int
)

/**
 * 每日趋势 - 对齐 Rust 版 DailyTrendItem
 */
data class DailyTrend(
    val date: String,
    val expense: Double,
    val income: Double
)

/**
 * 分类项 - 对齐 Rust 版 CategoryItem
 */
data class CategoryItem(
    val name: String,
    val value: Double,
    val count: Int,
    val color: String
)

/**
 * 用餐时段分布 - 对齐 Rust 版 MealDistItem
 */
data class MealDistribution(
    val name: String,
    val count: Int,
    val amount: Double
)

/**
 * 消费金额分布 - 对齐 Rust 版 ConsumptionBucketItem
 */
data class ConsumptionBucket(
    val range: String,
    val count: Int,
    val amount: Double
)

/**
 * 商户消费排行 - 对齐 Rust 版 MerchantRankingItem
 */
data class MerchantRanking(
    val merchant: String,
    val count: Int,
    val amount: Double
)

// ==================== 保留原有模型 ====================

data class BillOverview(
    val totalSpending: Double,
    val totalIncome: Double,
    val netChange: Double,
    val dailyAverage: Double,
    val transactionCount: Int,
    val activeDays: Int,
    val lastMonthSpending: Double,
    val lastMonthIncome: Double
)

data class SpendingTrend(
    val date: String,
    val amount: Double
)

data class CategoryBreakdown(
    val type: String,
    val amount: Double,
    val percentage: Float
)

data class TargetUserRanking(
    val targetUser: String,
    val amount: Double
)

data class MonthlySummary(
    val month: String,
    val spending: Double,
    val income: Double
)

// ==================== 导入导出模型 - 对齐 Rust 版 ====================

/** 导出格式 */
enum class ExportFormat {
    CSV,
    JSON,
    QIANJI
}

/** 导出参数 - 对齐 Rust 版 ExportParamsFrontend */
data class ExportParams(
    val identityId: Long,
    val format: ExportFormat,
    val sourceType: String = "identity",  // "identity" or "account"
    val filePath: String,
    val dateStart: String? = null,
    val dateEnd: String? = null
)

/** 导入导出账单项 - 对齐 Rust 版 JsonBillItem */
data class JsonBillItem(
    val dateTimeFormatted: String? = null,
    val timeStrFormatted: String? = null,
    val itemType: String? = null,
    val number: String? = null,
    val numberList: List<String>? = null,
    val targetUser: String? = null,
    val moneyStr: String? = null,
    val money: Double? = null,
    val method: String? = null,
    val statusStr: String? = null,
    val isCombined: Boolean = false,
    val classification: String? = null
)

/** JSON 导出结构 - 对齐 Rust 版 JsonExport */
data class JsonExport(
    val exportTime: String,
    val identityName: String,
    val source: String,
    val bills: List<JsonBillItem>
)

/** JSON 导入结构 - 对齐 Rust 版 JsonImport */
data class JsonImport(
    val exportTime: String,
    val identityName: String,
    val source: String,
    val bills: List<JsonBillItem>
)

/** 钱迹导出项 - 对齐 Rust 版 QianjiItem */
data class QianjiItem(
    val type: Int,       // 0=支出, 1=收入
    val money: Double,
    val category: String,
    val account: String,
    val remark: String,
    val time: Long       // unix timestamp
)

/** 快照信息 - 对齐 Rust 版 SnapshotInfoFrontend */
data class SnapshotInfo(
    val filename: String,
    val createdAt: String,
    val sizeBytes: Long
)
