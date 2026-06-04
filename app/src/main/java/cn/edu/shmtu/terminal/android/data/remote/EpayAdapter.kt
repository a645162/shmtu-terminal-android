package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.util.Log
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.classifier.BillCategory
import cn.edu.shmtu.cas.classifier.BillClassifier
import cn.edu.shmtu.cas.classifier.PositionInfo
import cn.edu.shmtu.cas.classifier.PositionTranslator
import cn.edu.shmtu.cas.datatype.BillType
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpayAdapter @Inject constructor(
    private val secureStorage: SecureStorage,
    @ApplicationContext private val context: Context,
    /** 由 Hilt 注入 [BillDatabaseManager] 供 [cn.edu.shmtu.terminal.android.data.sync.RoomBillStore] 使用 */
    val billDbManager: BillDatabaseManager,
) {
    private val TAG = "EpayAdapter"

    private val instances = mutableMapOf<Long, EpayAuth>()

    /**
     * 账单分类器（懒加载，从 assets/bill_rules/type.json 加载）
     */
    private val classifier: BillClassifier? by lazy {
        try {
            val typeJson = context.assets.open("bill_rules/type.json").bufferedReader().readText()
            BillClassifier.fromJson(typeJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load bill classifier: ${e.message}")
            null
        }
    }

    /**
     * 位置翻译器（懒加载，从 assets/bill_rules/position.json 加载）
     */
    private val positionTranslator: PositionTranslator? by lazy {
        try {
            val positionJson = context.assets.open("bill_rules/position.json").bufferedReader().readText()
            PositionTranslator.fromJson(positionJson)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load position translator: ${e.message}")
            null
        }
    }

    fun getEpayAuth(accountId: Long): EpayAuth {
        return instances.getOrPut(accountId) { createEpayAuthWithCookies(accountId) }
    }

    /**
     * 保存当前 [EpayAuth] 的 cookies 到 [SecureStorage]（登录成功后调用）
     */
    fun saveSessionCookies(accountId: Long, auth: EpayAuth) {
        try {
            val cookiesJson = auth.extractSession()
            secureStorage.saveLoginCookie(accountId, cookiesJson)
            Log.d(TAG, "Saved cookies for account $accountId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save session cookies: ${e.message}")
        }
    }

    /**
     * 清除 session，强制下次 [getEpayAuth] 时新登录
     */
    fun invalidateSession(accountId: Long) {
        secureStorage.removeLoginCookie(accountId)
        instances.remove(accountId)
        Log.d(TAG, "Invalidated session for account $accountId")
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

        return epayAuth
    }

    /**
     * 探测登录状态
     *
     * 若返回 NeedLogin(loginUrl)，调用方应自行保存 loginUrl。
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
     * 提交登录。调用方必须传入与当前验证码图片匹配的 execution。
     */
    suspend fun submitLogin(
        accountId: Long,
        username: String,
        password: String,
        captchaCode: String,
        execution: String,
    ): Result<LoginSubmitResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "submitLogin for account $accountId")

        val epayAuth = getEpayAuth(accountId)

        // 提交登录
        val result = epayAuth.submitLogin(username, password, captchaCode, execution)

        if (result.isSuccess && result.getOrNull() is LoginSubmitResult.Success) {
            // 保存会话
            val cookiesJson = epayAuth.extractSession()
            secureStorage.saveLoginCookie(accountId, cookiesJson)
            // 新 cas_lib 中 loginUrl 由 probeLogin() 返回值携带;此处尝试再次 probe 以获取并保存
            val probe = epayAuth.probeLogin()
            if (probe.isSuccess) {
                val p = probe.getOrNull()
                if (p is SessionProbe.AlreadyLoggedIn || p is SessionProbe.NeedLogin) {
                    val url = (p as? SessionProbe.NeedLogin)?.loginUrl ?: ""
                    if (url.isNotBlank()) secureStorage.saveLoginUrl(accountId, url)
                }
            }
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
        // 显式指定 BillType 以消歧 getBill(Int) 与 getBill(Int, String) 重载
        val result = getEpayAuth(accountId).getBill(pageNo = pageNo, billType = BillType.ALL)
        Log.d(TAG, "fetchBillPage account=$accountId page=$pageNo")
        result
    }

    /**
     * 解析账单 HTML，并在解析后自动分类和翻译
     *
     * 返回的 Map 包含以下额外字段：
     * - "category": 消费类型标签（如 "CANTEEN"、"DEPOSIT"、"OTHER"）
     * - "building": 建筑名
     * - "room": 房间/餐厅名
     */
    fun parseBillList(html: String): List<Map<String, String>> {
        val parser = BillParser()
        val bills = parser.getBillTr(html).getBillList().map { it as Map<String, String> }

        val clf = classifier
        val tr = positionTranslator
        if (clf != null || tr != null) {
            return bills.map { bill ->
                val mutable = bill.toMutableMap()
                if (clf != null) {
                    val category = clf.classify(
                        bill["type"] ?: "",
                        bill["targetUser"] ?: ""
                    )
                    mutable["category"] = category.name
                }
                if (tr != null) {
                    val pos = tr.translate(bill["targetUser"] ?: "")
                    if (pos != null) {
                        mutable["building"] = pos.position
                        mutable["room"] = pos.room
                    }
                }
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
        return positionTranslator?.translate(targetUser)
    }

    /**
     * 分类账单
     *
     * @param name 账单类型名（如"消费"、"中行云充值"）
     * @param target 对方账户（如"A食堂1楼大餐厅"）
     * @return 分类枚举，分类器未初始化返回 null
     */
    fun classifyBill(name: String, target: String): BillCategory? {
        return classifier?.classify(name, target)
    }

    /**
     * 按分类统计账单金额
     *
     * 对已解析的账单列表进行分类汇总，返回每个分类的总金额。
     *
     * @param bills 已解析的账单列表（来自 [parseBillList] 的返回结果）
     * @return 分类名 → 总金额 的映射（如 {"CANTEEN": 125.5, "DEPOSIT": 200.0}）
     */
    fun getStatistics(bills: List<Map<String, String>>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val clf = classifier ?: return emptyMap()
        for (bill in bills) {
            val category = clf.classify(
                bill["type"] ?: "",
                bill["targetUser"] ?: ""
            ).name
            val money = bill["money"]?.toDoubleOrNull() ?: 0.0
            result[category] = (result[category] ?: 0.0) + money
        }
        return result
    }
}
