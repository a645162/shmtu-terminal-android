package com.khm.shmtu.cas.auth.common

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CasAuth {

    companion object {

        fun getExecution(
            url: String = "https://cas.shmtu.edu.cn/cas/login",
            cookie: String = ""
        ): String {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build()

            val response = client.newCall(request).execute()

            val responseCode = response.code

            return if (responseCode == 200) {
                val htmlCode = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? =
                    document.selectFirst("input[name=execution]")
                val value: String = element?.attr("value") ?: ""

                value.trim()
            } else {
                // 处理错误
                println("获取execution失败，状态码：$responseCode")
                ""
            }
        }

        fun casLogin(
            url: String,
            username: String,
            password: String,
            validateCode: String,
            execution: String,
            cookie: String
        ): Triple<Int, String, String> {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val formBody = FormBody.Builder()
                .add("username", username.trim())
                .add("password", password.trim())
                .add("validateCode", validateCode.trim())
                .add("execution", execution.trim())
                .add("_eventId", "submit")
                .add("geolocation", "")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Host", "cas.shmtu.edu.cn")
                .addHeader(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )
                .addHeader("Connection", "keep-alive")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Accept", "*/*")
                .addHeader("Cookie", cookie.trim())
                .post(formBody)
                .build()

            val response =
                client.newCall(request).execute()

            val responseCode = response.code

            return if (responseCode == 302) {
                // 重定向
                val location =
                    response.header("Location") ?: ""
                val newCookie =
                    response.header("Set-Cookie") ?: ""

                Triple(responseCode, location, newCookie)
            } else {
                val htmlCode = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? =
                    document.selectFirst("#loginErrorsPanel")

                val errorText = element?.text() ?: ""
                println("登录失败，错误信息：$errorText")

                if (errorText.contains("account is not recognized")) {
                    println("用户名或密码错误")
                    Triple(
                        CasAuthStatus.PASSWORD_ERROR.code,
                        htmlCode, ""
                    )
                } else if (errorText.contains("reCAPTCHA")) {
                    println("验证码错误")
                    Triple(
                        CasAuthStatus.VALIDATE_CODE_ERROR.code,
                        htmlCode, ""
                    )
                } else {
                    Triple(
                        responseCode,
                        htmlCode, errorText
                    )
                }
            }
        }

        /**
         * 认证成功后用于重定向
         * @param url 重定向地址
         * @param cookie 重定向后的cookie
         */
        fun casRedirect(
            url: String,
            cookie: String
        ): Triple<Int, String, String> {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Cookie", cookie)
                .get()
                .build()

            val response = client.newCall(request).execute()

            val responseCode = response.code

            return if (responseCode == 302) {
                // 重定向
                val location =
                    response.header("Location") ?: ""
                val newCookie =
                    response.header("Set-Cookie") ?: ""

                Triple(responseCode, location, newCookie)
            } else {
                println("请求失败，状态码：$responseCode")
                Triple(responseCode, "", "")
            }
        }

    }

}
