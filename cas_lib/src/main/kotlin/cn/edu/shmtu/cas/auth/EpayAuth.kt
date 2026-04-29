package cn.edu.shmtu.cas.auth

import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.captcha.Captcha
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.logging.Logger

class EpayAuth {

    private companion object {
        val log = Logger.getLogger(EpayAuth::class.java.name)
    }

    private var _epayCookie = ""
    private var _htmlCode = ""

    private var _loginUrl = ""
    private var _loginCookie = ""
    private var _execution = ""

    fun setLoginUrl(loginUrl: String) {
        this._loginUrl = loginUrl
    }

    fun setLoginCookie(cookie: String) {
        this._loginCookie = cookie
    }

    fun setEpayCookie(cookie: String) {
        this._epayCookie = cookie
    }

    fun setExecution(execution: String) {
        this._execution = execution
    }

    fun getLoginUrl(): String = _loginUrl
    fun getLoginCookie(): String = _loginCookie
    fun getEpayCookie(): String = _epayCookie
    fun getExecution(): String = _execution

    fun getBill(
        pageNo: String = "1",
        tabNo: String = "1",
        cookie: String = ""
    ): Triple<Int, String, String> {
        val url =
            "https://ecard.shmtu.edu.cn/epay/consume/query" +
                    "?pageNo=$pageNo" +
                    "&tabNo=$tabNo"

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val finalCookie = cookie.ifBlank {
            this._epayCookie
        }

        log.info("[EpayAuth] getBill: url=$url, cookie=${finalCookie.take(30)}...")

        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", finalCookie)
            .get()
            .build()

        val response = client.newCall(request).execute()

        val responseCode = response.code
        log.info("[EpayAuth] getBill: responseCode=$responseCode")

        return if (responseCode == 200) {
            this._htmlCode = (response.body?.string() ?: "").trim()
            log.info("[EpayAuth] getBill: success, htmlLength=${_htmlCode.length}")
            Triple(responseCode, this._htmlCode, cookie)
        } else if (responseCode == 302) {
            val location =
                response.header("Location") ?: ""

            val setCookieHeaders: List<String> =
                response.headers.values("Set-Cookie")

            var newCookie = cookie
            for (currentSetCookie in setCookieHeaders) {
                if (currentSetCookie.contains("JSESSIONID")) {
                    newCookie = currentSetCookie
                }
            }

            this._epayCookie = newCookie
            log.info("[EpayAuth] getBill: 302 redirect, location=$location, newCookie=${newCookie.take(30)}...")

            Triple(responseCode, location, newCookie)
        } else {
            log.warning("[EpayAuth] getBill: unexpected code=$responseCode")
            Triple(responseCode, "", "")
        }
    }

    fun testLoginStatus(): Boolean {
        log.info("[EpayAuth] testLoginStatus: start, epayCookie=${_epayCookie.take(30)}...")
        val resultBill =
            getBill(cookie = this._epayCookie)

        if (resultBill.first == 200) {
            log.info("[EpayAuth] testLoginStatus: already logged in (200)")
            return true
        } else if (resultBill.first == 302) {
            this._loginUrl =
                resultBill.second
            this._epayCookie =
                resultBill.third
            log.info("[EpayAuth] testLoginStatus: not logged in (302), loginUrl=${_loginUrl.take(60)}..., epayCookie=${_epayCookie.take(30)}...")
            log.info("[EpayAuth] testLoginStatus: preserved loginCookie=${_loginCookie.take(30)}..., execution=${_execution.take(30)}...")

            return false
        } else {
            log.warning("[EpayAuth] testLoginStatus: unexpected code=${resultBill.first}")
            return false
        }
    }

    fun login(
        username: String,
        password: String
    ): Boolean {
        log.info("[EpayAuth] login: start, username=$username")

        if (this._loginUrl.isBlank() || this._epayCookie.isBlank()) {
            if (testLoginStatus()) {
                log.info("[EpayAuth] login: already logged in via testLoginStatus")
                return true
            }
        }

        log.info("[EpayAuth] login: getting execution, loginUrl=${_loginUrl.take(60)}..., epayCookie=${_epayCookie.take(30)}...")
        val executionStr =
            CasAuth.getExecution(
                this._loginUrl,
                this._epayCookie
            )
        log.info("[EpayAuth] login: execution=$executionStr")

        // 下载验证码
        val resultCaptcha =
            Captcha.getImageDataFromUrlUsingGet(
                cookie = this._loginCookie
            )

        // 检验下载的数据
        if (resultCaptcha == null) {
            log.warning("[EpayAuth] login: failed to get captcha image data")
            return false
        }
        val imageData = resultCaptcha.first
        this._loginCookie = resultCaptcha.second
        log.info("[EpayAuth] login: captcha downloaded, loginCookie=${_loginCookie.take(30)}...")
        if (imageData == null) {
            log.warning("[EpayAuth] login: captcha image data is null")
            return false
        }

        // 调用远端识别接口
        val validateCode: String =
            Captcha.ocrByRemoteTcpServer(
                "127.0.0.1", 21601,
                imageData
            )
        val exprResult =
            Captcha.getExprResultByExprString(validateCode)
        log.info("[EpayAuth] login: OCR result='$validateCode', exprResult='$exprResult'")

        val resultCas =
            CasAuth.casLogin(
                this._loginUrl,
                username,
                password,
                exprResult,
                executionStr,
                this._loginCookie
            )

        if (resultCas.first != 302) {
            log.warning("[EpayAuth] login: CAS login failed, code=${resultCas.first}")
            return false
        }

        this._loginCookie = resultCas.third
        log.info("[EpayAuth] login: CAS login success (302), redirectUrl=${resultCas.second.take(60)}..., loginCookie=${_loginCookie.take(30)}...")

        val resultRedirect =
            CasAuth.casRedirect(
                resultCas.second,
                this._epayCookie
            )

        if (resultRedirect.first != 302) {
            log.warning("[EpayAuth] login: CAS redirect failed, code=${resultRedirect.first}")
            return false
        }

        log.info("[EpayAuth] login: CAS redirect success (302), location=${resultRedirect.second.take(60)}...")

        val resultBill =
            getBill(cookie = this._epayCookie)

        val success = resultBill.first == 200
        log.info("[EpayAuth] login: final getBill result code=${resultBill.first}, success=$success")
        return success
    }

    fun loginWithCaptcha(
        username: String,
        password: String,
        captchaCode: String
    ): Boolean {
        log.info("[EpayAuth] loginWithCaptcha: start, username=$username, captchaCode=$captchaCode")
        log.info("[EpayAuth] loginWithCaptcha: initial state, loginUrl=${_loginUrl.take(60)}..., epayCookie=${_epayCookie.take(30)}..., loginCookie=${_loginCookie.take(30)}..., execution=${_execution.take(30)}...")

        if (_loginUrl.isBlank() || _epayCookie.isBlank()) {
            log.info("[EpayAuth] loginWithCaptcha: loginUrl or epayCookie is blank, calling testLoginStatus")
            if (testLoginStatus()) {
                log.info("[EpayAuth] loginWithCaptcha: already logged in via testLoginStatus")
                return true
            }
            log.info("[EpayAuth] loginWithCaptcha: after testLoginStatus, loginUrl=${_loginUrl.take(60)}..., epayCookie=${_epayCookie.take(30)}..., loginCookie=${_loginCookie.take(30)}..., execution=${_execution.take(30)}...")
        }

        log.info("[EpayAuth] loginWithCaptcha: fetching execution from CAS")
        _execution = CasAuth.getExecution(_loginUrl, _epayCookie)
        log.info("[EpayAuth] loginWithCaptcha: got execution=${_execution.take(40)}...")

        log.info("[EpayAuth] loginWithCaptcha: calling casLogin with url=${_loginUrl.take(60)}..., execution=${_execution.take(30)}..., loginCookie=${_loginCookie.take(30)}...")

        val resultCas = CasAuth.casLogin(
            _loginUrl,
            username,
            password,
            captchaCode,
            _execution,
            _loginCookie
        )

        log.info("[EpayAuth] loginWithCaptcha: casLogin result code=${resultCas.first}, location=${resultCas.second.take(60)}..., cookie=${resultCas.third.take(30)}...")

        if (resultCas.first != 302) {
            log.warning("[EpayAuth] loginWithCaptcha: CAS login failed, code=${resultCas.first}, body=${resultCas.second.take(200)}...")
            return false
        }

        _loginCookie = resultCas.third
        _execution = ""

        log.info("[EpayAuth] loginWithCaptcha: CAS login success, calling casRedirect with url=${resultCas.second.take(60)}..., epayCookie=${_epayCookie.take(30)}...")

        val resultRedirect = CasAuth.casRedirect(
            resultCas.second,
            _epayCookie
        )

        log.info("[EpayAuth] loginWithCaptcha: casRedirect result code=${resultRedirect.first}, location=${resultRedirect.second.take(60)}..., cookie=${resultRedirect.third.take(30)}...")

        if (resultRedirect.first != 302) {
            log.warning("[EpayAuth] loginWithCaptcha: CAS redirect failed, code=${resultRedirect.first}")
            return false
        }

        val resultBill = getBill(cookie = _epayCookie)
        val success = resultBill.first == 200
        log.info("[EpayAuth] loginWithCaptcha: final getBill code=${resultBill.first}, success=$success")
        return success
    }

}
