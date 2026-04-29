package cn.edu.shmtu.cas.captcha

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.net.SocketTimeoutException
import java.io.BufferedInputStream
import java.util.logging.Logger

class Captcha {

    companion object {
        private val log = Logger.getLogger(Captcha::class.java.name)

        var ocrHost: String = "127.0.0.1"
        var ocrPort: Int = 21601

        fun validateIPAddress(ip: String): Boolean {
            val ipAddressPattern = Regex(
                "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                        "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
            )
            return ipAddressPattern.matches(ip)
        }

        fun validatePort(port: String): Boolean {
            val integerPort = port.toIntOrNull()
            return integerPort != null && validatePort(integerPort)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        fun getImageDataFromUrl(
            imageUrl: String = "https://cas.shmtu.edu.cn/cas/captcha"
        ): ByteArray {
            val url = URL(imageUrl)
            val inputStream = BufferedInputStream(url.openStream())
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            return outputStream.toByteArray()
        }

        fun getImageDataFromUrlUsingGet(
            cookie: String? = null
        ): Pair<ByteArray?, String>? {
            val imageUrl = "https://cas.shmtu.edu.cn/cas/captcha"

            val client = OkHttpClient.Builder()
                .build()

            val requestBuilder = Request.Builder()
                .url(imageUrl)
                .get()

            if (!cookie.isNullOrBlank()) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    log.warning("[Captcha] getImageDataFromUrlUsingGet: request failed, code=${response.code}")
                    return null
                }

                val returnCookie =
                    response.headers["Set-Cookie"] ?: (cookie ?: "")

                log.info("[Captcha] getImageDataFromUrlUsingGet: success, cookie=${returnCookie.take(30)}...")
                return Pair(response.body?.bytes(), returnCookie)
            } catch (e: IOException) {
                log.warning("[Captcha] getImageDataFromUrlUsingGet: IOException: ${e.message}")
                return null
            }
        }

        fun ocrByRemoteTcpServer(
            host: String, port: Int,
            imageData: ByteArray
        ): String {
            log.info("[Captcha] ocrByRemoteTcp: connecting to $host:$port, imageData size: ${imageData.size} bytes")
            try {
                Socket(host, port).use { socket ->
                    socket.soTimeout = 10000
                    log.info("[Captcha] ocrByRemoteTcp: socket connected, remote=${socket.remoteSocketAddress}")

                    val outputStream = socket.getOutputStream()
                    val dataOutputStream = DataOutputStream(outputStream)

                    dataOutputStream.write(imageData)
                    dataOutputStream.flush()

                    val endMarker = "<END>".toByteArray(Charsets.UTF_8)
                    outputStream.write(endMarker)
                    outputStream.flush()
                    log.info("[Captcha] ocrByRemoteTcp: data sent (${imageData.size} + ${endMarker.size} bytes), waiting...")

                    try {
                        val inputStream = socket.getInputStream()
                        val response = inputStream.readBytes().toString(Charsets.UTF_8)
                        log.info("[Captcha] ocrByRemoteTcp: response='$response' (length=${response.length})")
                        return response
                    } catch (e: SocketTimeoutException) {
                        log.warning("[Captcha] ocrByRemoteTcp: socket timeout while reading response")
                        return ""
                    }
                }
            } catch (e: Exception) {
                log.warning("[Captcha] ocrByRemoteTcp: connection failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }

        fun ocrByRemoteTcpServerAutoRetry(
            host: String, port: Int,
            imageData: ByteArray,
            retryTimes: Int = 3
        ): String {
            var result: String = ""

            log.info("[Captcha] ocrAutoRetry: host=$host, port=$port, retryTimes=$retryTimes, imageData size=${imageData.size}")

            for (i in 1..retryTimes) {
                log.info("[Captcha] ocrAutoRetry: attempt $i/$retryTimes")
                try {
                    result = ocrByRemoteTcpServer(host, port, imageData)
                    log.info("[Captcha] ocrAutoRetry: attempt $i result='$result'")
                } catch (e: Exception) {
                    log.warning("[Captcha] ocrAutoRetry: attempt $i failed: ${e.javaClass.simpleName}: ${e.message}")
                    continue
                }

                if (result.isNotEmpty()) {
                    log.info("[Captcha] ocrAutoRetry: success on attempt $i")
                    break
                } else {
                    log.warning("[Captcha] ocrAutoRetry: attempt $i returned empty, will retry")
                }
            }

            if (result.isEmpty()) {
                log.warning("[Captcha] ocrAutoRetry: all $retryTimes attempts failed")
            }

            return result
        }

        fun getExprResultByExprString(expr: String): String {
            val index = expr.indexOf("=")
            if (index != -1) {
                val result = expr.substring(index + 1).trim()
                return result
            }
            return ""
        }

    }

}
