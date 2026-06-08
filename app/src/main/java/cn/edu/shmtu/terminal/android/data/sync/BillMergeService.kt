package cn.edu.shmtu.terminal.android.data.sync

import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.db.dao.BillDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账单合并服务
 *
 * 核心规则：
 * - **触发时机**：新增账单时（落库前/落库后）由调用方决定；本服务只做"给定新账单，找出可合并的已有账单，产出合并结果"
 * - **合并条件**（全部满足）：
 *   1. 同一账号（accountId）
 *   2. 同一类型（type）
 *   3. 同一商户/位置（targetUser），允许模糊（trim 后相等）
 *   4. 相邻时间间隔 < 阈值（默认 15 分钟，可配置）
 *   5. 状态一致（status）
 *   6. 仅对"洗澡/热水"类账单生效（type 含"浴"/"洗澡"/"热水"/"淋浴"/"shower"/"bath"等关键词）
 *
 * - **合并后语义**（首尾合并）：
 *   - `transactionNo` 保留为新账单的交易号
 *   - `mergedTransactionNos` = [前一个账单.txn, ..., 当前.txn]
 *   - `dateTimeStrFormat` = 两笔中较晚的（即"账单结束后"）
 *   - `mergedDateTimes` = [前一个时间, 当前时间]（按升序）
 *   - `money` = 两笔之和（数值相加）
 *   - `timeStr` 记录结束时间
 *   - `isMerged` = true，`mergedBillCount` = 笔数
 *
 * - **不删除原账单**（保留可追溯性），但**隐藏/标记**在展示层
 */
@Singleton
class BillMergeService @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val tag = "BillMergeService"

    /**
     * 洗澡/热水账单分类关键词（不区分大小写）
     * 与 Tauri Rust 端 BillClassifier.classify 的 type_label "bath" 对齐。
     */
    private val bathKeywords = listOf(
        "浴", "洗澡", "热水", "淋浴", "水控", "洗浴", "bath", "shower", "wash"
    )

    /**
     * 判断账单是否属于"洗澡/热水"类
     */
    fun isBathBill(bill: BillEntity): Boolean {
        val typeLower = bill.type.lowercase(Locale.ROOT)
        val targetLower = bill.targetUser.lowercase(Locale.ROOT)
        return bathKeywords.any { keyword ->
            val k = keyword.lowercase(Locale.ROOT)
            typeLower.contains(k) || targetLower.contains(k)
        }
    }

    /**
     * 获取当前配置的合并阈值（分钟）
     */
    suspend fun getMergeThresholdMinutes(): Int {
        return settingsDataStore.getBillMergeThresholdMinutes()
    }

    /**
     * 给定一笔新账单，查找同一账号中可以与它合并的**最近一笔**账单
     *
     * @param newBill 新增的账单
     * @param dao 账单 DAO（accountDb 或 identityDb）
     * @param thresholdMinutes 合并阈值（分钟）
     * @return 可合并的已有账单；找不到返回 null
     */
    suspend fun findMergeableBill(
        newBill: BillEntity,
        dao: BillDao,
        thresholdMinutes: Int
    ): BillEntity? {
        if (!isBathBill(newBill)) return null

        // 查找同一类型在时间窗口内的所有账单
        val newTime = parseDateTime(newBill.dateTimeStrFormat) ?: return null
        val windowStart = newTime - thresholdMinutes * 60_000L - 60_000L
        val windowEnd = newTime + 60_000L

        val candidates = dao.getBillsByTypeInRange(
            type = newBill.type,
            startDate = formatDateTime(windowStart),
            endDate = formatDateTime(windowEnd)
        ).first()

        return candidates
            .filter { it.id != newBill.id }
            .filter { it.accountId == newBill.accountId }
            .filter { it.status == newBill.status }
            .filter { normalizeTarget(it.targetUser) == normalizeTarget(newBill.targetUser) }
            .filter { it.transactionNo != newBill.transactionNo }
            .mapNotNull { existing ->
                val existingTime = parseDateTime(existing.dateTimeStrFormat) ?: return@mapNotNull null
                val gap = newTime - existingTime
                if (gap in 0..(thresholdMinutes * 60_000L)) existing to gap else null
            }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * 合并两笔账单，返回合并后的新账单
     * **不修改数据库**，只返回实体
     */
    fun mergeBills(existing: BillEntity, newBill: BillEntity): BillEntity {
        // 合并交易号列表
        val existingTxns = if (existing.isMerged && existing.mergedTransactionNos.isNotEmpty()) {
            existing.mergedTransactionNos
        } else {
            listOf(existing.transactionNo)
        }
        val newTxns = if (newBill.isMerged && newBill.mergedTransactionNos.isNotEmpty()) {
            newBill.mergedTransactionNos
        } else {
            listOf(newBill.transactionNo)
        }
        val allTxns = (existingTxns + newTxns).distinct()

        // 合并时间列表
        val existingTimes = if (existing.isMerged && existing.mergedDateTimes.isNotEmpty()) {
            existing.mergedDateTimes
        } else {
            listOf(existing.dateTimeStrFormat)
        }
        val newTimes = if (newBill.isMerged && newBill.mergedDateTimes.isNotEmpty()) {
            newBill.mergedDateTimes
        } else {
            listOf(newBill.dateTimeStrFormat)
        }
        val allTimes = (existingTimes + newTimes).distinct().sorted()

        // 金额相加
        val totalMoney = (parseMoney(existing.money) + parseMoney(newBill.money)).let {
            String.format(Locale.ROOT, "%.2f", it)
        }

        // 取新账单为代表（覆盖原账单的 identity 字段）
        return newBill.copy(
            transactionNo = newBill.transactionNo,
            dateTimeStrFormat = allTimes.last(),  // 结束时间
            dateStr = allTimes.last().substringBefore(' '),
            timeStr = allTimes.last().substringAfter(' ').take(8),
            money = totalMoney,
            mergedTransactionNos = allTxns,
            mergedDateTimes = allTimes,
            isMerged = true,
            mergedBillCount = existing.mergedBillCount + newBill.mergedBillCount
        )
    }

    /**
     * 商户名称归一化（用于匹配）
     */
    private fun normalizeTarget(target: String): String =
        target.trim().replace("\\s+".toRegex(), "")

    /**
     * 解析日期时间字符串
     */
    private fun parseDateTime(text: String): Long? {
        if (text.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy.MM.dd HH:mm"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ROOT)
                sdf.timeZone = TimeZone.getDefault()
                val date = sdf.parse(text) ?: continue
                return date.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * 解析金额字符串为 Double
     */
    private fun parseMoney(money: String): Double {
        return try {
            money.replace("¥", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
        } catch (_: Exception) { 0.0 }
    }
}
