package cn.edu.shmtu.terminal.android.data.repository

import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabase
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.ConsumptionBucket
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.ForgotCardItem
import cn.edu.shmtu.terminal.android.domain.model.ForgotCardStats
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
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
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
    private val fullSyncIdentityBillsUseCase: FullSyncIdentityBillsUseCase,
    /** GitHub 同步的规则文件管理器 — classifier/mealClassifier 走它读取本地缓存 */
    private val billRulesManager: cn.edu.shmtu.terminal.android.data.sync.BillRulesManager,
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
        return mergedBills(identityId).map { allBills ->
            val successfulBills = allBills.filterSuccessful()
            Log.d("BillRepo", "getBillOverview totalBills=${allBills.size} successful=${successfulBills.size}")
            val thisMonthBills = successfulBills.filterByRange(thisMonthStart, thisMonthEnd)
            val lastMonthBills = successfulBills.filterByRange(lastMonthStart, lastMonthEnd)
            val thisSpending = thisMonthBills.filterNot(::isIncome).sumOf { kotlin.math.abs(it.moneyValue()) }
            val thisIncome = thisMonthBills.filter(::isIncome).sumOf { kotlin.math.abs(it.moneyValue()) }
            val lastSpending = lastMonthBills.filterNot(::isIncome).sumOf { kotlin.math.abs(it.moneyValue()) }
            val lastIncome = lastMonthBills.filter(::isIncome).sumOf { kotlin.math.abs(it.moneyValue()) }
            val activeDays = thisMonthBills.map { it.dateStr }.distinct().size
            val dailyAverage = if (activeDays > 0) thisSpending / activeDays else 0.0
            Log.d("BillRepo", "getBillOverview thisSpending=$thisSpending thisIncome=$thisIncome lastSpending=$lastSpending activeDays=$activeDays")
            BillOverview(
                totalSpending = thisSpending,
                totalIncome = thisIncome,
                netChange = thisIncome - thisSpending,
                dailyAverage = dailyAverage,
                transactionCount = thisMonthBills.size,
                activeDays = activeDays,
                lastMonthSpending = lastSpending,
                lastMonthIncome = lastIncome
            )
        }
    }

    override fun getSpendingTrend(identityId: Long?, startDate: String, endDate: String): Flow<List<SpendingTrend>> {
        return mergedBills(identityId).map { bills ->
            bills.filterSuccessful()
                .filterByRange(startDate, endDate)
                .groupBy { it.dateStr }
                .toSortedMap()
                .map { (date, items) ->
                    SpendingTrend(date, items.sumOf { it.moneyValue() })
                }
        }
    }

    override fun getCategoryBreakdown(identityId: Long?, startDate: String, endDate: String): Flow<List<CategoryBreakdown>> {
        return mergedBills(identityId).map { bills ->
            val merged = bills.filterSuccessful()
                .filterByRange(startDate, endDate)
                .filterNot(::isIncome)
                // 对齐 Tauri `get_category_distribution`:
                // 实时按 billClassifier.classifyKey(itemType, targetUser) 拿到 category 内部 key,
                // 再用 BillEntity.category 字段(merge 落库时已写入)做 group by。
                // 这里优先用 BillEntity.category; 若落库时未注入 classifier(老数据),则
                // 用 billDbManager 注入的 EpayAdapter.classifier 即时跑一次。
                .groupBy { it.category ?: billClassifier?.classifyKey(it.type, it.targetUser) ?: "other" }
                .mapValues { (_, items) -> items.sumOf { kotlin.math.abs(it.moneyValue()) } }
            val total = merged.values.sum()
            merged.entries.map { (type, amount) ->
                CategoryBreakdown(type, amount, if (total > 0) (amount / total).toFloat() else 0f)
            }.sortedByDescending { it.amount }
        }
    }

    override fun getTargetUserRanking(identityId: Long?, startDate: String, endDate: String, limit: Int): Flow<List<TargetUserRanking>> {
        return mergedBills(identityId).map { bills ->
            bills.filterSuccessful()
                .filterByRange(startDate, endDate)
                .filterNot(::isIncome)
                .groupBy { it.targetUser }
                .mapValues { (_, items) -> items.sumOf { it.moneyValue() } }
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { TargetUserRanking(it.key, it.value) }
        }
    }

    override fun getMonthlySummary(identityId: Long?): Flow<List<MonthlySummary>> {
        return mergedBills(identityId).map { bills ->
            bills.filterSuccessful()
                .groupBy { it.dateTimeStrFormat.take(7) }
                .map { (month, items) ->
                    MonthlySummary(
                        month = month,
                        spending = items.filterNot(::isIncome).sumOf { it.moneyValue() },
                        income = items.filter(::isIncome).sumOf { it.moneyValue() }
                    )
                }
                .sortedByDescending { it.month }
        }
    }

    // ==================== 新增统计功能 - 对齐 Rust 版 ====================

    /**
     * 统计汇总 - 对齐 Rust 版 get_statistics_summary
     */
    override fun getStatisticsSummary(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<StatisticsSummary> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
        Log.d("BillRepo", "getStatisticsSummary identityId=$identityId range=$start..$end")

        return getDatabases(identityId).flatMapLatest { dbs ->
            Log.d("BillRepo", "getStatisticsSummary dbs.size=${dbs.size}")
            combine(
                combine(dbs.map { db ->
                    db.billDao().getSumByTypeInRangeDotFormat(start, end)
                }) { results ->
                    val sums = results.flatMap { it.toList() }
                    Log.d("BillRepo", "getSumByTypeInRange rows=${sums.size} samples=${sums.take(8)}")
                    val expenseKeywords = listOf("消费", "支出", "扣款")
                    val incomeKeywords = listOf("充值", "存入", "转入", "退款", "返还", "冲正")
                    val expenseSum = sums.filter { row ->
                        expenseKeywords.any { row.type.contains(it) }
                    }.sumOf { kotlin.math.abs(it.total) }
                    val incomeSum = sums.filter { row ->
                        incomeKeywords.any { row.type.contains(it) }
                    }.sumOf { kotlin.math.abs(it.total) }
                    Log.d("BillRepo", "getStatisticsSummary expenseSum=$expenseSum incomeSum=$incomeSum")
                    expenseSum to incomeSum
                },
                combine(dbs.map { db ->
                    db.billDao().getSumByTypeInRangeDotFormat(start, end)
                }) { results ->
                    val sums = results.flatMap { it.toList() }
                    val expenseKeywords = listOf("消费", "支出", "扣款")
                    val incomeKeywords = listOf("充值", "存入", "转入", "退款", "返还", "冲正")
                    val expenseCount = sums.filter { row ->
                        expenseKeywords.any { row.type.contains(it) }
                    }.sumOf { 1 }
                    val incomeCount = sums.filter { row ->
                        incomeKeywords.any { row.type.contains(it) }
                    }.sumOf { 1 }
                    expenseCount to incomeCount
                }
            ) { (expenseSum, incomeSum), (expenseCount, incomeCount) ->
                val activeDays = 30
                val summary = StatisticsSummary(
                    totalExpense = expenseSum,
                    totalIncome = incomeSum,
                    netExpense = expenseSum - incomeSum,
                    dailyAverage = if (activeDays > 0) expenseSum / activeDays else 0.0,
                    expenseCount = expenseCount,
                    incomeCount = incomeCount
                )
                Log.d("BillRepo", "getStatisticsSummary -> $summary")
                summary
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
                db.billDao().getDailyTrendByTypeDotFormat(start, end)
            }) { results ->
                val merged = mutableMapOf<String, MutableMap<String, Double>>()
                for (list in results) {
                    for (item in list) {
                        val typeMap = merged.getOrPut(item.dateStr) { mutableMapOf() }
                        typeMap[item.type] = (typeMap[item.type] ?: 0.0) + kotlin.math.abs(item.total)
                    }
                }
                val expenseKeywords = listOf("消费", "支出", "扣款")
                val incomeKeywords = listOf("充值", "存入", "转入", "退款", "返还", "冲正")
                merged.entries.sortedBy { it.key }.map { (date, typeMap) ->
                    DailyTrend(
                        date = date,
                        expense = typeMap.filter { (k, _) -> expenseKeywords.any { k.contains(it) } }.values.sum(),
                        income = typeMap.filter { (k, _) -> incomeKeywords.any { k.contains(it) } }.values.sum()
                    )
                }
            }
        }
    }

    override fun getForgotCardStats(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<ForgotCardStats> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return mergedBills(identityId).map { bills ->
            val items = bills.filterSuccessful()
                .filterByRange(start, end)
                .asSequence()
                .filterNot(::isIncome)
                .filter { kotlin.math.abs(it.moneyValue() - 5.0) <= 0.01 }
                .filter(::isBathBill)
                .map { bill ->
                    ForgotCardItem(
                        id = bill.id,
                        date = bill.dateStr.ifBlank { bill.dateTimeStrFormat.replace(".", "-").substringBefore(" ") },
                        time = bill.timeStr.ifBlank { bill.dateTimeStrFormat.substringAfter(" ", "") },
                        amount = bill.moneyValue(),
                        targetUser = bill.targetUser
                    )
                }
                .sortedWith(compareByDescending<ForgotCardItem> { it.date }.thenByDescending { it.time })
                .toList()

            ForgotCardStats(
                count = items.size,
                totalAmount = items.sumOf { it.amount },
                items = items
            )
        }
    }

    /**
     * 用餐时段分布 - 对齐 Rust 版 get_meal_distribution
     * 数据源: meal.bill.items (从 bills 表读全量后按 time 在 Kotlin 层分类)
     * 优先用 MealClassifier (assets/bill/schedule.toml) 与 Tauri 端完全一致;
     * 不可用时回退到 hour 区间硬编码,保证 UI 永远有数据。
     */
    override fun getMealDistribution(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<MealDistribution>> {
        val start = dateStart ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = dateEnd ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)

        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db -> db.billDao().getAllBills() }) { results ->
                val merged = mutableMapOf<String, MutableMap<String, Double>>()
                val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
                for (list in results) {
                    for (item in list) {
                        if (!isSuccessfulStatus(item.status)) continue
                        if (isIncome(item)) continue
                        if (!isInRange(item.dateTimeStrFormat, start, end)) continue
                        val timestamp = try {
                            java.time.LocalDateTime
                                .parse(item.dateTimeStrFormat, formatter)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toEpochSecond()
                        } catch (_: Exception) { 0L }
                        val meal = mealClassifier.classify(timestamp) ?: inferMealFromHour(item.dateTimeStrFormat) ?: "其他"
                        val typeMap = merged.getOrPut(meal) { mutableMapOf() }
                        typeMap["count"] = (typeMap["count"] ?: 0.0) + 1
                        typeMap["amount"] = (typeMap["amount"] ?: 0.0) + item.moneyValue()
                    }
                }
                // 固定顺序:早餐, 午餐, 晚餐, 夜宵, 其他
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

    /**
     * 取指定 type + 时间区间内所有 bill(对齐 Rust get_category_bills)
     */
    override fun getCategoryBills(
        identityId: Long?,
        category: String,
        startDate: String?,
        endDate: String?
    ): Flow<List<BillItem>> {
        val start = startDate ?: YearMonth.now().atDay(1).format(DATE_FMT)
        val end = endDate ?: YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
        return getDatabases(identityId).flatMapLatest { dbs ->
            combine(dbs.map { db -> db.billDao().getBillsByTypeInRange(category, start, end) }) { results ->
                results.flatMap { it.toList() }.map { it.toDomain() }
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

    /**
     * 用餐时段分类器(由 [BillRepositoryImpl] 构造时或首次访问时懒加载 schedule.toml)。
     * 加载失败时降级为 [MealClassifier.defaultRules] — 与 Tauri schedule.toml 默认内容完全一致。
     * 优先走 [BillRulesManager] 本地缓存(GitHub 同步目标),缺失回退到 assets/bill/。
     */
    private val mealClassifier: cn.edu.shmtu.cas.classifier.MealClassifier by lazy {
        try {
            val rulesToml = runCatching { billRulesManager.readFile("rules.toml") }.getOrNull()
            val scheduleToml = runCatching { billRulesManager.readFile("schedule.toml") }.getOrNull()
            val text = rulesToml ?: scheduleToml
            if (text != null) {
                cn.edu.shmtu.cas.classifier.MealClassifier.fromRulesToml(text)
            } else {
                cn.edu.shmtu.cas.classifier.MealClassifier.defaultRules()
            }
        } catch (_: Exception) {
            cn.edu.shmtu.cas.classifier.MealClassifier.defaultRules()
        }
    }

    /**
     * 账单分类器(由 [BillRepositoryImpl] 构造时或首次访问时懒加载 rules.toml),
     * 用于 [getCategoryBreakdown] 内部对 category 字段为空的老数据做兜底分类。
     * 优先走 [BillRulesManager] 本地缓存(GitHub 同步目标),缺失回退到 assets/bill/。
     */
    private val billClassifier: cn.edu.shmtu.cas.classifier.BillClassifier? by lazy {
        try {
            val rulesToml = runCatching { billRulesManager.readFile("rules.toml") }.getOrNull()
            val typeToml = runCatching { billRulesManager.readFile("type.toml") }.getOrNull()
            val text = rulesToml ?: typeToml ?: return@lazy null
            cn.edu.shmtu.cas.classifier.BillClassifier.fromRulesToml(text)
        } catch (_: Exception) {
            null
        }
    }

    private fun mergedBills(identityId: Long?): Flow<List<BillEntity>> {
        return getDatabases(identityId).flatMapLatest { dbs ->
            if (dbs.isEmpty()) {
                flow { emit(emptyList()) }
            } else {
                combine(dbs.map { db -> db.billDao().getAllBills() }) { results ->
                    results.flatMap { it.toList() }
                }
            }
        }
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_FMT_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}

private fun cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity.toDomain() = EntityMappers.run { this@toDomain.toDomain() }
private fun BillEntity.moneyValue(): Double = money.replace("¥", "").replace(",", "").toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0

private fun isIncome(bill: BillEntity): Boolean {
    val keywords = listOf("充值", "冲正", "退款", "返还", "补偿", "存入", "转入")
    val res = keywords.any { keyword ->
        bill.type.contains(keyword) || bill.targetUser.contains(keyword)
    }
    return res
}

private fun isBathBill(bill: BillEntity): Boolean {
    val type = bill.type.lowercase()
    val target = bill.targetUser.lowercase()
    return type == "bath" ||
        type.contains("洗澡") ||
        type.contains("淋浴") ||
        type.contains("热水") ||
        target.contains("淋浴") ||
        target.contains("热水")
}

/**
 * 兼容多套状态值: SQL 写入用 "SUCCESS" / 中文混合时用 "交易成功"。
 */
private fun isSuccessfulStatus(status: String): Boolean =
    status == "SUCCESS" || status == "交易成功"

/**
 * 把库内 'dateTimeStrFormat'('yyyy.MM.dd HH:mm:ss' 或 'yyyy-MM-dd HH:mm:ss')的 '.' 替换为 '-' 后
 * 再与 startDate/endDate 做字符串字典序比较 — 兼容所有时间范围字符串格式。
 */
private fun isInRange(dateTimeStrFormat: String, startDate: String, endDate: String): Boolean {
    val normalized = dateTimeStrFormat.replace(".", "-")
    return normalized >= startDate && normalized <= endDate
}

/**
 * 硬编码 hour → 时段,与 schedule.toml 加载失败时降级使用。
 * 与原 SQL CASE WHEN 区间完全一致:6-8 早餐 / 11-12 午餐 / 17-18 晚餐 / 21-22 夜宵 / 其他。
 */
private fun inferMealFromHour(dateTimeStrFormat: String): String? {
    // 取第 12-13 位字符(原 SQL substr(dateTimeStrFormat, 12, 2))。
    return try {
        if (dateTimeStrFormat.length < 13) return null
        val h = dateTimeStrFormat.substring(11, 13).toIntOrNull() ?: return null
        when (h) {
            in 6..8 -> "早餐"
            in 11..12 -> "午餐"
            in 17..18 -> "晚餐"
            in 21..22 -> "夜宵"
            else -> "其他"
        }
    } catch (_: Exception) { null }
}

private fun List<BillEntity>.filterSuccessful(): List<BillEntity> {
    val filtered = filter { it.status == "SUCCESS" }
    if (isNotEmpty() && filtered.isEmpty()) {
        val first = first()
        val distinctStatuses = map { it.status }.distinct()
        Log.d("BillRepo", "filterSuccessful dropped all: size=${size} first.status='${first.status}' distinctStatuses=$distinctStatuses")
    }
    return filtered
}
private fun List<BillEntity>.filterByRange(startDate: String, endDate: String): List<BillEntity> {
    // 兼容库内 dateTimeStrFormat 格式 "yyyy.MM.dd HH:mm:ss" 与入参 "yyyy-MM-dd" / "yyyy-MM-dd 23:59:59"
    // 做法:把库内 '.' 替换为 '-' 后再做字符串比较
    val filtered = filter {
        val normalized = it.dateTimeStrFormat.replace(".", "-")
        normalized >= startDate && normalized <= endDate
    }
    if (isNotEmpty() && filtered.isEmpty()) {
        val first = first()
        Log.d("BillRepo", "filterByRange dropped all: range=$startDate..$endDate first.dateTime='${first.dateTimeStrFormat}'")
    }
    return filtered
}
