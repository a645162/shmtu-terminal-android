package cn.edu.shmtu.terminal.android.data.sync

import cn.edu.shmtu.cas.datatype.BillItem
import cn.edu.shmtu.cas.sync.BillStore
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabase
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.mapper.EntityMappers.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room 适配的 [BillStore]
 *
 * 把 lib 端 [BillItem] 转成 app 端 [BillEntity] 并写入当前账号的 Room 数据库。
 * 同时写入"账号原始库"和"身份合并库"，与之前 [SyncAccountBillsUseCase] 的语义一致。
 *
 * 线程安全：单实例由 [SyncAccountBillsUseCase] 持有；
 * [cn.edu.shmtu.cas.sync.syncAccountsParallel] 场景下每个账号持有一个独立实例。
 */
@Singleton
class RoomBillStore @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    /** 当前账号的 app 域 ID（用于双写：account + identity） */
    private val accountId: Long,
    /** 当前账号的 userId（用于定位 account 数据库） */
    private val studentId: String,
    /** 当前账号所属的 identity ID（用于双写 identity 合并库） */
    private val identityId: Long,
) : BillStore {

    private val accountDb: BillDatabase
        get() = billDbManager.getAccountDatabase(studentId)

    private val identityDb: BillDatabase
        get() = billDbManager.getIdentityDatabase(identityId)

    override fun contains(transactionNo: String): Boolean = kotlinx.coroutines.runBlocking {
        accountDb.billDao().existsByTransactionNo(transactionNo)
    }

    override fun merge(newBills: List<BillItem>) {
        val entities: List<BillEntity> = newBills.map { it.toEntity(accountId, studentId) }
        kotlinx.coroutines.runBlocking {
            accountDb.billDao().insertAll(entities)
            identityDb.billDao().insertAll(entities)
        }
    }

    override fun clear() {
        kotlinx.coroutines.runBlocking {
            accountDb.billDao().deleteByAccountId(accountId)
            identityDb.billDao().deleteByAccountId(accountId)
        }
    }
}
