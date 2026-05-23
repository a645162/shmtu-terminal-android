package cn.edu.shmtu.terminal.android.data.remote

import android.util.Log
import cn.edu.shmtu.cas.auth.EpayAuth
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

data class LoginResult(
    val success: Boolean,
    val cookie: String,
    val errorCode: Int = 0
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

    suspend fun getLoginUrl(accountId: Long, epayAdapter: EpayAdapter): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "getLoginUrl: accountId=$accountId")
        val result = epayAdapter.fetchBillPage(accountId, 1)
        val loginUrl = if (result.first == 302) result.second else ""
        Log.d(TAG, "getLoginUrl: result code=${result.first}, loginUrl=${loginUrl.take(60)}...")
        loginUrl
    }

    suspend fun casLogin(
        url: String,
        username: String,
        password: String,
        validateCode: String,
        execution: String,
        cookie: String
    ): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "casLogin: url=$url, username=$username, validateCode=$validateCode")
        val result = CasAuth.casLogin(url, username, password, validateCode, execution, cookie)
        Log.d(TAG, "casLogin: result code=${result.first}")
        result
    }

    suspend fun casRedirect(url: String, cookie: String): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "casRedirect: url=${url.take(80)}...")
        val result = CasAuth.casRedirect(url, cookie)
        Log.d(TAG, "casRedirect: result code=${result.first}")
        result
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
