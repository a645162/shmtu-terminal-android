package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabase
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.ConsumptionBucket
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.MealDistribution
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.FullSyncAccountBillsUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.FullSyncIdentityBillsUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** 收入关键词 - 对齐 Rust 版 INCOME_KEYWORDS */
private val INCOME_KEYWORDS = listOf("充值", "冲正", "退款", "返还", "补偿")

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class BillRepositoryImpl @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val accountRepository: AccountRepository,
    private val identityRepository: IdentityRepository,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase,
    private val fullSyncAccountBillsUseCase: FullSyncAccountBillsUseCase,
    private val fullSyncIdentityBillsUseCase: FullSyncIdentityBillsUseCase
) : BillRepository {

    private val _syncProgress = MutableSharedFlow<SyncProgress>(extraBufferCapacity = 1)
    override val syncProgress: SharedFlow<SyncProgress> = _syncProgress

    override fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>> {
        return billDbManager.getIdentityDatabase(identityId)
            .billDao().getAllBills()
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>> {
        return flow {
            val account = accountRepository.getAccountById(accountId)
            if (account != null) {
                billDbManager.getAccountDatabase(account.userId)
                    .billDao().getBillsByAccount(accountId)
                    .collect { list ->
                        emit(list.map { it.toDomain() })
                    }
            } else {
                emit(emptyList())
            }
        }
    }

    override suspend fun syncAccountBills(accountId: Long): SyncResult {
        val account = accountRepository.getAccountById(accountId) ?: return SyncResult(0, false, "Account not found")
        return syncAccountBillsUseCase(account)
    }

    override suspend fun syncIdentityBills(identityId: Long): SyncResult {
        return syncIdentityBillsUseCase(identityId)
    }

    override suspend fun deleteBillsForAccount(accountId: Long, identityId: Long) {
        billDbManager.getIdentityDatabase(identityId)
            .billDao().deleteByAccountId(accountId)
    }

    // ==================== 带进度的同步 ====================

    override suspend fun syncAccountBillsWithProgress(accountId: Long): SyncResult {
        val account = accountRepository.getAccountById(accountId) ?: return SyncResult(0, false, "Account not found")
        return syncAccountBillsUseCase(account) { progress ->
            _syncProgress.tryEmit(progress)
        }
    }

    override suspend fun syncIdentityBillsWithProgress(identityId: Long): SyncResult {
        return syncIdentityBillsUseCase(identityId) { progress ->
            _syncProgress.tryEmit(progress)
        }
    }

    override suspend fun fullSyncAccountWithProgress(accountId: Long): SyncResult {
        val account = accountRepository.getAccountById(accountId) ?: return SyncResult(0, false, "Account not found")
        return fullSyncAccountBillsUseCase(account) { progress ->
            _syncProgress.tryEmit(progress)
        }
    }

    override suspend fun fullSyncIdentityWithProgress(identityId: Long): SyncResult {
        return fullSyncIdentityBillsUseCase(identityId) { progress ->
            _syncProgress.tryEmit(progress)
        }
    }

    // ==================== 统计功能 ====================

    override fun getBillOverview(identityId: Long?): Flow<BillOverview> {
        val now = YearMonth.now()
        val thisMonthStart = now.atDay(1).format(DATE_FMT)
        val thisMonthEnd = now.atEndOfMonth().format(DATE_FMT_END)
        val lastMonth = now.minusMonths(1)
        val lastMonthStart = lastMonth.atDay(1).format(DATE_FMT)
        val lastMonthEnd = lastMonth.atEndOfMonth().format(DATE_FMT_END)

        val databases = getDatabases(identityId)

        return combine(
            databases.flatMapLatest { dbs ->
                combine(dbs.map { db ->
                    db.billDao().getSumByTypeInRange(thisMonthStart, thisMonthEnd)
                }) { results ->
                    val sums = results.flatMap { it.toList() }
                    val spending = sums.filter { it.type.contains("消费") }.sumOf { it.total }
                    val income = sums.filter { it.type.contains("充值") }.sumOf { it.total }
                    spending to income
                }
            },
            databases.flatMapLatest { dbs ->
                combine(dbs.map { db ->
                    db.billDao().getSumByTypeInRange(lastMonthStart, lastMonthEnd)
                }) { results ->
                    val sums = results.flatMap { it.toList() }
                    val spending = sums.filter { it.type.contains("消费") }.sumOf { it.total }
                    val income = sums.filter { it.type.contains("充值") }.sumOf { it.total }
                    spending to income
                }
            },
            databases.flatMapLatest { dbs ->
                combine(dbs.map { db ->
                    db.billDao().getAllBills()
                }) { results ->
                    results.sumOf { it.size }
                }
            },
            databases.flatMapLatest { dbs ->
                combine(dbs.map { db ->
                    db.billDao().getActiveDaysInRange(thisMonthStart, thisMonthEnd)
                }) { results ->
                    results.flatMap { it.toList() }.toSet().size
                }
            }
        ) { (thisSpending, thisIncome), (lastSpending, lastIncome), count, activeDays ->
            val dailyAverage = if (activeDays > 0) thisSpending / activeDays else 0.0
            BillOverview(
                totalSpending = thisSpending,
                totalIncome = thisIncome,
                netChange = thisIncome - thisSpending,
                dailyAverage = dailyAverage,
                transactionCount = count,
                activeDays = activeDays,
                lastMonthSpending = lastSpending,
                lastMonthIncome = lastIncome
            )
        }
    }

    override fun getSpendingTrend(identityId: Long?, startDate: String, endDate: String): Flow<List<SpendingTrend>> {
        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getDailyTotalsInRange(startDate, endDate)
            }) { results ->
                val merged = mutableMapOf<String, Double>()
                for (list in results) {
                    for (item in list) {
                        merged[item.dateStr] = (merged[item.dateStr] ?: 0.0) + item.total
                    }
                }
                merged.entries.sortedBy { it.key }.map { SpendingTrend(it.key, it.value) }
            }
        }
    }

    override fun getCategoryBreakdown(identityId: Long?, startDate: String, endDate: String): Flow<List<CategoryBreakdown>> {
        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getSumByTypeInRange(startDate, endDate)
            }) { results ->
                val merged = mutableMapOf<String, Double>()
                for (list in results) {
                    for (item in list) {
                        merged[item.type] = (merged[item.type] ?: 0.0) + item.total
                    }
                }
                val total = merged.values.sum()
                merged.entries.map { (type, amount) ->
                    CategoryBreakdown(type, amount, if (total > 0) (amount / total).toFloat() else 0f)
                }.sortedByDescending { it.amount }
            }
        }
    }

    override fun getTargetUserRanking(identityId: Long?, startDate: String, endDate: String, limit: Int): Flow<List<TargetUserRanking>> {
        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getTopTargetUsers(startDate, endDate, limit)
            }) { results ->
                val merged = mutableMapOf<String, Double>()
                for (list in results) {
                    for (item in list) {
                        merged[item.targetUser] = (merged[item.targetUser] ?: 0.0) + item.total
                    }
                }
                merged.entries.sortedByDescending { it.value }.take(limit).map {
                    TargetUserRanking(it.key, it.value)
                }
            }
        }
    }

    override fun getMonthlySummary(identityId: Long?): Flow<List<MonthlySummary>> {
        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getMonthlySummary()
            }) { results ->
                val merged = mutableMapOf<String, MutableMap<String, Double>>()
                for (list in results) {
                    for (item in list) {
                        val typeMap = merged.getOrPut(item.month) { mutableMapOf() }
                        typeMap[item.type] = (typeMap[item.type] ?: 0.0) + item.total
                    }
                }
                merged.entries.map { (month, typeMap) ->
                    MonthlySummary(
                        month = month,
                        spending = typeMap.filter { it.key.contains("消费") }.values.sum(),
                        income = typeMap.filter { it.key.contains("充值") }.values.sum()
                    )
                }.sortedByDescending { it.month }
            }
        }
    }

    // ==================== 新增统计功能 - 对齐 Rust 版 ====================

    /**
     * 统计汇总 - 对齐 Rust 版 get_statistics_summary
     */
    override fun getStatisticsSummary(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<StatisticsSummary> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(
                combine(dbs.map { db ->
                    db.billDao().getSumByTypeInRange(start, end)
                }) { results ->
                    val sums = results.flatMap { it.toList() }
                    val expenseSum = sums.filter { it.type.contains("消费") }.sumOf { it.total }
                    val incomeSum = sums.filter { it.type.contains("充值") }.sumOf { it.total }
                    expenseSum to incomeSum
                },
                combine(dbs.map { db ->
                    db.billDao().getAllBills()
                }) { results ->
                    val allBills = results.flatMap { it.toList() }
                    val expenseCount = allBills.count {
                        it.status == "交易成功" && it.type.contains("消费")
                    }
                    val incomeCount = allBills.count {
                        it.status == "交易成功" && it.type.contains("充值")
                    }
                    expenseCount to incomeCount
                }
            ) { (expenseSum, incomeSum), (expenseCount, incomeCount) ->
                val activeDays = 30 // simplified
                StatisticsSummary(
                    totalExpense = expenseSum,
                    totalIncome = incomeSum,
                    netExpense = expenseSum - incomeSum,
                    dailyAverage = if (activeDays > 0) expenseSum / activeDays else 0.0,
                    expenseCount = expenseCount,
                    incomeCount = incomeCount
                )
            }
        }
    }

    /**
     * 每日趋势 - 对齐 Rust 版 get_daily_trend
     * 返回每日支出和收入
     */
    override fun getDailyTrend(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<DailyTrend>> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getDailyTrendByType(start, end)
            }) { results ->
                val merged = mutableMapOf<String, MutableMap<String, Double>>()
                for (list in results) {
                    for (item in list) {
                        val typeMap = merged.getOrPut(item.dateStr) { mutableMapOf() }
                        typeMap[item.type] = (typeMap[item.type] ?: 0.0) + item.total
                    }
                }
                merged.entries.sortedBy { it.key }.map { (date, typeMap) ->
                    DailyTrend(
                        date = date,
                        expense = typeMap.filter { it.key.contains("消费") }.values.sum(),
                        income = typeMap.filter { it.key.contains("充值") }.values.sum()
                    )
                }
            }
        }
    }

    /**
     * 用餐时段分布 - 对齐 Rust 版 get_meal_distribution
     * 早餐(6-9), 午餐(11-13), 晚餐(17-19), 夜宵(21-23), 其他
     */
    override fun getMealDistribution(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<MealDistribution>> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getMealDistribution(start, end)
            }) { results ->
                val merged = mutableMapOf<String, MutableMap<String, Double>>()
                for (list in results) {
                    for (item in list) {
                        val typeMap = merged.getOrPut(item.meal) { mutableMapOf() }
                        typeMap["count"] = (typeMap["count"] ?: 0.0) + item.count
                        typeMap["amount"] = (typeMap["amount"] ?: 0.0) + item.amount
                    }
                }
                // 固定顺序：早餐, 午餐, 晚餐, 夜宵, 其他
                val order = listOf("早餐", "午餐", "晚餐", "夜宵", "其他")
                order.mapNotNull { meal ->
                    val data = merged[meal] ?: return@mapNotNull null
                    MealDistribution(meal, data["count"]?.toInt() ?: 0, data["amount"] ?: 0.0)
                }
            }
        }
    }

    /**
     * 消费金额分布 - 对齐 Rust 版 get_consumption_distribution
     * 5 个固定区间: <10元, 10-20元, 20-50元, 50-100元, >100元
     */
    override fun getConsumptionDistribution(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<ConsumptionBucket>> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db ->
                db.billDao().getConsumptionDistribution(start, end)
            }) { results ->
                // 合并各数据库的结果
                val buckets = listOf(
                    ConsumptionBucket("<10元", 0, 0.0),
                    ConsumptionBucket("10-20元", 0, 0.0),
                    ConsumptionBucket("20-50元", 0, 0.0),
                    ConsumptionBucket("50-100元", 0, 0.0),
                    ConsumptionBucket(">100元", 0, 0.0)
                ).toMutableList()

                for (list in results) {
                    for (item in list) {
                        val idx = when {
                            item.bucket == "<10" -> 0
                            item.bucket == "10-20" -> 1
                            item.bucket == "20-50" -> 2
                            item.bucket == "50-100" -> 3
                            item.bucket == ">100" -> 4
                            else -> -1
                        }
                        if (idx >= 0) {
                            buckets[idx] = ConsumptionBucket(
                                buckets[idx].range,
                                buckets[idx].count + item.count,
                                buckets[idx].amount + item.amount
                            )
                        }
                    }
                }
                buckets
            }
        }
    }

    private fun getDatabases(identityId: Long?): Flow<List<BillDatabase>> {
        return if (identityId != null) {
            flow { emit(listOf(billDbManager.getIdentityDatabase(identityId))) }
        } else {
            identityRepository.getAllIdentities().map { identities ->
                identities.map { billDbManager.getIdentityDatabase(it.id) }
            }
        }
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_FMT_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }
