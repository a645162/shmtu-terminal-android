package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.BillItem
import kotlinx.coroutines.flow.Flow

interface BillRepository {
    fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>>
    fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>>
    suspend fun syncAccountBills(accountId: Long): SyncResult
    suspend fun syncIdentityBills(identityId: Long): SyncResult
    suspend fun deleteBillsForAccount(accountId: Long, identityId: Long)
}

data class SyncResult(
    val newCount: Int,
    val success: Boolean = true,
    val errorMessage: String? = null
)
