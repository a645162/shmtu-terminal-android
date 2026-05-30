package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import kotlinx.coroutines.flow.Flow

data class TypeSum(val type: String, val total: Double)

data class DailyTotal(val dateStr: String, val total: Double)

data class TargetUserTotal(val targetUser: String, val total: Double)

data class MonthlyTypeSum(val month: String, val type: String, val total: Double)

/** 每日趋势（按日期+类型分组） */
data class DailyTypeSum(val dateStr: String, val type: String, val total: Double)

/** 用餐时段分布 */
data class MealCount(
    val meal: String,
    val count: Int,
    val amount: Double
)

/** 消费金额分布区间 */
data class ConsumptionRange(
    val bucket: String,
    val count: Int,
    val amount: Double
)

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(bills: List<BillEntity>): List<Long>

    @Query("SELECT * FROM bills ORDER BY dateTimeStrFormat DESC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY dateTimeStrFormat DESC LIMIT 1")
    suspend fun getLatestBill(): BillEntity?

    @Query("SELECT COUNT(*) > 0 FROM bills WHERE transactionNo = :transactionNo")
    suspend fun existsByTransactionNo(transactionNo: String): Boolean

    @Query("SELECT * FROM bills WHERE accountId = :accountId ORDER BY dateTimeStrFormat DESC")
    fun getBillsByAccount(accountId: Long): Flow<List<BillEntity>>

    @Query("DELETE FROM bills WHERE accountId = :accountId")
    suspend fun deleteByAccountId(accountId: Long)

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun getCount(): Int

    @Query("""
        SELECT type, SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
        GROUP BY type
    """)
    fun getSumByTypeInRange(startDate: String, endDate: String): Flow<List<TypeSum>>

    @Query("""
        SELECT dateStr, SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
        GROUP BY dateStr
        ORDER BY dateStr
    """)
    fun getDailyTotalsInRange(startDate: String, endDate: String): Flow<List<DailyTotal>>

    @Query("""
        SELECT targetUser, SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
        GROUP BY targetUser
        ORDER BY total DESC
        LIMIT :limit
    """)
    fun getTopTargetUsers(startDate: String, endDate: String, limit: Int): Flow<List<TargetUserTotal>>

    @Query("""
        SELECT substr(dateTimeStrFormat, 1, 7) as month, type,
               SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        GROUP BY month, type
        ORDER BY month DESC
    """)
    fun getMonthlySummary(): Flow<List<MonthlyTypeSum>>

    @Query("""
        SELECT DISTINCT substr(dateTimeStrFormat, 1, 10) as dateStr
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
    """)
    fun getActiveDaysInRange(startDate: String, endDate: String): Flow<List<String>>

    // ==================== 新增查询 - 对齐 Rust 版统计 ====================

    /**
     * 每日趋势（按日期+类型分组）- 对齐 Rust 版 get_daily_trend
     * 返回每天的支出/收入明细
     */
    @Query("""
        SELECT substr(dateTimeStrFormat, 1, 10) as dateStr, type,
               SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
          AND status = '交易成功'
        GROUP BY dateStr, type
        ORDER BY dateStr
    """)
    fun getDailyTrendByType(startDate: String, endDate: String): Flow<List<DailyTypeSum>>

    /**
     * 用餐时段分布 - 对齐 Rust 版 get_meal_distribution
     * 根据时间判断时段：
     * - 早餐: 6:00-9:00
     * - 午餐: 11:00-13:00
     * - 晚餐: 17:00-19:00
     * - 夜宵: 21:00-23:00
     * - 其他: 剩余时段
     */
    @Query("""
        SELECT
            CASE
                WHEN CAST(substr(dateTimeStrFormat, 12, 2) AS INTEGER) BETWEEN 6 AND 8 THEN '早餐'
                WHEN CAST(substr(dateTimeStrFormat, 12, 2) AS INTEGER) BETWEEN 11 AND 12 THEN '午餐'
                WHEN CAST(substr(dateTimeStrFormat, 12, 2) AS INTEGER) BETWEEN 17 AND 18 THEN '晚餐'
                WHEN CAST(substr(dateTimeStrFormat, 12, 2) AS INTEGER) BETWEEN 21 AND 22 THEN '夜宵'
                ELSE '其他'
            END as meal,
            COUNT(*) as count,
            SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as amount
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
          AND status = '交易成功'
          AND type LIKE '%消费%'
        GROUP BY meal
    """)
    fun getMealDistribution(startDate: String, endDate: String): Flow<List<MealCount>>

    /**
     * 消费金额分布 - 对齐 Rust 版 get_consumption_distribution
     * 5 个固定区间: <10元, 10-20元, 20-50元, 50-100元, >100元
     * Note: `range` is a SQLite reserved word, use `bucket` alias
     */
    @Query("""
        SELECT
            CASE
                WHEN CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL) < 10 THEN '<10'
                WHEN CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL) < 20 THEN '10-20'
                WHEN CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL) < 50 THEN '20-50'
                WHEN CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL) < 100 THEN '50-100'
                ELSE '>100'
            END as bucket,
            COUNT(*) as count,
            SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as amount
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
          AND status = '交易成功'
          AND type LIKE '%消费%'
        GROUP BY bucket
    """)
    fun getConsumptionDistribution(startDate: String, endDate: String): Flow<List<ConsumptionRange>>

    /**
     * 按位置/商户统计 - 对齐 Rust 版 get_merchant_ranking
     */
    @Query("""
        SELECT targetUser,
               SUM(CAST(REPLACE(REPLACE(money, '¥', ''), ',', '') AS REAL)) as total
        FROM bills
        WHERE dateTimeStrFormat >= :startDate AND dateTimeStrFormat <= :endDate
          AND status = '交易成功'
          AND type LIKE '%消费%'
        GROUP BY targetUser
        ORDER BY total DESC
        LIMIT :limit
    """)
    fun getMerchantRanking(startDate: String, endDate: String, limit: Int): Flow<List<TargetUserTotal>>
}
