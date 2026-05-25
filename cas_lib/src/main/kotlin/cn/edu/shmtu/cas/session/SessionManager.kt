package cn.edu.shmtu.cas.session

import cn.edu.shmtu.cas.auth.common.CookieManager
import java.util.logging.Logger

/**
 * 会话管理器
 *
 * 管理 CAS 会话的完整生命周期：
 * 1. 探测登录状态 (probe)
 * 2. 获取登录挑战（execution + 验证码）(prepare)
 * 3. 提交登录 (submit)
 * 4. 测试登录状态 (test)
 *
 * 对齐 Rust/C# 版本的设计
 *
 * @property accountId 学号，用于 session 存储的 key
 * @property restoreSession 从持久化恢复会话的回调（可选）
 * @property saveSession 保存会话到持久化的回调（可选）
 */
class SessionManager(
    val accountId: String,
    private val restoreSession: (suspend (String) -> Result<Unit>)? = null,
    private val saveSession: (suspend (String) -> Result<Unit>)? = null
) {
    private companion object {
        val log = Logger.getLogger(SessionManager::class.java.name)
    }

    private val cookies = CookieManager()
    private var loginUrl: String? = null
    private var executionToken: String? = null

    /**
     * 当前 cookies 是否为空
     */
    fun isCookiesEmpty(): Boolean = cookies.isEmpty()

    /**
     * 从外部恢复会话 cookies
     *
     * @param cookiesJson JSON 格式的 cookie 字符串
     * @return Result.success(Unit) 或 Result.failure(error)
     */
    fun restoreCookies(cookiesJson: String): Result<Unit> {
        return cookies.restore(cookiesJson)
    }

    /**
     * 提取当前 cookies 为 JSON
     *
     * @return JSON 格式的 cookie 字符串
     */
    fun extractCookies(): String = cookies.extract()

    /**
     * 获取当前 cookie 字符串
     */
    fun getCookies(): String = cookies.get()

    /**
     * 更新 cookies
     */
    fun updateCookies(cookie: String) {
        cookies.restore(cookie)
    }

    /**
     * 添加单个 Set-Cookie header
     */
    fun addSetCookie(headerVal: String) {
        cookies.addFromSetCookie(headerVal)
    }

    /**
     * 批量添加 Set-Cookie headers
     */
    fun addSetCookies(headers: List<String>) {
        cookies.addAllFromSetCookieHeaders(headers)
    }

    /**
     * 保存会话到持久化存储
     */
    suspend fun persistSession() {
        if (saveSession == null) {
            log.warning("[SessionManager] saveSession callback is null, skip persistence")
            return
        }

        val cookiesJson = extractCookies()
        val result = saveSession.invoke(cookiesJson)
        if (result.isFailure) {
            log.warning("[SessionManager] persistSession failed: ${result.exceptionOrNull()?.message}")
        } else {
            log.info("[SessionManager] persistSession success for account: $accountId")
        }
    }

    /**
     * 从持久化存储恢复会话
     */
    suspend fun loadSession(cookiesJson: String) {
        val result = restoreCookies(cookiesJson)
        if (result.isFailure) {
            log.warning("[SessionManager] loadSession failed: ${result.exceptionOrNull()?.message}")
        } else {
            log.info("[SessionManager] loadSession success for account: $accountId")
        }
    }

    /**
     * 获取最后使用的 loginUrl
     */
    fun getLoginUrl(): String? = loginUrl

    /**
     * 设置 loginUrl
     */
    fun setLoginUrl(url: String) {
        loginUrl = url
        executionToken = null // loginUrl 变了，execution 必须重新获取
    }

    /**
     * 获取 execution token（如果已获取）
     */
    fun getExecution(): String? = executionToken

    /**
     * 设置 execution token
     */
    fun setExecution(execution: String) {
        executionToken = execution
    }

    /**
     * 清除所有会话状态
     */
    fun clear() {
        cookies.clear()
        loginUrl = null
        executionToken = null
    }
}