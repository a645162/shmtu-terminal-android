package cn.edu.shmtu.terminal.android.data.remote

import android.util.Log
import cn.edu.shmtu.cas.auth.common.CasAuth
import cn.edu.shmtu.cas.captcha.Captcha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CaptchaResult(
    val imageData: ByteArray,
    val cookie: String
)

@Singleton
class CasAuthAdapter @Inject constructor() {

    private companion object {
        const val TAG = "CasAuthAdapter"
    }

    suspend fun getExecution(url: String, cookie: String): Pair<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getExecution: url=$url, cookie=${cookie.take(30)}...")
        val result = CasAuth.getExecution(url, cookie)
        Log.d(TAG, "getExecution: execution=${result.first.take(40)}..., sessionId=${result.second.take(40)}...")
        result
    }

    suspend fun getCaptcha(cookie: String? = null): CaptchaResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "getCaptcha: cookie=${cookie?.take(30)}...")
        val result = Captcha.getImageDataFromUrlUsingGet(cookie) ?: run {
            Log.e(TAG, "getCaptcha: failed to get captcha")
            return@withContext null
        }
        val imageData = result.first ?: run {
            Log.e(TAG, "getCaptcha: image data is null")
            return@withContext null
        }
        Log.d(TAG, "getCaptcha: success, imageSize=${imageData.size}, cookie=${result.second.take(30)}...")
        CaptchaResult(imageData = imageData, cookie = result.second)
    }

    suspend fun ocrByRemoteTcp(host: String, port: Int, imageData: ByteArray): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ocrByRemoteTcp: host=$host, port=$port, imageSize=${imageData.size}")
        Captcha.ocrByRemoteTcpServer(host, port, imageData)
    }

    suspend fun ocrByRemoteTcpAutoRetry(host: String, port: Int, imageData: ByteArray, retryTimes: Int = 3): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ocrByRemoteTcpAutoRetry: host=$host, port=$port, retryTimes=$retryTimes")
        Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageData, retryTimes)
    }

    fun getExprResult(expr: String): String {
        return Captcha.getExprResultByExprString(expr)
    }
}
