package cn.edu.shmtu.terminal.android.domain.usecase.export

import android.util.Log
import cn.edu.shmtu.terminal.android.domain.model.ExportFormat
import cn.edu.shmtu.terminal.android.domain.model.ExportParams
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import javax.inject.Inject

/**
 * 安卓端数据导出:
 * - CSV / 钱迹: 维持原用途
 * - JSON: 改为 ZIP 数据包,可选口令加密,内含身份/账号/账单
 */
class ExportDataUseCase @Inject constructor(
    private val legacyExportUseCase: LegacyBillExportUseCase,
    private val transferArchiveService: TransferArchiveService
) {
    suspend operator fun invoke(params: ExportParams, password: String? = null): Result<String> {
        return try {
            when (params.format) {
                ExportFormat.CSV -> legacyExportUseCase.exportCsv(params)
                ExportFormat.QIANJI -> legacyExportUseCase.exportQianji(params)
                ExportFormat.JSON -> exportArchive(params, password)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exportArchive(params: ExportParams, password: String?): Result<String> {
        val file = File(params.filePath)
        file.parentFile?.mkdirs()

        val identityIds = setOf(params.identityId)
        val payload = transferArchiveService.buildEncryptedArchiveBytes(password, identityIds)
        FileOutputStream(file).use { it.write(payload.bytes) }
        return Result.success(file.absolutePath)
    }
}

/**
 * 安卓端数据导入:
 * JSON 入口保持不变,但实际读取的是 ZIP/加密 ZIP 包。
 */
class ImportDataUseCase @Inject constructor(
    private val transferArchiveService: TransferArchiveService
) {
    private val tag = "ImportDataUseCase"

    suspend operator fun invoke(filePath: String, password: String? = null): Result<Int> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return Result.failure(Exception("文件不存在: $filePath"))
            importFromBytesDetailed(file.readBytes(), password).map { it.billCount }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromBytes(data: ByteArray, password: String? = null): Result<Int> {
        return importFromBytesDetailed(data, password).map { it.billCount }
    }

    suspend fun importFromBytesDetailed(
        data: ByteArray,
        password: String? = null
    ): Result<ArchiveImportReport> {
        return try {
            val digest = shortSha256(data)
            val result = transferArchiveService.importArchiveBytes(data, password)
            Log.i(
                tag,
                "archive import success digest=$digest identities=${result.identityCount} accounts=${result.accountCount} bills=${result.billCount}"
            )
            Result.success(
                ArchiveImportReport(
                    identityCount = result.identityCount,
                    accountCount = result.accountCount,
                    billCount = result.billCount,
                    summary = "已导入 ${result.identityCount} 个身份、${result.accountCount} 个账号、${result.billCount} 条账单",
                    detail = "digest=$digest\nidentities=${result.identityCount}\naccounts=${result.accountCount}\nbills=${result.billCount}"
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "archive import failed bytes=${data.size}", e)
            Result.failure(e)
        }
    }

    private fun shortSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

data class ArchiveImportReport(
    val identityCount: Int,
    val accountCount: Int,
    val billCount: Int,
    val summary: String,
    val detail: String
)

/**
 * 保留旧的 CSV / 钱迹导出能力。
 */
class LegacyBillExportUseCase @Inject constructor(
    private val legacyExportDelegate: LegacyBillExportDelegate
) {
    suspend fun exportCsv(params: ExportParams): Result<String> = legacyExportDelegate.exportCsv(params)
    suspend fun exportQianji(params: ExportParams): Result<String> = legacyExportDelegate.exportQianji(params)
}

class LegacyBillExportDelegate @Inject constructor(
    private val billRepository: BillRepository
) {
    suspend fun exportCsv(params: ExportParams): Result<String> {
        val bills = billRepository.getBillsForIdentity(params.identityId).first()
        val file = File(params.filePath)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            fos.write("日期时间,交易名称,交易号,对方账户,金额,支付方式,状态\n".toByteArray(Charsets.UTF_8))
            bills.forEach { bill ->
                fos.write(
                    "${bill.dateTimeStrFormat},${bill.type},${bill.transactionNo},${bill.targetUser},${bill.money},${bill.method},${bill.status}\n"
                        .toByteArray(Charsets.UTF_8)
                )
            }
        }
        return Result.success(file.absolutePath)
    }

    suspend fun exportQianji(params: ExportParams): Result<String> {
        val bills = billRepository.getBillsForIdentity(params.identityId).first()
        val file = File(params.filePath)
        file.parentFile?.mkdirs()

        val jsonArray = JSONArray()
        bills.forEach { bill ->
            val moneyVal = bill.money.toDoubleOrNull() ?: 0.0
            val isIncome = listOf("充值", "冲正", "退款", "返还", "补偿").any { bill.type.contains(it) }
            val category = when {
                isIncome -> "其他收入"
                bill.type.contains("食堂") || bill.type.contains("餐厅") -> "餐饮"
                else -> "其他支出"
            }
            jsonArray.put(
                JSONObject().apply {
                    put("type", if (isIncome) 1 else 0)
                    put("money", abs(moneyVal))
                    put("category", category)
                    put("account", "校园卡")
                    put("remark", bill.targetUser)
                    put("time", System.currentTimeMillis())
                }
            )
        }

        file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        return Result.success(file.absolutePath)
    }
}
