package cn.edu.shmtu.terminal.android.data.remote

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

    suspend fun getExecution(url: String, cookie: String): String = withContext(Dispatchers.IO) {
        CasAuth.getExecution(url, cookie)
    }

    suspend fun getCaptcha(cookie: String? = null): CaptchaResult? = withContext(Dispatchers.IO) {
        val result = Captcha.getImageDataFromUrlUsingGet(cookie) ?: return@withContext null
        val imageData = result.first ?: return@withContext null
        CaptchaResult(imageData = imageData, cookie = result.second)
    }

    suspend fun getLoginUrl(accountId: Long, epayAdapter: EpayAdapter): String = withContext(Dispatchers.IO) {
        val result = epayAdapter.fetchBillPage(accountId, 1)
        if (result.first == 302) result.second else ""
    }

    suspend fun casLogin(
        url: String,
        username: String,
        password: String,
        validateCode: String,
        execution: String,
        cookie: String
    ): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        CasAuth.casLogin(url, username, password, validateCode, execution, cookie)
    }

    suspend fun casRedirect(url: String, cookie: String): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        CasAuth.casRedirect(url, cookie)
    }

    suspend fun ocrByRemoteTcp(host: String, port: Int, imageData: ByteArray): String = withContext(Dispatchers.IO) {
        Captcha.ocrByRemoteTcpServer(host, port, imageData)
    }

    suspend fun ocrByRemoteTcpAutoRetry(host: String, port: Int, imageData: ByteArray, retryTimes: Int = 3): String = withContext(Dispatchers.IO) {
        Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageData, retryTimes)
    }

    fun getExprResult(expr: String): String {
        return Captcha.getExprResultByExprString(expr)
    }
}
