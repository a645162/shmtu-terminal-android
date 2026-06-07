package cn.edu.shmtu.terminal.android.data.webserver

import android.content.Context
import android.util.Log
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.JsonBillItem
import cn.edu.shmtu.terminal.android.domain.model.JsonExport
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 NanoHTTPD 的账单 Web 服务器
 */
@Singleton
class BillWebServer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val identityRepository: IdentityRepository,
    private val webServerSettings: SettingsDataStoreWebExt
) {
    companion object {
        private const val TAG = "BillWebServer"
        private const val DEFAULT_PORT = 8080
        private const val TOKEN_BYTES = 16
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var currentToken: String = ""
    @Volatile
    private var innerServer: NanoHTTPD? = null
    @Volatile
    private var runningPort: Int = 0

    fun getCurrentToken(): String = currentToken
    fun isRunning(): Boolean = innerServer?.isAlive == true
    fun getPort(): Int = runningPort

    fun start(port: Int = DEFAULT_PORT): Result<Unit> {
        return try {
            if (innerServer?.isAlive == true) {
                Log.w(TAG, "Server already running on port $runningPort")
                return Result.success(Unit)
            }
            currentToken = loadOrCreateToken()
            val server = object : NanoHTTPD("0.0.0.0", port) {
                override fun serve(session: IHTTPSession): Response {
                    return handleRequest(session)
                }
            }
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            innerServer = server
            runningPort = port
            Log.i(TAG, "BillWebServer started on 0.0.0.0:$port")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BillWebServer", e)
            Result.failure(e)
        }
    }

    fun stop() {
        try {
            innerServer?.stop()
            Log.i(TAG, "BillWebServer stopped")
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        } finally {
            innerServer = null
            runningPort = 0
        }
    }

    fun shutdown() {
        stop()
        serverScope.cancel()
    }

    private fun loadOrCreateToken(): String {
        val existing = webServerSettings.webServerAuthTokenValue()
        if (existing.isNotBlank()) return existing
        val newToken = generateToken()
        webServerSettings.setWebServerAuthToken(newToken)
        return newToken
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ============== 请求处理 ==============

    private fun handleRequest(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        val method = session.method
        return try {
            if (uri == "/" && method == NanoHTTPD.Method.GET) {
                serveIndex()
            } else if (uri == "/api/auth" && method == NanoHTTPD.Method.POST) {
                handleAuth()
            } else if (uri.startsWith("/api/")) {
                if (!isAuthorized(session)) {
                    jsonError(401, "Unauthorized - token invalid or missing")
                } else {
                    handleApi(uri, method, session)
                }
            } else {
                jsonError(404, "Not Found: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleRequest error", e)
            jsonError(500, "Internal error: ${e.message}")
        }
    }

    private fun handleApi(
        uri: String,
        method: NanoHTTPD.Method,
        session: NanoHTTPD.IHTTPSession
    ): NanoHTTPD.Response {
        return when {
            uri == "/api/health" && method == NanoHTTPD.Method.GET -> handleHealth()
            uri == "/api/info" && method == NanoHTTPD.Method.GET -> handleInfo()
            uri == "/api/identities" && method == NanoHTTPD.Method.GET -> handleIdentities()
            uri == "/api/bills" && method == NanoHTTPD.Method.GET -> handleBills(session)
            uri.startsWith("/api/bills/") && method == NanoHTTPD.Method.GET -> handleBillDetail(uri)
            uri == "/api/statistics" && method == NanoHTTPD.Method.GET -> handleStatistics(session)
            uri == "/api/export.json" && method == NanoHTTPD.Method.GET -> handleExportJson()
            uri == "/api/export.csv" && method == NanoHTTPD.Method.GET -> handleExportCsv()
            else -> jsonError(404, "Not Found: $uri")
        }
    }

    private fun isAuthorized(session: NanoHTTPD.IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: session.headers["Authorization"]
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            val bearer = header.substringAfter("Bearer ").trim()
            if (bearer == currentToken) return true
        }
        val tokenParam = session.parameters["token"]?.firstOrNull()
        if (tokenParam != null && tokenParam == currentToken) return true
        return false
    }

    private fun handleAuth(): NanoHTTPD.Response {
        val data = AuthData(token = currentToken, issuedAt = java.time.Instant.now().toString())
        return jsonResponse(200, ApiResponse.success(data))
    }

    private fun handleHealth(): NanoHTTPD.Response {
        val data = mapOf("status" to "ok", "uptime" to "running")
        return jsonResponse(200, ApiResponse.success(data))
    }

    private fun handleInfo(): NanoHTTPD.Response {
        val info = ServerInfo(
            deviceName = android.os.Build.MODEL ?: "SHMTU Device",
            ipAddress = NetworkUtils.getLocalIpAddress(context),
            port = runningPort,
            token = currentToken
        )
        return jsonResponse(200, ApiResponse.success(info))
    }

    private fun handleIdentities(): NanoHTTPD.Response {
        val list = runBlocking { identityRepository.getAllIdentities().first() }
        return jsonResponse(200, ApiResponse.success(list))
    }

    private fun handleBills(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val identityId = params["identityId"]?.firstOrNull()?.toLongOrNull()
        val dateStart = params["dateStart"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val dateEnd = params["dateEnd"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val page = params["page"]?.firstOrNull()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val pageSize = params["pageSize"]?.firstOrNull()?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val keyword = params["keyword"]?.firstOrNull()?.takeIf { it.isNotBlank() }

        val bills = runBlocking { collectBills(identityId) }
        val filtered = bills.asSequence()
            .filter { dateStart.isNullOrBlank() || it.dateTimeStrFormat >= dateStart }
            .filter { dateEnd.isNullOrBlank() || it.dateTimeStrFormat <= dateEnd }
            .filter { item ->
                keyword.isNullOrBlank() ||
                    item.type.contains(keyword, ignoreCase = true) ||
                    item.targetUser.contains(keyword, ignoreCase = true) ||
                    (item.position?.contains(keyword, ignoreCase = true) == true)
            }
            .toList()

        val total = filtered.size
        val fromIdx = (page - 1) * pageSize
        val toIdx = (fromIdx + pageSize).coerceAtMost(total)
        val pageItems = if (fromIdx >= total) emptyList() else filtered.subList(fromIdx, toIdx)

        val itemsJson = json.encodeToString(
            ListSerializer(BillWebJson.serializer()),
            pageItems.map { it.toWebJson() }
        )
        val paged = PagedBills(items = itemsJson, page = page, pageSize = pageSize, total = total)
        return jsonResponse(200, ApiResponse.success(paged))
    }

    private fun handleBillDetail(uri: String): NanoHTTPD.Response {
        val idStr = uri.removePrefix("/api/bills/").substringBefore('?')
        val id = idStr.toLongOrNull() ?: return jsonError(400, "invalid bill id")
        val bill = runBlocking { findBillById(id) }
            ?: return jsonError(404, "bill not found")
        return jsonResponse(200, ApiResponse.success(bill.toWebJson()))
    }

    private fun handleStatistics(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val params = session.parameters
        val identityId = params["identityId"]?.firstOrNull()?.toLongOrNull()
        val dateStart = params["dateStart"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        val dateEnd = params["dateEnd"]?.firstOrNull()?.takeIf { it.isNotBlank() }

        val summary = runBlocking { billRepository.getStatisticsSummary(identityId, dateStart, dateEnd).first() }
        val daily = runBlocking { billRepository.getDailyTrend(identityId, dateStart, dateEnd).first() }
        val category = runBlocking {
            if (dateStart != null && dateEnd != null) {
                billRepository.getCategoryBreakdown(identityId, dateStart, dateEnd).first()
            } else {
                billRepository.getCategoryBreakdown(identityId, "", "").first()
            }
        }

        val resp = StatisticsResponse(
            totalExpense = summary.totalExpense,
            totalIncome = summary.totalIncome,
            transactionCount = summary.expenseCount + summary.incomeCount,
            dailyAverage = summary.dailyAverage,
            categoryBreakdown = category.map { CategoryStat(name = it.type, value = it.amount, count = 0) },
            dailyTrend = daily.map { DailyStat(date = it.date, expense = it.expense) }
        )
        return jsonResponse(200, ApiResponse.success(resp))
    }

    private fun handleExportJson(): NanoHTTPD.Response {
        val allBills = runBlocking { collectAllBills() }
        val items = allBills.map { bill ->
            JsonBillItem(
                dateTimeFormatted = bill.dateTimeStrFormat,
                timeStrFormatted = null,
                itemType = bill.type,
                number = bill.transactionNo,
                numberList = null,
                targetUser = bill.targetUser,
                moneyStr = bill.money,
                money = bill.money.toDoubleOrNull(),
                method = bill.method,
                statusStr = bill.status,
                isCombined = false,
                classification = bill.category
            )
        }
        val export = JsonExport(
            exportTime = java.time.Instant.now().toString(),
            identityName = "all",
            source = "android-webserver",
            bills = items
        )
        val body = buildString {
            append("{")
            append("\"exportTime\":").append(jsonStr(export.exportTime)).append(",")
            append("\"identityName\":").append(jsonStr(export.identityName)).append(",")
            append("\"source\":").append(jsonStr(export.source)).append(",")
            append("\"bills\":[")
            export.bills.forEachIndexed { idx, b ->
                if (idx > 0) append(",")
                append("{")
                append("\"dateTimeFormatted\":").append(jsonStr(b.dateTimeFormatted)).append(",")
                append("\"itemType\":").append(jsonStr(b.itemType)).append(",")
                append("\"number\":").append(jsonStr(b.number)).append(",")
                append("\"targetUser\":").append(jsonStr(b.targetUser)).append(",")
                append("\"moneyStr\":").append(jsonStr(b.moneyStr)).append(",")
                append("\"money\":").append(b.money?.toString() ?: "null").append(",")
                append("\"method\":").append(jsonStr(b.method)).append(",")
                append("\"statusStr\":").append(jsonStr(b.statusStr)).append(",")
                append("\"isCombined\":").append(b.isCombined).append(",")
                append("\"classification\":").append(jsonStr(b.classification))
                append("}")
            }
            append("]}")
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            body
        )
    }

        
    private fun jsonStr(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder()
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }



    private fun handleExportCsv(): NanoHTTPD.Response {
        val allBills = runBlocking { collectAllBills() }
        val sb = StringBuilder()
        sb.append("date,type,money,merchant,status,position,category\n")
        for (bill in allBills) {
            sb.append(csvEscape(bill.dateTimeStrFormat)).append(',')
            sb.append(csvEscape(bill.type)).append(',')
            sb.append(csvEscape(bill.money)).append(',')
            sb.append(csvEscape(bill.targetUser)).append(',')
            sb.append(csvEscape(bill.status)).append(',')
            sb.append(csvEscape(bill.position ?: "")).append(',')
            sb.append(csvEscape(bill.category ?: "")).append('\n')
        }
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "text/csv; charset=utf-8",
            sb.toString()
        )
    }

    // ============== 辅助 ==============

    private fun serveIndex(): NanoHTTPD.Response {
        return try {
            context.assets.open("web/index.html").use { input ->
                val bytes = input.readBytes()
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "text/html; charset=utf-8",
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read web/index.html", e)
            jsonError(500, "index.html missing: ${e.message}")
        }
    }

    private fun jsonResponse(status: Int, body: String): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.lookup(status) ?: NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            body
        )
    }

    private fun jsonError(status: Int, message: String): NanoHTTPD.Response {
        return jsonResponse(status, ApiResponse.error(message))
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private suspend fun collectBills(identityId: Long?): List<BillItem> {
        val identities = identityRepository.getAllIdentities().first()
        val targetIdentities = if (identityId != null) {
            identities.filter { it.id == identityId }
        } else {
            identities
        }
        val result = mutableListOf<BillItem>()
        for (identity in targetIdentities) {
            try {
                val items = billRepository.getBillsForIdentity(identity.id).first()
                result.addAll(items)
            } catch (e: Exception) {
                Log.w(TAG, "collectBills for identity ${identity.id} failed", e)
            }
        }
        return result.sortedByDescending { it.dateTimeStrFormat }
    }

    private suspend fun collectAllBills(): List<BillItem> = collectBills(null)

    private suspend fun findBillById(id: Long): BillItem? {
        val all = collectAllBills()
        return all.firstOrNull { it.id == id }
    }

    @kotlinx.serialization.Serializable
    data class BillWebJson(
        val id: Long,
        val accountId: Long,
        val accountLabel: String,
        val dateTime: String,
        val type: String,
        val transactionNo: String,
        val targetUser: String,
        val money: String,
        val method: String,
        val status: String,
        val position: String? = null,
        val room: String? = null,
        val category: String? = null,
        val building: String? = null
    )

    private fun BillItem.toWebJson(): BillWebJson = BillWebJson(
        id = id,
        accountId = accountId,
        accountLabel = accountLabel,
        dateTime = dateTimeStrFormat,
        type = type,
        transactionNo = transactionNo,
        targetUser = targetUser,
        money = money,
        method = method,
        status = status,
        position = position,
        room = room,
        category = category,
        building = building
    )
}
