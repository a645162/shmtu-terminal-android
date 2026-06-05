package cn.edu.shmtu.terminal.android.data.sync

import android.util.Log
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
 * 同时写入"账号原始库"和"身份合并库",与之前 [SyncAccountBillsUseCase] 的语义一致。
 *
 * **分类与位置翻译**:
 * - 在 merge 写入数据库之前,根据 [cn.edu.shmtu.cas.classifier.BillClassifier] 与
 *   [cn.edu.shmtu.cas.classifier.PositionTranslator] 实时计算每条 bill 的
 *   `category` / `position` / `room` / `building` 字段,落库持久化。
 * - 计算规则与 Tauri `get_bill_statistics` 完全一致: 按 `item_type` / `target_user` 子串匹配,
 *   命中 type.toml 顺序匹配, position.toml 精确+contains 匹配。
 *
 * 线程安全:单实例由 [SyncAccountBillsUseCase] 持有;
 * [cn.edu.shmtu.cas.sync.syncAccountsParallel] 场景下每个账号持有一个独立实例。
 */
@Singleton
class RoomBillStore @Inject constructor(
    private val billDbManager: BillDatabaseManager,
    /** 当前账号的 app 域 ID（用于双写:account + identity） */
    private val accountId: Long,
    /** 当前账号的 userId（用于定位 account 数据库） */
    private val studentId: String,
    /** 当前账号所属的 identity ID（用于双写 identity 合并库） */
    private val identityId: Long,
) : BillStore {

    private val TAG = "RoomBillStore"

    private val accountDb: BillDatabase
        get() = billDbManager.getAccountDatabase(studentId)

    private val identityDb: BillDatabase
        get() = billDbManager.getIdentityDatabase(identityId)

    /**
     * 注入的分类器 + 翻译器。Hilt 通过 [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter]
     * 已提供懒加载的 [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter.classifier] /
     * [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter.positionTranslator],
     * 这里直接由 SyncAccountBillsUseCase 在构造时注入;若未注入,降级为 null,
     * merge 仍然能跑,只是 category/position 全部为 null(运行时统计时再即时算)。
     */
    var classifier: cn.edu.shmtu.cas.classifier.BillClassifier? = null
    var positionTranslator: cn.edu.shmtu.cas.classifier.PositionTranslator? = null

    override fun contains(transactionNo: String): Boolean = kotlinx.coroutines.runBlocking {
        accountDb.billDao().existsByTransactionNo(transactionNo)
    }

    override fun merge(newBills: List<BillItem>) {
        val rawEntities: List<BillEntity> = newBills.map { it.toEntity(accountId, studentId) }
        // 落库前按 Tauri 语义即时计算分类 / 位置翻译 — 与 Tauri BillClassifier.classify +
        // PositionTranslator.translate 行为一致,首次命中即返回,精确 + contains 匹配。
        val entities: List<BillEntity> = rawEntities.map { e ->
            // 回调式 trace:让 lib 把命中模式暴露出来,日志在 app 层打。
            // (lib 模块是纯 JVM,不能直接 import android.util.Log)
            var lastTraceMode: String? = null
            var lastTraceKeyword: String? = null
            val pos = positionTranslator?.translate(e.targetUser) { mode, keyword, _ ->
                lastTraceMode = mode
                lastTraceKeyword = keyword
            }
            val cat = classifier?.classifyKey(e.type, e.targetUser)
            // ====== 详细 logcat:方便定位为什么某条 bill 没被正确翻译 ======
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "[translate] tx=${e.transactionNo} " +
                        "type='${e.type}' targetUser='${e.targetUser}' " +
                        "money=${e.money} " +
                        "→ category=${cat ?: "(null→other)"} " +
                        "building=${pos?.position ?: "(null)"} " +
                        "room=${pos?.room ?: "(null)"} " +
                        "matchMode=${lastTraceMode ?: "(none)"} " +
                        "matchedKeyword='${lastTraceKeyword ?: ""}'")
            }
            // WARN 级:没命中位置规则的 bill 显式标出,便于扫日志快速定位问题条目
            if (Log.isLoggable(TAG, Log.WARN) && pos == null && e.targetUser.isNotBlank()) {
                Log.w(TAG, "[translate-MISS] tx=${e.transactionNo} " +
                        "targetUser='${e.targetUser}' NO position rule matched. " +
                        "rulesLoaded=${positionTranslator?.getAllKeywords()?.size ?: 0}")
            }
            e.copy(
                category = cat ?: "other",
                position = pos?.position ?: e.position,
                room = pos?.room ?: e.room,
                building = pos?.position ?: e.building
            )
        }
        Log.i(TAG, "merge() accountId=$accountId newBills=${newBills.size} " +
                "classifier=${if (classifier != null) "loaded(${classifier!!.ruleCount()} rules)" else "NULL"} " +
                "positionTranslator=${if (positionTranslator != null) "loaded(${positionTranslator!!.getAllKeywords().size} keywords)" else "NULL"}")
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
