package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.util.Log
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.classifier.BillClassifier
import cn.edu.shmtu.cas.classifier.ClassificationResult
import cn.edu.shmtu.cas.classifier.PositionInfo
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpayAdapter @Inject constructor(
    private val secureStorage: SecureStorage,
    @ApplicationContext private val context: Context
) {
    private val TAG = "EpayAdapter"

    private val instances = mutableMapOf<Long, EpayAuth>()

    /**
     * 账单分类器（懒加载，从 assets/bill_rules/ 加载规则文件）
     */
    private val classifier: BillClassifier? by lazy {
        try {
            val typeJson = context.assets.open("bill_rules/type.json").bufferedReader().readText()
            val positionJson = context.assets.open("bill_rules/position.json").bufferedReader().readText()
            val scheduleJson = context.assets.open("bill_rules/schedule.json").bufferedReader().readText()
            BillClassifier.fromJson(typeJson, positionJson, scheduleJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bill classifier: ${e.message}")
            null
        }
    }

    fun getEpayAuth(accountId: Long): EpayAuth {
        return instances.getOrPut(accountId) { createEpayAuthWithCookies(accountId) }
    }

    private fun createEpayAuthWithCookies(accountId: Long): EpayAuth {
        val epayAuth = EpayAuth()

        // 恢复会话 cookies
        secureStorage.getLoginCookie(accountId)?.let { cookie ->
            val result = epayAuth.restoreSession(cookie)
            if (result.isSuccess) {
                Log.d(TAG, "Restored loginCookie for account $accountId")
            }
        }

        // 恢复登录 URL
        secureStorage.getLoginUrl(accountId)?.let { url ->
            epayAuth.setLoginUrl(url)
            Log.d(TAG, "Restored loginUrl for account $accountId")
        }

        return epayAuth
    }

    /**
     * 探测登录状态
     */
    suspend fun probeLogin(accountId: Long): Result<SessionProbe> = withContext(Dispatchers.IO) {
        val epayAuth = getEpayAuth(accountId)
        val result = epayAuth.probeLogin()
        Log.d(TAG, "probeLogin for account $accountId: $result")
        result
    }

    /**
     * 获取验证码图片
     */
    suspend fun prepareChallenge(accountId: Long) = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).prepareChallenge()
        Log.d(TAG, "prepareChallenge for account $accountId")
        result
    }

    /**
     * 提交登录（自动获取 execution）
     */
    suspend fun submitLogin(accountId: Long, username: String, password: String, captchaCode: String): Result<LoginSubmitResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "submitLogin for account $accountId")

        val epayAuth = getEpayAuth(accountId)

        // 先获取 execution
        val challengeResult = epayAuth.prepareChallenge()
        if (challengeResult.isFailure) {
            Log.e(TAG, "prepareChallenge failed: ${challengeResult.exceptionOrNull()?.message}")
            return@withContext Result.failure(challengeResult.exceptionOrNull() ?: Exception("获取验证码失败"))
        }

        val challenge = challengeResult.getOrNull()
        if (challenge == null) {
            return@withContext Result.failure(Exception("获取验证码失败"))
        }

        // 提交登录
        val result = epayAuth.submitLogin(username, password, captchaCode, challenge.execution)

        if (result.isSuccess && result.getOrNull() is LoginSubmitResult.Success) {
            // 保存会话
            val cookiesJson = epayAuth.extractSession()
            secureStorage.saveLoginCookie(accountId, cookiesJson)
            secureStorage.saveLoginUrl(accountId, epayAuth.getLoginUrl())
            Log.d(TAG, "Saved cookies after successful login for account $accountId")
        }

        result
    }

    /**
     * 测试登录状态
     */
    suspend fun testLoginStatus(accountId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).testLoginStatus()
        Log.d(TAG, "testLoginStatus for account $accountId: $result")
        result
    }

    /**
     * 获取账单页面
     */
    suspend fun fetchBillPage(accountId: Long, pageNo: Int): Result<String> = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).getBill(pageNo)
        Log.d(TAG, "fetchBillPage account=$accountId page=$pageNo")
        result
    }

    /**
     * 解析账单 HTML，并在解析后自动分类和翻译
     *
     * 返回的 Map 包含以下额外字段：
     * - "category": 消费类型标签（如 "canteen"、"deposit"、"other"）
     * - "building": 建筑名（如 "海馨楼"）
     * - "room": 房间/餐厅名（如 "海馨第1食堂"）
     * - "meal": 用餐时段（如 "午餐"）
     */
    fun parseBillList(html: String): List<Map<String, String>> {
        val parser = BillParser()
        val bills = parser.getBillTr(html).getBillList().map { it as Map<String, String> }

        val clf = classifier
        if (clf != null) {
            return bills.map { bill ->
                val mutable = bill.toMutableMap()
                val result = clf.classify(
                    bill["type"] ?: "",
                    bill["targetUser"] ?: "",
                    bill["dateTimeStrFormat"]
                )
                mutable["category"] = result.typeLabel
                mutable["building"] = result.building
                mutable["room"] = result.room
                mutable["meal"] = result.meal
                mutable
            }
        }
        return bills
    }

    fun getPageCount(html: String): Int {
        return BillParser().getPageCount(html)
    }

    // ========== 账单分类与翻译 API ==========

    /**
     * 翻译对方账户
     *
     * @param targetUser 原始对方账户字符串（如"A食堂1楼大餐厅"）
     * @return 位置信息（建筑名 + 房间名），无匹配返回 null
     */
    fun translateTarget(targetUser: String): PositionInfo? {
        return classifier?.getPositionTranslator()?.translate(targetUser)
    }

    /**
     * 分类账单
     *
     * @param name 账单类型名（如"消费"、"中行云充值"）
     * @param target 对方账户（如"A食堂1楼大餐厅"）
     * @param dateTimeStr 日期时间（格式：yyyy-MM-dd HH:mm:ss），可选
     * @return 分类结果，分类器未初始化返回 null
     */
    fun classifyBill(name: String, target: String, dateTimeStr: String? = null): ClassificationResult? {
        return classifier?.classify(name, target, dateTimeStr)
    }

    /**
     * 按分类统计账单金额
     *
     * 对已解析的账单列表进行分类汇总，返回每个分类的总金额。
     *
     * @param bills 已解析的账单列表（来自 [parseBillList] 的返回结果）
     * @return 分类名 → 总金额 的映射（如 {"canteen": 125.5, "deposit": 200.0}）
     */
    fun getStatistics(bills: List<Map<String, String>>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val clf = classifier ?: return emptyMap()
        for (bill in bills) {
            val category = clf.classify(
                bill["type"] ?: "",
                bill["targetUser"] ?: "",
                bill["dateTimeStrFormat"]
            ).typeLabel
            val money = bill["money"]?.toDoubleOrNull() ?: 0.0
            result[category] = (result[category] ?: 0.0) + money
        }
        return result
    }
}
