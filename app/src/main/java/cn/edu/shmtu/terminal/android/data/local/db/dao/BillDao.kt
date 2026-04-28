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
}
