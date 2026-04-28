package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import kotlinx.coroutines.flow.Flow

interface BillRepository {
    fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>>
    fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>>
    suspend fun syncAccountBills(accountId: Long): SyncResult
    suspend fun syncIdentityBills(identityId: Long): SyncResult
    suspend fun deleteBillsForAccount(accountId: Long, identityId: Long)

    fun getBillOverview(identityId: Long?): Flow<BillOverview>
    fun getSpendingTrend(identityId: Long?, startDate: String, endDate: String): Flow<List<SpendingTrend>>
    fun getCategoryBreakdown(identityId: Long?, startDate: String, endDate: String): Flow<List<CategoryBreakdown>>
    fun getTargetUserRanking(identityId: Long?, startDate: String, endDate: String, limit: Int): Flow<List<TargetUserRanking>>
    fun getMonthlySummary(identityId: Long?): Flow<List<MonthlySummary>>
}

data class SyncResult(
    val newCount: Int,
    val success: Boolean = true,
    val errorMessage: String? = null
)
