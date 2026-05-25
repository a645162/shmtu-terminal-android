package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabase
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class BillRepositoryImpl @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val accountRepository: AccountRepository,
    private val identityRepository: IdentityRepository,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase
) : BillRepository {

    override fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>> {
        // 读取 identity_{identityId}.sqlite 中的合并账单
        return billDbManager.getIdentityDatabase(identityId)
            .billDao().getAllBills()
            .map { list -> list.map { it.toDomain() } }
    }

    override fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>> {
        // 通过 accountId 找到 studentId，然后读取 account_{studentId}.sqlite
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
        val accountList = accountRepository.getAccountsByIdentity(identityId).first()
        var totalNew = 0
        var hasError = false
        var errorMsg: String? = null

        for (account in accountList) {
            val result = syncAccountBillsUseCase(account)
            totalNew += result.newCount
            if (!result.success) {
                hasError = true
                errorMsg = result.errorMessage
            }
        }

        return SyncResult(totalNew, !hasError, errorMsg)
    }

    override suspend fun deleteBillsForAccount(accountId: Long, identityId: Long) {
        // 从 identity 数据库删除该账号的账单
        billDbManager.getIdentityDatabase(identityId)
            .billDao().deleteByAccountId(accountId)
    }

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
