package cn.edu.shmtu.cas.auth.common

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.logging.Logger

class CasAuth {

    companion object {
        private val log = Logger.getLogger(CasAuth::class.java.name)

        private fun extractCookieName(setCookieValue: String): String {
            val eqIdx = setCookieValue.indexOf('=')
            return if (eqIdx > 0) setCookieValue.substring(0, eqIdx).trim() else ""
        }

        private fun extractCookieNameValue(setCookieValue: String): String {
            val semiIdx = setCookieValue.indexOf(';')
            return if (semiIdx > 0) setCookieValue.substring(0, semiIdx).trim() else setCookieValue.trim()
        }

        fun mergeCookies(existingCookie: String, setCookieHeaders: List<String>): String {
            if (setCookieHeaders.isEmpty()) return existingCookie

            val cookieMap = linkedMapOf<String, String>()

            existingCookie.split(";").map { it.trim() }.filter { it.contains("=") }.forEach {
                val name = it.substringBefore("=").trim()
                val value = it.trim()
                cookieMap[name] = value
            }

            for (setCookie in setCookieHeaders) {
                val name = extractCookieName(setCookie)
                if (name.isNotEmpty()) {
                    cookieMap[name] = extractCookieNameValue(setCookie)
                }
            }

            return cookieMap.values.joinToString("; ")
        }

        fun getExecution(
            url: String = "https://cas.shmtu.edu.cn/cas/login",
            cookie: String = ""
        ): Pair<String, String> {
            log.info("[CasAuth] getExecution: url=$url, cookie=${cookie.take(30)}...")

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
            log.info("[CasAuth] getExecution: responseCode=$responseCode")

            return if (responseCode == 200) {
                val htmlCode = response.body?.string() ?: ""
                log.info("[CasAuth] getExecution: htmlLength=${htmlCode.length}")
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? =
                    document.selectFirst("input[name=execution]")
                val value: String = element?.attr("value") ?: ""

                val jSessionId = response.headers("Set-Cookie")
                    .firstOrNull { it.contains("JSESSIONID") }
                    ?: cookie

                log.info("[CasAuth] getExecution: execution=${value.take(40)}..., jSessionId=${jSessionId.take(40)}...")
                Pair(value.trim(), jSessionId)
            } else {
                log.warning("[CasAuth] getExecution: failed, responseCode=$responseCode")
                Pair("", "")
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
            log.info("[CasAuth] casLogin: url=$url, username=$username, validateCode=$validateCode, execution=${execution.take(30)}..., cookie=${cookie.take(30)}...")

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
            log.info("[CasAuth] casLogin: responseCode=$responseCode")

            return if (responseCode == 302) {
                val location =
                    response.header("Location") ?: ""
                val newCookie = mergeCookies(cookie, response.headers("Set-Cookie"))

                log.info("[CasAuth] casLogin: success (302), location=${location.take(60)}..., newCookie=${newCookie.take(30)}...")
                Triple(responseCode, location, newCookie)
            } else {
                val htmlCode = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(htmlCode)
                val element: Element? =
                    document.selectFirst("#loginErrorsPanel")

                val errorText = element?.text() ?: ""
                log.warning("[CasAuth] casLogin: failed, code=$responseCode, error=$errorText")

                if (errorText.contains("account is not recognized")) {
                    log.warning("[CasAuth] casLogin: password error")
                    Triple(
                        CasAuthStatus.PASSWORD_ERROR.code,
                        htmlCode, ""
                    )
                } else if (errorText.contains("reCAPTCHA")) {
                    log.warning("[CasAuth] casLogin: captcha error")
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

        fun casRedirect(
            url: String,
            cookie: String
        ): Triple<Int, String, String> {
            log.info("[CasAuth] casRedirect: url=${url.take(80)}..., cookie=${cookie.take(30)}...")

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
            log.info("[CasAuth] casRedirect: responseCode=$responseCode")

            return if (responseCode == 302) {
                val location =
                    response.header("Location") ?: ""
                val newCookie = mergeCookies(cookie, response.headers("Set-Cookie"))

                log.info("[CasAuth] casRedirect: success (302), location=${location.take(60)}..., newCookie=${newCookie.take(30)}...")
                Triple(responseCode, location, newCookie)
            } else {
                log.warning("[CasAuth] casRedirect: failed, responseCode=$responseCode")
                Triple(responseCode, "", "")
            }
        }

    }

}
