package cn.edu.shmtu.terminal.android.domain.model

data class BillOverview(
    val totalSpending: Double,
    val totalIncome: Double,
    val netChange: Double,
    val transactionCount: Int,
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
