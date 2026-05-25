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
 * 上海海事大学-微信认证
 * 微信相关接口主要使用 wengine_new_ticket
 */
class WechatAuth {

    private companion object {
        val log = Logger.getLogger(WechatAuth::class.java.name)
        const val HOT_WATER_URL = "http://hqzx.shmtu.edu.cn/cellphone/getHotWater"
        const val VALIDATE_CODE_ERROR = 401
        const val PASSWORD_ERROR = 402
    }

    private val cookies = CookieManager()
    private val client: OkHttpClient = CasAuth.createClient()
    private var loginWUrl: String? = null

    fun restoreSession(cookiesJson: String): Result<Unit> = cookies.restore(cookiesJson)
    fun extractSession(): String = cookies.extract()

    /**
     * 探测热水登录状态
     */
    suspend fun probeLogin(): Result<SessionProbe> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
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
                    log.info("[WechatAuth] probeLogin: already logged in")
                    cont.resume(Result.success(SessionProbe.AlreadyLoggedIn))
                }
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isNotBlank()) {
                        this.loginWUrl = location
                        log.info("[WechatAuth] probeLogin: need login, loginWUrl=$location")
                        cont.resume(Result.success(SessionProbe.NeedLogin(location)))
                    } else {
                        cont.resumeWithException(Exception("重定向URL为空"))
                    }
                }
                else -> {
                    cont.resumeWithException(Exception("探测登录状态失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[WechatAuth] probeLogin: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    /**
     * 获取 wengine_new_ticket 和登录 URL（同步版本）
     */
    private fun getWEngineNewTicketSync(): Triple<Int, String, String> {
        val url = loginWUrl ?: return Triple(0, "", "")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        cookies.addAllFromSetCookieHeaders(response.headers.values("Set-Cookie"))

        return if (response.code == 302) {
            val location = response.header("Location") ?: ""
            Triple(response.code, location, cookies.get())
        } else {
            Triple(response.code, "", "")
        }
    }

    /**
     * 获取 execution 令牌 + 验证码图片
     */
    suspend fun prepareChallenge(): Result<LoginChallenge> = suspendCoroutine { cont ->
        val url = loginWUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态"))
            return@suspendCoroutine
        }

        // 获取 CAS 登录 URL
        val ticketResult = getWEngineNewTicketSync()
        if (ticketResult.first != 302) {
            cont.resumeWithException(Exception("获取 wengine ticket 失败，状态码: ${ticketResult.first}"))
            return@suspendCoroutine
        }

        val casLoginUrl = ticketResult.second
        val cookie = ticketResult.third

        val (execution, executionSessionId) = CasAuth.getExecution(casLoginUrl, cookie)
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

        log.info("[WechatAuth] prepareChallenge: execution=${execution.take(30)}..., imageSize=${imageData.size}")
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
        val url = loginWUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态"))
            return@suspendCoroutine
        }

        // 获取 CAS 登录 URL
        val ticketResult = getWEngineNewTicketSync()
        if (ticketResult.first != 302) {
            cont.resumeWithException(Exception("获取 wengine ticket 失败，状态码: ${ticketResult.first}"))
            return@suspendCoroutine
        }

        val casLoginUrl = ticketResult.second
        val cookie = ticketResult.third

        val loginResult = CasAuth.casLogin(casLoginUrl, username, password, validateCode, execution, cookie)

        when {
            loginResult.first == 302 -> {
                val redirectUrl = loginResult.second
                val loginCookie = loginResult.third

                if (loginCookie.isNotBlank()) {
                    cookies.restore(loginCookie)
                }

                val wechatRedirectUrl = "$redirectUrl&from=$HOT_WATER_URL"
                val redirectResponse = CasAuth.casRedirect(wechatRedirectUrl, cookies.get())

                if (redirectResponse.first == 302) {
                    if (redirectResponse.third.isNotBlank()) {
                        cookies.restore(redirectResponse.third)
                    }
                    log.info("[WechatAuth] submitLogin: success")
                    cont.resume(Result.success(LoginSubmitResult.Success))
                } else {
                    log.warning("[WechatAuth] submitLogin: redirect failed")
                    cont.resume(Result.success(LoginSubmitResult.Failure("重定向失败")))
                }
            }
            loginResult.first == VALIDATE_CODE_ERROR -> {
                log.info("[WechatAuth] submitLogin: validate code error")
                cont.resume(Result.success(LoginSubmitResult.ValidateCodeError))
            }
            loginResult.first == PASSWORD_ERROR -> {
                log.info("[WechatAuth] submitLogin: password error")
                cont.resume(Result.success(LoginSubmitResult.PasswordError))
            }
            else -> {
                log.warning("[WechatAuth] submitLogin: failure, code=${loginResult.first}")
                cont.resume(Result.success(LoginSubmitResult.Failure(loginResult.third ?: "未知错误")))
            }
        }
    }

    /**
     * 测试是否已登录
     */
    suspend fun testLoginStatus(): Result<Boolean> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
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
                    log.info("[WechatAuth] testLoginStatus: logged in")
                    cont.resume(Result.success(true))
                }
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isNotBlank()) {
                        this.loginWUrl = location
                    }
                    log.info("[WechatAuth] testLoginStatus: not logged in")
                    cont.resume(Result.success(false))
                }
                else -> {
                    cont.resumeWithException(Exception("测试登录状态失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[WechatAuth] testLoginStatus: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    /**
     * 获取热水数据
     */
    suspend fun getHotWater(): Result<String> = suspendCoroutine { cont ->
        val request = Request.Builder()
            .url(HOT_WATER_URL)
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
                    log.info("[WechatAuth] getHotWater: success, htmlLength=${html.length}")
                    cont.resume(Result.success(html))
                }
                302 -> {
                    val location = response.header("Location") ?: ""
                    if (location.isNotBlank()) {
                        this.loginWUrl = location
                    }
                    cont.resumeWithException(Exception("未登录，需要重新登录"))
                }
                else -> {
                    cont.resumeWithException(Exception("获取热水数据失败，状态码: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            log.warning("[WechatAuth] getHotWater: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    // ========== 向后兼容的旧 API ==========

    fun setLoginWUrl(url: String) { this.loginWUrl = url }
    fun getLoginWUrl(): String = loginWUrl ?: ""
    fun getCookie(): String = cookies.get()
    fun setCookie(cookie: String) { cookies.restore(cookie) }
}
