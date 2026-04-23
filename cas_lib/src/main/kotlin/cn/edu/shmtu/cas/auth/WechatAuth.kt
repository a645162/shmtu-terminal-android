package com.khm.shmtu.cas.auth

import com.khm.shmtu.cas.auth.common.CasAuth
import com.khm.shmtu.cas.captcha.Captcha
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 上海海事大学-微信认证
 * 微信相关接口主要使用wengine_new_ticket
 */
class WechatAuth {

    private var savedCookie: String = ""

    private var loginWUrl: String = ""

    fun getHotWater(
        cookie: String = ""
    ): Triple<Int, String, String> {

        val currentCookie =
            cookie.ifBlank {
                savedCookie
            }

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder()
            .url("http://hqzx.shmtu.edu.cn/cellphone/getHotWater")
            .addHeader("Cookie", currentCookie)
            .build()

        val response = client.newCall(request).execute()

        val responseCode = response.code

        return if (response.isSuccessful) {
            val responseText = response.body?.string() ?: ""
            Triple(responseCode, responseText, "")
        } else {
            if (responseCode == 302) {
                // 重定向
                val location = response.header("Location") ?: ""
                val newCookie = response.header("Set-Cookie") ?: ""
                Triple(responseCode, location, newCookie)
            } else {
                // 处理错误
                println("请求失败，状态码：$responseCode")
                Triple(responseCode, "", "")
            }
        }
    }


    private fun getWEngineNewTicket(
        url: String
    ): Triple<Int, String, String> {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()

        val responseCode = response.code

        return if (responseCode == 302) {
            // 重定向
            val location = response.header("Location") ?: ""
            val cookie_new_url = response.header("Set-Cookie") ?: ""
            Triple(responseCode, location, cookie_new_url)
        } else {
            // 处理错误
            println("请求失败，状态码：$responseCode")
            Triple(responseCode, "", "")
        }
    }

    fun testLoginStatus(): Boolean {
        val resultHotWater =
            getHotWater(savedCookie)
        if (resultHotWater.first == 200) {
            // Ok
            return true
        } else {
            if (resultHotWater.first == 302) {
                this.loginWUrl = resultHotWater.second
            }
            return false
        }
    }

    fun login(
        username: String,
        password: String
    ): Boolean {

        if (username.isBlank() || password.isBlank()) {
            println("用户名或密码为空")
            return false
        }

        if (loginWUrl.isBlank()) {
            if (testLoginStatus()) {
                return true
            }
        }

        val resultWEngineNewTicket =
            getWEngineNewTicket(this.loginWUrl)

        if (resultWEngineNewTicket.first != 302) {
            println("程序出错，状态码：${resultWEngineNewTicket.first}")
            return false
        }

        val executionStr = CasAuth.getExecution(
            resultWEngineNewTicket.second,
            resultWEngineNewTicket.third
        )

        val resultCaptcha =
            Captcha.getImageDataFromUrlUsingGet()

        if (resultCaptcha == null) {
            println("获取验证码失败")
            return false
        }

        val imageData = resultCaptcha.first
        val jSessionId = resultCaptcha.second

        if (imageData == null) {
            println("获取验证码失败")
            return false
        }

        val validateCode: String =
            Captcha.ocrByRemoteTcpServer(
                "127.0.0.1", 21601,
                imageData
            )
        val exprResult =
            Captcha.getExprResultByExprString(validateCode)

        val resultCas =
            CasAuth.casLogin(
                resultWEngineNewTicket.second,
                username,
                password,
                exprResult,
                executionStr,
                jSessionId
            )

        if (resultCas.first != 302) {
            println("程序出错，状态码：${resultCas.first}")
            return false
        }

        val wechatAuthResult =
            CasAuth.casRedirect(
                resultCas.second + "&from=http://hqzx.shmtu.edu.cn/cellphone/getHotWater",
                resultCas.third
            )

        if (wechatAuthResult.first != 302) {
            println("程序出错，状态码：${wechatAuthResult.first}")
            return false
        }

        this.savedCookie = wechatAuthResult.third

        val loginResult = testLoginStatus()

        println("登录结果：$loginResult")

        return loginResult
    }

}
