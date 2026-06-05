package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.ConsumptionBucket
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.ForgotCardStats
import cn.edu.shmtu.terminal.android.domain.model.MealDistribution
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface BillRepository {
    fun getBillsForIdentity(identityId: Long): Flow<List<BillItem>>
    fun getBillsForAccount(identityId: Long, accountId: Long): Flow<List<BillItem>>
    suspend fun syncAccountBills(accountId: Long): SyncResult
    suspend fun syncIdentityBills(identityId: Long): SyncResult
    suspend fun deleteBillsForAccount(accountId: Long, identityId: Long)

    /** 同步进度事件流 (对齐 Rust 版 sync-progress 事件) */
    val syncProgress: SharedFlow<SyncProgress>

    /** 带进度的增量同步 - 单账号 */
    suspend fun syncAccountBillsWithProgress(accountId: Long): SyncResult

    /** 带进度的增量同步 - 身份全部账号 */
    suspend fun syncIdentityBillsWithProgress(identityId: Long): SyncResult

    /** 带进度的全量同步 - 单账号 */
    suspend fun fullSyncAccountWithProgress(accountId: Long): SyncResult

    /** 带进度的全量同步 - 身份全部账号 */
    suspend fun fullSyncIdentityWithProgress(identityId: Long): SyncResult

    fun getBillOverview(identityId: Long?): Flow<BillOverview>
    fun getSpendingTrend(identityId: Long?, startDate: String, endDate: String): Flow<List<SpendingTrend>>
    fun getCategoryBreakdown(identityId: Long?, startDate: String, endDate: String): Flow<List<CategoryBreakdown>>
    fun getTargetUserRanking(identityId: Long?, startDate: String, endDate: String, limit: Int): Flow<List<TargetUserRanking>>
    fun getMonthlySummary(identityId: Long?): Flow<List<MonthlySummary>>

    /** 统计功能 - 对齐 Rust 版 statistics commands */
    fun getStatisticsSummary(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<StatisticsSummary>
    fun getDailyTrend(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<DailyTrend>>
    fun getForgotCardStats(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<ForgotCardStats>
    fun getMealDistribution(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<MealDistribution>>
    fun getConsumptionDistribution(identityId: Long?, dateStart: String?, dateEnd: String?): Flow<List<ConsumptionBucket>>

    /**
     * 取指定 type + 时间区间内所有 bill(对齐 Rust get_category_bills)
     * 用于"分类分析" Tab 选中具体分类时显示消费明细
     */
    fun getCategoryBills(identityId: Long?, category: String, startDate: String?, endDate: String?): Flow<List<BillItem>>

    /**
     * 重算数据库中**所有**账单的 building / room / position / category。
     *
     * 用途: 之前因 [cn.edu.shmtu.terminal.android.data.remote.EpayAdapter.positionTranslator]
     * 加载顺序错误(rules.toml 优先于 position.toml),老数据里"海馨第一/二/三/四食堂"
     * 等仅出现在 position.toml 的位置规则被静默丢失。该函数在不重新走 CAS 登录、
     * 不重新拉取账单的前提下,用修复后的 classifier + positionTranslator 把所有
     * 已有 bill 行重算并写回数据库。
     *
     * 遍历范围: 已打开过的所有 account 数据库 + 所有 identity 数据库。
     */
    suspend fun reclassifyAllBills(
        onProgress: (ReclassifyProgress) -> Unit = {},
    ): ReclassifyResult
}

/**
 * 重算结果统计(给 UI 展示)
 */
data class ReclassifyResult(
    val totalScanned: Int,
    val translated: Int,
    val categoryUpdated: Int,
    val missed: Int,
    val durationMs: Long,
    val missedSamples: List<ReclassifyMissSample> = emptyList(),
)

data class ReclassifyMissSample(
    val targetUser: String,
    val sampleType: String,
    val count: Int,
    val suggestedToml: String,
    val candidates: List<ReclassifyMissCandidate> = emptyList(),
)

data class ReclassifyMissCandidate(
    val keyword: String,
    val building: String,
    val room: String,
    val score: Int,
)

data class ReclassifyProgress(
    val processed: Int,
    val total: Int,
    val currentDbIndex: Int,
    val totalDbs: Int,
    val currentTargetUser: String? = null,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()
}
