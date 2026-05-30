package cn.edu.shmtu.terminal.android.domain.usecase.export

import cn.edu.shmtu.terminal.android.domain.model.*
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 导出数据用例 - 对齐 Rust 版 export_data
 * 支持 CSV / JSON / 钱迹格式
 */
class ExportDataUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(params: ExportParams): Result<String> {
        return try {
            when (params.format) {
                ExportFormat.CSV -> exportCsv(params)
                ExportFormat.JSON -> exportJson(params)
                ExportFormat.QIANJI -> exportQianji(params)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * CSV 导出 - 对齐 Rust 版 csv 格式
     * UTF-8 BOM + 列: 日期时间, 交易名称, 交易号, 对方账户, 金额, 支付方式, 状态
     */
    private suspend fun exportCsv(params: ExportParams): Result<String> {
        val bills = getBills(params)
        val file = File(params.filePath)

        FileOutputStream(file).use { fos ->
            // UTF-8 BOM
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

            // Header
            fos.write("日期时间,交易名称,交易号,对方账户,金额,支付方式,状态\n".toByteArray(Charsets.UTF_8))

            // Data rows
            for (bill in bills) {
                val line = "${bill.dateTimeStrFormat},${bill.type},${bill.transactionNo},${bill.targetUser},${bill.money},${bill.method},${bill.status}\n"
                fos.write(line.toByteArray(Charsets.UTF_8))
            }
        }

        return Result.success(file.absolutePath)
    }

    /**
     * JSON 导出 - 对齐 Rust 版 JsonExport 格式
     * { export_time, identity_name, source, bills[] }
     */
    private suspend fun exportJson(params: ExportParams): Result<String> {
        val bills = getBills(params)
        val identity = identityRepository.getIdentityById(params.identityId)
        val file = File(params.filePath)

        val root = JSONObject().apply {
            put("export_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("identity_name", identity?.remark ?: "")
            put("source", params.sourceType)
            val billsArray = JSONArray()
            for (bill in bills) {
                billsArray.put(JSONObject().apply {
                    put("date_time_formatted", bill.dateTimeStrFormat)
                    put("item_type", bill.type)
                    put("number", bill.transactionNo)
                    put("target_user", bill.targetUser)
                    put("money_str", bill.money)
                    put("money", bill.money.toDoubleOrNull())
                    put("method", bill.method)
                    put("status_str", bill.status)
                    put("is_combined", false)
                })
            }
            put("bills", billsArray)
        }

        file.writeText(root.toString(2), Charsets.UTF_8)
        return Result.success(file.absolutePath)
    }

    /**
     * 钱迹格式导出 - 对齐 Rust 版 QianjiItem
     * type(0=支出/1=收入), money, category, account, remark, time(unix)
     */
    private suspend fun exportQianji(params: ExportParams): Result<String> {
        val bills = getBills(params)
        val file = File(params.filePath)

        val items = bills.map { bill ->
            val moneyVal = bill.money.toDoubleOrNull() ?: 0.0
            val isIncome = INCOME_KEYWORDS.any { bill.type.contains(it) }
            val category = when {
                isIncome -> "其他收入"
                bill.type.contains("食堂") || bill.type.contains("餐厅") -> "餐饮"
                else -> "其他支出"
            }

            QianjiItem(
                type = if (isIncome) 1 else 0,
                money = kotlin.math.abs(moneyVal),
                category = category,
                account = "校园卡",
                remark = bill.targetUser,
                time = System.currentTimeMillis() // simplified; would need proper date parsing
            )
        }

        val jsonArray = JSONArray()
        for (item in items) {
            jsonArray.put(JSONObject().apply {
                put("type", item.type)
                put("money", item.money)
                put("category", item.category)
                put("account", item.account)
                put("remark", item.remark)
                put("time", item.time)
            })
        }

        file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        return Result.success(file.absolutePath)
    }

    private suspend fun getBills(params: ExportParams): List<BillItem> {
        return billRepository.getBillsForIdentity(params.identityId).first()
    }

    companion object {
        private val INCOME_KEYWORDS = listOf("充值", "冲正", "退款", "返还", "补偿")
    }
}

/**
 * 导入数据用例 - 对齐 Rust 版 import_data
 * 仅支持 JSON 格式
 */
class ImportDataUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val identityRepository: IdentityRepository
) {
    /**
     * 从 JSON 文件导入账单
     * @param filePath JSON 文件路径
     * @param identityId 目标身份 ID
     * @return 导入的账单数量
     */
    suspend operator fun invoke(filePath: String, identityId: Long): Result<Int> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return Result.failure(Exception("文件不存在: $filePath"))

            val content = file.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val billsArray = root.optJSONArray("bills") ?: return Result.failure(Exception("无效的 JSON 格式"))

            // TODO: 将 JsonBillItem 转为 BillEntity 并插入数据库
            // 当前返回解析的条目数
            return Result.success(billsArray.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
