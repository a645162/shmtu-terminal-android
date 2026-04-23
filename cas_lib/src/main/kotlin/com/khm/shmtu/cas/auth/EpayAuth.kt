package com.khm.shmtu.cas.auth

import com.khm.shmtu.cas.auth.common.CasAuth
import com.khm.shmtu.cas.captcha.Captcha
import okhttp3.OkHttpClient
import okhttp3.Request

class EpayAuth {

    private var _epayCookie = ""
    private var _htmlCode = ""

    private var _loginUrl = ""
    private var _loginCookie = ""

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

        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", finalCookie)
            .get()
            .build()

        val response = client.newCall(request).execute()

        val responseCode = response.code

        return if (responseCode == 200) {
            this._htmlCode = (response.body?.string() ?: "").trim()

            Triple(responseCode, this._htmlCode, cookie)
        } else if (responseCode == 302) {
            val location =
                response.header("Location") ?: ""

            // Get all "Set-Cookie" Header
            val setCookieHeaders: List<String> =
                response.headers.values("Set-Cookie")

            var newCookie = cookie
            for (currentSetCookie in setCookieHeaders) {
                if (currentSetCookie.contains("JSESSIONID")) {
                    newCookie = currentSetCookie
                }
            }

            this._epayCookie = newCookie

            Triple(responseCode, location, newCookie)
        } else {
            Triple(responseCode, "", "")
        }
    }

    fun testLoginStatus(): Boolean {
        val resultBill =
            getBill(cookie = this._epayCookie)

        if (resultBill.first == 200) {
            // OK
            return true
        } else if (resultBill.first == 302) {
            this._loginUrl =
                resultBill.second
            this._epayCookie =
                resultBill.third

            return false
        } else {
            return false
        }
    }

    fun login(
        username: String,
        password: String
    ): Boolean {

        if (this._loginUrl.isBlank() || this._epayCookie.isBlank()) {
            if (testLoginStatus()) {
                return true
            }
        }

        val executionStr =
            CasAuth.getExecution(
                this._loginUrl,
                this._epayCookie
            )

        // 下载验证码
        val resultCaptcha =
            Captcha.getImageDataFromUrlUsingGet(
                cookie = this._loginCookie
            )

        // 检验下载的数据
        if (resultCaptcha == null) {
            println("获取验证码图片失败")
            return false
        }
        val imageData = resultCaptcha.first
        this._loginCookie = resultCaptcha.second
        if (imageData == null) {
            println("获取验证码失败")
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
            println("程序出错，状态码：${resultCas.first}")
            return false
        }

        this._loginCookie = resultCas.third

        val resultRedirect =
            CasAuth.casRedirect(
                resultCas.second,
                this._epayCookie
            )

        if (resultRedirect.first != 302) {
            println("Login Ok,but cannot redirect to bill page.")
            println("Status code：${resultRedirect.first}")
            return false
        }

        val resultBill =
            getBill(cookie = this._epayCookie)

        return resultBill.first == 200
    }

}
