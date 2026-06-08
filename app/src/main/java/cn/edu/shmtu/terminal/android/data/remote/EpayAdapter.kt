package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.util.Log
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.classifier.BillCategory
import cn.edu.shmtu.cas.classifier.BillClassifier
import cn.edu.shmtu.cas.classifier.MealClassifier
import cn.edu.shmtu.cas.classifier.PositionInfo
import cn.edu.shmtu.cas.classifier.PositionTranslator
import cn.edu.shmtu.cas.datatype.BillType
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpayAdapter @Inject constructor(
    private val secureStorage: SecureStorage,
    @param:ApplicationContext private val context: Context,
    /** 由 Hilt 注入 [BillDatabaseManager] 供 [cn.edu.shmtu.terminal.android.data.sync.RoomBillStore] 使用 */
    val billDbManager: BillDatabaseManager,
    /** 由 Hilt 注入 [BillRulesManager], 走 filesDir/bill/ 本地缓存(缺失回退到 assets/bill/) */
    private val billRulesManager: BillRulesManager,
) {
    private val TAG = "EpayAdapter"

    private val instances = mutableMapOf<Long, EpayAuth>()

    /**
     * 加载策略: 多层 fallback + 自检 (避免静默加载到空规则导致全部 MISS)。
     */
    private val TAG_PT = "PositionTranslator"
    private val TAG_CL = "BillClassifier"
    private val MIN_POSITION_KEYWORDS = 15
    private val MIN_CLASSIFIER_RULES = 5
    private val MIN_MEAL_RULES = 1

    data class LoadedClassifier(
        val classifier: BillClassifier?,
        val source: String,
        val ruleCount: Int
    )

    data class LoadedPositionTranslator(
        val translator: PositionTranslator?,
        val source: String,
        val keywordCount: Int
    )

    data class LoadedMealClassifier(
        val classifier: MealClassifier,
        val source: String,
        val scheduleCount: Int
    )

    /**
     * 账单分类器（懒加载,多层 fallback + 自检）。
     * 优先 `rules.toml` 的 [type] 段(13 条),回退到 `type.toml`(12 条)。
     */
    fun loadClassifier(): LoadedClassifier {
        return try {
            val rulesToml = runCatching { billRulesManager.readFile("rules.toml") }.getOrNull()
            val typeToml = runCatching { billRulesManager.readFile("type.toml") }.getOrNull()
            val (text, source) = when {
                rulesToml != null -> rulesToml to billRulesManager.activeSource("rules.toml")
                typeToml != null -> typeToml to billRulesManager.activeSource("type.toml")
                else -> null to "(none)"
            }
            if (text == null) {
                Log.e(TAG_CL, "ALL sources missing. rulesToml=${rulesToml != null} typeToml=${typeToml != null}")
                return LoadedClassifier(null, "(none)", 0)
            }
            val clf = BillClassifier.fromRulesToml(text)
            val ruleCount = clf.ruleCount()
            Log.d(TAG_CL, "from $source → $ruleCount rules (localRules=${billRulesManager.hasLocalFile("rules.toml")}, localType=${billRulesManager.hasLocalFile("type.toml")})")
            if (ruleCount < MIN_CLASSIFIER_RULES) {
                Log.e(TAG_CL, "Rule count <$MIN_CLASSIFIER_RULES ($ruleCount). 分类器基本无效,所有 bill 都会 fall through 到 'other'")
            } else {
                Log.i(TAG_CL, "OK: $ruleCount rules loaded from $source")
            }
            LoadedClassifier(if (ruleCount == 0) null else clf, source, ruleCount)
        } catch (e: Exception) {
            Log.e(TAG_CL, "Failed to load bill classifier: ${e.message}", e)
            LoadedClassifier(null, "ERROR: ${e.message}", 0)
        }
    }

    val classifier: BillClassifier?
        get() = loadClassifier().classifier

    /**
     * 位置翻译器（懒加载）。
     *
     * 加载策略(对齐 Tauri `DatabaseFileManager::read_file` + 多层自检):
     *   1) `position.toml`  — 单文件覆盖范围最全(包含 22 条规则)
     *   2) `rules.toml`     — 合并文件中的 [position] 段(17+ 条,作为回退)
     *   3) assets `position.toml` — 出厂默认
     *
     * 每层解析后都会校验 keyword 数。若最终 keyword 数 == 0,会打印 ERROR 级别的 log,
     * 不再被静默吞掉(这是导致历史账单全部 MISS 的根因)。
     */
    fun loadPositionTranslator(): LoadedPositionTranslator {
        return try {
            val localPos = runCatching { billRulesManager.readFile("position.toml") }.getOrNull()
            val localRules = runCatching { billRulesManager.readFile("rules.toml") }.getOrNull()
            val (text, source) = when {
                localPos != null -> localPos to billRulesManager.activeSource("position.toml")
                localRules != null -> localRules to billRulesManager.activeSource("rules.toml")
                else -> null to "(none)"
            }
            if (text == null) {
                Log.e(TAG_PT, "ALL sources missing. localPos=${localPos != null} localRules=${localRules != null}")
                return LoadedPositionTranslator(null, "(none)", 0)
            }
            var tr = PositionTranslator.fromRulesToml(text)
            var keywordCount = tr.getAllKeywords().size
            Log.d(TAG_PT, "from $source → $keywordCount keywords (localPos=${billRulesManager.hasLocalFile("position.toml")}, localRules=${billRulesManager.hasLocalFile("rules.toml")})")
            // 自检: 如果该层拿到的 keywords 太可疑,降级到下一层
            if (keywordCount < MIN_POSITION_KEYWORDS && localPos != null && localRules != null) {
                Log.w(TAG_PT, "local position.toml 仅 $keywordCount keywords,降级用 rules.toml")
                tr = PositionTranslator.fromRulesToml(localRules)
                keywordCount = tr.getAllKeywords().size
                Log.d(TAG_PT, "from ${billRulesManager.activeSource("rules.toml")} → $keywordCount keywords")
            }
            if (keywordCount < MIN_POSITION_KEYWORDS) {
                Log.e(TAG_PT, "ALL local sources yield <$MIN_POSITION_KEYWORDS keywords ($keywordCount). " +
                        "Position translation will be DISABLED — 所有 bill 全部 MISS! " +
                        "可能原因: 本地 TOML 损坏 / 网络从未同步过 / 解析器 bug")
            } else {
                Log.i(TAG_PT, "OK: $keywordCount keywords loaded from $source")
            }
            LoadedPositionTranslator(if (keywordCount == 0) null else tr, source, keywordCount)
        } catch (e: Exception) {
            Log.e(TAG_PT, "Failed to load position translator: ${e.message}", e)
            LoadedPositionTranslator(null, "ERROR: ${e.message}", 0)
        }
    }

    val positionTranslator: PositionTranslator?
        get() = loadPositionTranslator().translator

    fun loadMealClassifier(): LoadedMealClassifier {
        return try {
            val rulesToml = runCatching { billRulesManager.readFile("rules.toml") }.getOrNull()
            val scheduleToml = runCatching { billRulesManager.readFile("schedule.toml") }.getOrNull()
            val (text, source) = when {
                rulesToml != null -> rulesToml to billRulesManager.activeSource("rules.toml")
                scheduleToml != null -> scheduleToml to billRulesManager.activeSource("schedule.toml")
                else -> null to "(defaultRules)"
            }
            val classifier = if (text != null) {
                MealClassifier.fromRulesToml(text)
            } else {
                MealClassifier.defaultRules()
            }
            val scheduleCount = classifier.ruleCount()
            if (scheduleCount < MIN_MEAL_RULES) {
                Log.e(TAG, "MealClassifier ruleCount <$MIN_MEAL_RULES ($scheduleCount)")
            } else {
                Log.i(TAG, "MealClassifier OK: $scheduleCount schedules loaded from $source")
            }
            LoadedMealClassifier(classifier, source, scheduleCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load meal classifier: ${e.message}", e)
            val fallback = MealClassifier.defaultRules()
            LoadedMealClassifier(fallback, "defaultRules(ERROR: ${e.message})", fallback.ruleCount())
        }
    }

    /**
     * (重复声明已删除 — 真正的 classifier 在更上面)
     */

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
     * 拉取一卡通个人账户页面 HTML。
     *
     * cookies 过期时自动重新登录（OCR 自动模式），最多重试 3 次。
     * 登录成功后自动保存新 cookies 到 SecureStorage。
     */
    suspend fun fetchPersonAccountHtml(accountId: Long): Result<String> = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).getPersonAccountHtml()

        if (result.isSuccess) {
            Log.d(TAG, "fetchPersonAccountHtml account=$accountId success")
            return@withContext result
        }

        // session 过期 → 尝试用已存 cookies 重新登录
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        if (!errorMsg.contains("未登录") && !errorMsg.contains("302") && !errorMsg.contains("re-login")) {
            Log.w(TAG, "fetchPersonAccountHtml account=$accountId failed: $errorMsg")
            return@withContext result
        }

        Log.w(TAG, "fetchPersonAccountHtml: session expired, re-logging in...")
        // 清除旧 session, 后续由上层 (HomeViewModel / IdentityDetailViewModel) 通过
        // 常规的 login/probe 流程重新登录; 这里直接返回错误让上层处理
        invalidateSession(accountId)
        Result.failure(Exception("SESSION_EXPIRED: 会话已失效，请重新登录"))
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
