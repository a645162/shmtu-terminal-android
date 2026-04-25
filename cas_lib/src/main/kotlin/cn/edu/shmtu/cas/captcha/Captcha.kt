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

class Captcha {

    companion object {

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
                    println("请求失败，状态码：${response.code}")
                    return null
                }

                val returnCookie =
                    response.headers["Set-Cookie"] ?: (cookie ?: "")

                return Pair(response.body?.bytes(), returnCookie)
            } catch (e: IOException) {
                println("请求失败：${e.message}")
                return null
            }
        }

        fun ocrByRemoteTcpServer(
            host: String, port: Int,
            imageData: ByteArray
        ): String {
            println("[RemoteOCR] Connecting to $host:$port, imageData size: ${imageData.size} bytes")
            try {
                Socket(host, port).use { socket ->
                    socket.soTimeout = 10000
                    println("[RemoteOCR] Socket connected, remote address: ${socket.remoteSocketAddress}")

                    val outputStream = socket.getOutputStream()
                    val dataOutputStream = DataOutputStream(outputStream)

                    dataOutputStream.write(imageData)
                    dataOutputStream.flush()

                    val endMarker = "<END>".toByteArray(Charsets.UTF_8)
                    outputStream.write(endMarker)
                    outputStream.flush()
                    println("[RemoteOCR] Image data sent (${imageData.size} + ${endMarker.size} bytes), waiting for response...")

                    try {
                        val inputStream = socket.getInputStream()
                        val response = inputStream.readBytes().toString(Charsets.UTF_8)
                        println("[RemoteOCR] Response received: '$response' (length=${response.length})")
                        return response
                    } catch (e: SocketTimeoutException) {
                        println("[RemoteOCR] Socket timeout while reading response")
                        return ""
                    }
                }
            } catch (e: Exception) {
                println("[RemoteOCR] Connection failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }

        fun ocrByRemoteTcpServerAutoRetry(
            host: String, port: Int,
            imageData: ByteArray,
            retryTimes: Int = 3
        ): String {
            var result: String = ""

            println("[RemoteOCR] Starting auto retry: host=$host, port=$port, retryTimes=$retryTimes, imageData size=${imageData.size}")

            for (i in 1..retryTimes) {
                println("[RemoteOCR] Attempt $i/$retryTimes")
                try {
                    result = ocrByRemoteTcpServer(host, port, imageData)
                    println("[RemoteOCR] Attempt $i result: '$result'")
                } catch (e: Exception) {
                    println("[RemoteOCR] Attempt $i failed: ${e.javaClass.simpleName}: ${e.message}")
                    e.printStackTrace()
                    continue
                }

                if (result.isNotEmpty()) {
                    println("[RemoteOCR] Success on attempt $i")
                    break
                } else {
                    println("[RemoteOCR] Attempt $i returned empty result, will retry")
                }
            }

            if (result.isEmpty()) {
                println("[RemoteOCR] All $retryTimes attempts failed, returning empty result")
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
