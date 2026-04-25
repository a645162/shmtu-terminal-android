package cn.edu.shmtu.terminal.android.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import kotlinx.coroutines.flow.Flow

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
}
