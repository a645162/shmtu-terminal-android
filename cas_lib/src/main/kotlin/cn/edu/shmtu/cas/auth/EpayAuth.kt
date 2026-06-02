package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.auth.common.CookieManager
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaResolver
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
 * 对齐 C# 版本的设计模式：
 * 1. probeLogin() - 探测登录状态
 * 2. prepareChallenge() - 获取 execution + 验证码图片（断点：可在此暂停，由外部处理验证码）
 * 3. submitLogin(user, pwd, code, execution) - 提交登录（手动路径）
 * 4. submitLogin(user, pwd) - 一键登录（自动路径，使用注入的 CaptchaResolver）
 * 5. testLoginStatus() - 测试登录状态
 *
 * 两种使用方式：
 * - **自动路径**：构造时注入 [CaptchaResolver]，调用 `submitLogin(username, password)` 自动完成验证码解析
 * - **手动路径**：调用 `prepareChallenge()` 获取验证码图片，由外部（UI/CLI/SpringBoot）处理后再调用 `submitLogin(username, password, validateCode, execution)`
 *
 * @param captchaResolver 验证码解析策略（可选），为 null 时仅支持手动路径
 */
class EpayAuth(
    private val captchaResolver: CaptchaResolver? = null
) {

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
     * 尝试复用 TGC（Ticket Granting Cookie）
     *
     * 当 probeLogin 返回 NeedLogin 后，CAS 仍可能通过 TGC 自动认证。
     * 若 TGC 有效，CAS 会自动跟随重定向，此时无需提交登录表单。
     * 对齐 C# 版本的 TryReuseTgcAsync。
     */
    suspend fun tryReuseTgc(): Result<Boolean> = suspendCoroutine { cont ->
        val url = loginUrl ?: run {
            cont.resumeWithException(Exception("尚未探测登录状态，请先调用 probeLogin"))
            return@suspendCoroutine
        }

        try {
            val (execution, _) = CasAuth.getExecution(url, cookies.get())
            if (execution.isEmpty()) {
                log.info("[EpayAuth] tryReuseTgc: TGC valid, CAS auto-authenticated")
                cont.resume(Result.success(true))
            } else {
                log.info("[EpayAuth] tryReuseTgc: TGC invalid, need manual login")
                cont.resume(Result.success(false))
            }
        } catch (e: Exception) {
            log.warning("[EpayAuth] tryReuseTgc: exception: ${e.message}")
            cont.resume(Result.failure(e))
        }
    }

    /**
     * 获取 execution 令牌 + 验证码图片
     *
     * 这是"断点"方法——调用后流程暂停，由外部决定如何处理验证码：
     * - 显示给用户手动输入
     * - 调用 OCR 服务自动识别
     * - 传给 SpringBoot 的 REST API 让前端处理
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
     * 提交登录（手动路径）
     *
     * 由外部提供验证码答案，适用于 UI 交互、SpringBoot REST API 等场景。
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
     * 一键登录（自动路径）
     *
     * 内部自动完成：tryReuseTgc → prepareChallenge → resolve → intoFinalAnswer → submitLogin，
     * 若验证码错误则自动重试最多 [maxRetries] 次。
     * 对齐 C# 版本的 SubmitLoginAsync(username, password)。
     *
     * 需要构造时注入 [CaptchaResolver]，否则抛出 IllegalStateException。
     *
     * 重试循环在库内部完成——调用方只需处理最终结果。
     */
    suspend fun submitLogin(
        username: String,
        password: String,
        maxRetries: Int = 5
    ): Result<LoginSubmitResult> {
        val resolver = captchaResolver
            ?: return Result.failure(IllegalStateException("未设置 CaptchaResolver，请使用构造函数注入或调用 submitLogin(username, password, validateCode, execution)"))

        // 1. 尝试 TGC 复用
        val tgcResult = tryReuseTgc()
        if (tgcResult.isSuccess && tgcResult.getOrThrow()) {
            val testResult = testLoginStatus()
            if (testResult.isSuccess && testResult.getOrThrow()) {
                log.info("[EpayAuth] submitLogin: TGC reuse success")
                return Result.success(LoginSubmitResult.Success)
            }
            log.warning("[EpayAuth] submitLogin: TGC authenticated but session invalid, fallback to login form")
        }

        // 2. 重试循环
        var lastResult: Result<LoginSubmitResult>? = null

        for (attempt in 1..maxRetries) {
            log.info("[EpayAuth] submitLogin: attempt $attempt/$maxRetries")

            val challengeResult = prepareChallenge()
            if (challengeResult.isFailure) {
                log.warning("[EpayAuth] submitLogin: prepareChallenge failed: ${challengeResult.exceptionOrNull()?.message}")
                lastResult = Result.failure(challengeResult.exceptionOrNull() ?: Exception("获取 challenge 失败"))
                continue
            }
            val challenge = challengeResult.getOrThrow()

            val resolveResult = resolver.resolve(challenge.captchaImage)
            if (resolveResult.isFailure) {
                log.warning("[EpayAuth] submitLogin: resolve failed: ${resolveResult.exceptionOrNull()?.message}")
                lastResult = Result.failure(resolveResult.exceptionOrNull() ?: Exception("验证码解析失败"))
                continue
            }
            val captchaAnswer = resolveResult.getOrThrow()

            val finalAnswer = captchaAnswer.intoFinalAnswer()
            log.info("[EpayAuth] submitLogin: captcha value='${finalAnswer.value}', kind=${finalAnswer.kind}")

            val submitResult = submitLogin(username, password, finalAnswer.value, challenge.execution)
            if (submitResult.isFailure) {
                log.warning("[EpayAuth] submitLogin: submitLogin failed: ${submitResult.exceptionOrNull()?.message}")
                lastResult = submitResult
                continue
            }

            val loginResult = submitResult.getOrThrow()
            when (loginResult) {
                is LoginSubmitResult.Success -> {
                    log.info("[EpayAuth] submitLogin: success on attempt $attempt")
                    return Result.success(LoginSubmitResult.Success)
                }
                is LoginSubmitResult.ValidateCodeError -> {
                    log.info("[EpayAuth] submitLogin: validate code error, will retry")
                    lastResult = Result.success(LoginSubmitResult.ValidateCodeError)
                    continue
                }
                is LoginSubmitResult.PasswordError -> {
                    log.info("[EpayAuth] submitLogin: password error, abort")
                    return Result.success(LoginSubmitResult.PasswordError)
                }
                is LoginSubmitResult.Failure -> {
                    log.warning("[EpayAuth] submitLogin: failure: ${loginResult.message}")
                    lastResult = Result.success(loginResult)
                    continue
                }
            }
        }

        log.warning("[EpayAuth] submitLogin: all $maxRetries attempts exhausted")
        return lastResult ?: Result.failure(Exception("登录重试次数耗尽"))
    }

    /**
     * 使用指定 CaptchaResolver 一键登录
     *
     * 保留向后兼容：允许临时传入 resolver 而非构造函数注入。
     * 内部逻辑与 [submitLogin] 一致。
     */
    suspend fun submitLoginWithResolver(
        username: String,
        password: String,
        captchaResolver: CaptchaResolver,
        maxRetries: Int = 5
    ): Result<LoginSubmitResult> {
        return EpayAuth(captchaResolver).also {
            it.loginUrl = this.loginUrl
            it.cookies.restore(this.cookies.extract())
        }.submitLogin(username, password, maxRetries)
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
