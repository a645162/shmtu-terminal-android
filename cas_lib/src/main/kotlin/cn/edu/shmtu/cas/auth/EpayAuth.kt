package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.auth.common.CookieManager
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.session.LoginChallenge
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 一卡通充值平台认证
 *
 * 对齐 Rust 版本的 session 管理方式：
 * 1. probeLogin() - 探测登录状态
 * 2. prepareChallenge() - 获取 execution + 验证码图片
 * 3. submitLogin() - 提交登录
 * 4. testLoginStatus() - 测试登录状态
 */
class EpayAuth {

    private companion object {
        val log = Logger.getLogger(EpayAuth::class.java.name)
        const val EPAY_BILL_URL = "https://ecard.shmtu.edu.cn/epay/consume/query"
        const val VALIDATE_CODE_ERROR = 401
        const val PASSWORD_ERROR = 402
    }

    private val cookies = CookieManager()
    private val client: OkHttpClient = CasAuth.createClient()
    private var loginUrl: String? = null

    /**
     * 从外部恢复会话 cookies
     */
    fun restoreSession(cookiesJson: String): Result<Unit> {
        return cookies.restore(cookiesJson)
    }

    /**
     * 提取当前 cookies 为 JSON
     */
    fun extractSession(): String {
        return cookies.extract()
    }

    /**
     * 探测登录状态
     */
    suspend fun probeLogin(): Result<SessionProbe> = suspendCoroutine { cont ->
        val url = "$EPAY_BILL_URL?pageNo=1&tabNo=1"

        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cookies.isEmpty()) {
                    addHeader("Cookie", cookies.get())
                }
            }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers.values("Set-Cookie"))

            when (response.code) {
                200 -> {
                    log.info("[EpayAuth] probeLogin: already logged in")
                    cont.resume(Result.success(SessionProbe.AlreadyLoggedIn))
                }
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isEmpty()) {
                        cont.resumeWithException(Exception("重定向URL为空"))
                    } else {
                        this.loginUrl = location
                        log.info("[EpayAuth] probeLogin: need login, loginUrl=$location")
                        cont.resume(Result.success(SessionProbe.NeedLogin(location)))
                    }
                }
                else -> {
                    cont.resumeWithException(Exception("探测登录状态失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[EpayAuth] probeLogin: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    /**
     * 获取 execution 令牌 + 验证码图片
     */
    suspend fun prepareChallenge(): Result<LoginChallenge> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        val (execution, executionSessionId) = CasAuth.getExecution(url, cookies.get())
        if (execution.isBlank()) {
            cont.resumeWithException(Exception("获取 execution 失败"))
            return@suspendCoroutine
        }

        val captchaResult = Captcha.getImageDataFromUrlUsingGet(executionSessionId)
        if (captchaResult == null || captchaResult.first == null) {
            cont.resumeWithException(Exception("获取验证码图片失败"))
            return@suspendCoroutine
        }

        val imageData = captchaResult.first!!
        val captchaSessionId = captchaResult.second

        if (captchaSessionId.isNotBlank()) {
            cookies.addFromSetCookie(captchaSessionId)
        }

        log.info("[EpayAuth] prepareChallenge: execution=${execution.take(30)}..., imageSize=${imageData.size}")
        cont.resume(Result.success(LoginChallenge(execution, imageData)))
    }

    /**
     * 提交登录
     */
    suspend fun submitLogin(
        username: String,
        password: String,
        validateCode: String,
        execution: String
    ): Result<LoginSubmitResult> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        val result = CasAuth.casLogin(url, username, password, validateCode, execution, cookies.get())

        when {
            result.first == 302 -> {
                val redirectUrl = result.second
                val loginCookie = result.third

                if (loginCookie.isNotBlank()) {
                    cookies.restore(loginCookie)
                }

                val redirectResult = CasAuth.casRedirect(redirectUrl, cookies.get())
                if (redirectResult.first == 302) {
                    if (redirectResult.third.isNotBlank()) {
                        cookies.restore(redirectResult.third)
                    }
                    log.info("[EpayAuth] submitLogin: success")
                    cont.resume(Result.success(LoginSubmitResult.Success))
                } else {
                    log.warning("[EpayAuth] submitLogin: redirect failed, code=${redirectResult.first}")
                    cont.resume(Result.success(LoginSubmitResult.Failure("重定向失败")))
                }
            }
            result.first == VALIDATE_CODE_ERROR -> {
                log.info("[EpayAuth] submitLogin: validate code error")
                cont.resume(Result.success(LoginSubmitResult.ValidateCodeError))
            }
            result.first == PASSWORD_ERROR -> {
                log.info("[EpayAuth] submitLogin: password error")
                cont.resume(Result.success(LoginSubmitResult.PasswordError))
            }
            else -> {
                log.warning("[EpayAuth] submitLogin: failure, code=${result.first}")
                cont.resume(Result.success(LoginSubmitResult.Failure(result.third ?: "未知错误")))
            }
        }
    }

    /**
     * 测试是否已登录
     */
    suspend fun testLoginStatus(): Result<Boolean> = suspendCoroutine { cont ->
        val url = "$EPAY_BILL_URL?pageNo=1&tabNo=1"

        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cookies.isEmpty()) {
                    addHeader("Cookie", cookies.get())
                }
            }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers.values("Set-Cookie"))

            when (response.code) {
                200 -> {
                    log.info("[EpayAuth] testLoginStatus: logged in")
                    cont.resume(Result.success(true))
                }
                302 -> {
                    log.info("[EpayAuth] testLoginStatus: not logged in")
                    cont.resume(Result.success(false))
                }
                else -> {
                    cont.resumeWithException(Exception("测试登录状态失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[EpayAuth] testLoginStatus: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    /**
     * 获取账单页面 HTML
     */
    suspend fun getBill(pageNo: Int = 1, tabNo: String = "1"): Result<String> = suspendCoroutine { cont ->
        val url = "$EPAY_BILL_URL?pageNo=$pageNo&tabNo=$tabNo"

        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cookies.isEmpty()) {
                    addHeader("Cookie", cookies.get())
                }
            }
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            cookies.addAllFromSetCookieHeaders(response.headers.values("Set-Cookie"))

            when (response.code) {
                200 -> {
                    val html = response.body?.string() ?: ""
                    log.info("[EpayAuth] getBill: success, htmlLength=${html.length}")
                    cont.resume(Result.success(html))
                }
                302 -> {
                    log.warning("[EpayAuth] getBill: not logged in (302)")
                    cont.resumeWithException(Exception("未登录，需要重新登录"))
                }
                else -> {
                    cont.resumeWithException(Exception("获取账单失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[EpayAuth] getBill: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    // ========== 向后兼容的旧 API ==========

    fun setLoginUrl(loginUrl: String) {
        this.loginUrl = loginUrl
    }

    fun getLoginUrl(): String = loginUrl ?: ""

    fun getEpayCookie(): String = cookies.get()
}
