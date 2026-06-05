package cn.edu.shmtu.terminal.android.data.dedupe

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabase
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单去重仓库 - 对齐 Tauri `dedupe_identity_bills` / `dedupe_account_bills`。
 *
 * 去重规则: 同 (accountId, transactionNo) 视为同一条账单, 只保留最早 (MIN(id)),
 * 删除其余重复行。
 */
@Singleton
class BillDedupeRepository @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    private val identityRepository: IdentityRepository
) {
    private suspend fun currentIdentityDb(): BillDatabase {
        val identityId = identityRepository.getCurrentIdentityId().first()
            ?: error("当前没有可用身份，无法执行去重")
        return billDbManager.getIdentityDatabase(identityId)
    }

    /**
     * 身份级去重 - 对当前身份的合并库做去重。
     * @return Pair<保留行数, 删除行数>
     */
    suspend fun dedupeIdentity(): Pair<Int, Int> {
        val identityDb = currentIdentityDb()
        val before = identityDb.billDao().getCount()
        val removed = identityDb.billDao().dedupeByTransactionNo()
        val after = identityDb.billDao().getCount()
        return Pair(after, removed)
    }

    /**
     * 账号级去重 - 调用方传入 identityId, 内部定位到对应身份合并库。
     * Tauri 端是"账号数据库", Android 端按设计统一写入 identity 合并库, 此处接口保留。
     */
    suspend fun dedupeAccount(identityId: Long): Pair<Int, Int> {
        val identityDb = billDbManager.getIdentityDatabase(identityId)
        val before = identityDb.billDao().getCount()
        val removed = identityDb.billDao().dedupeByTransactionNo()
        val after = identityDb.billDao().getCount()
        return Pair(after, removed)
    }
}
