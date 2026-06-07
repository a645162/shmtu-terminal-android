package cn.edu.shmtu.terminal.android.data.webserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 通用 JSON 响应包装
 */
@Serializable
data class ApiResponse(
    val success: Boolean,
    val data: String? = null,
    val error: String? = null,
    val timestamp: String = java.time.Instant.now().toString()
) {
    companion object {
        /**
         * 共享 Json 实例
         */
        @PublishedApi
        internal val sharedJson: Json = Json { prettyPrint = false; ignoreUnknownKeys = true }

        /**
         * 序列化任意 @Serializable 数据为 ApiResponse JSON 字符串
         */
        inline fun <reified T> success(data: T): String {
            val dataJson = sharedJson.encodeToString(serializer<T>(), data)
            val resp = ApiResponse(success = true, data = dataJson)
            return sharedJson.encodeToString(serializer(), resp)
        }

        fun error(message: String): String {
            val resp = ApiResponse(success = false, error = message)
            return sharedJson.encodeToString(serializer(), resp)
        }
    }
}

/**
 * 服务信息
 */
@Serializable
data class ServerInfo(
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val token: String,
    val protocolVersion: String = "1.0"
)

/**
 * 分页账单请求参数
 */
data class BillQueryParams(
    val identityId: Long? = null,
    val dateStart: String? = null,
    val dateEnd: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50
)

/**
 * 通用分页响应
 */
@Serializable
data class PagedBills(
    val items: String,
    val page: Int,
    val pageSize: Int,
    val total: Int
)

/**
 * 统计数据响应
 */
@Serializable
data class StatisticsResponse(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val transactionCount: Int = 0,
    val dailyAverage: Double = 0.0,
    val categoryBreakdown: List<CategoryStat> = emptyList(),
    val dailyTrend: List<DailyStat> = emptyList()
)

@Serializable
data class CategoryStat(
    val name: String,
    val value: Double,
    val count: Int
)

@Serializable
data class DailyStat(
    val date: String,
    val expense: Double
)

/**
 * 鉴权响应
 */
@Serializable
data class AuthData(
    val token: String,
    val issuedAt: String
)

