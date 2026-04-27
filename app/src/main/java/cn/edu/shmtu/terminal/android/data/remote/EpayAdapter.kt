package cn.edu.shmtu.terminal.android.data.remote

import android.util.Log
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.parser.BillParser
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpayAdapter @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val TAG = "EpayAdapter"

    private val instances = mutableMapOf<Long, EpayAuth>()

    fun getEpayAuth(accountId: Long): EpayAuth {
        return instances.getOrPut(accountId) { createEpayAuthWithCookies(accountId) }
    }

    private fun createEpayAuthWithCookies(accountId: Long): EpayAuth {
        val epayAuth = EpayAuth()
        secureStorage.getLoginUrl(accountId)?.let { url ->
            epayAuth.setLoginUrl(url)
            Log.d(TAG, "Restored loginUrl for account $accountId: $url")
        }
        secureStorage.getLoginCookie(accountId)?.let { cookie ->
            epayAuth.setLoginCookie(cookie)
            Log.d(TAG, "Restored loginCookie for account $accountId")
        }
        secureStorage.getEpayCookie(accountId)?.let { cookie ->
            epayAuth.setEpayCookie(cookie)
            Log.d(TAG, "Restored epayCookie for account $accountId")
        }
        return epayAuth
    }

    suspend fun testLoginStatus(accountId: Long): Boolean = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).testLoginStatus()
        Log.d(TAG, "testLoginStatus for account $accountId: $result")
        if (!result) {
            val epayAuth = getEpayAuth(accountId)
            Log.d(TAG, "testLoginStatus failed, loginUrl=${epayAuth.getLoginUrl()}, epayCookie=${epayAuth.getEpayCookie().take(20)}...")
        }
        result
    }

    suspend fun loginWithCaptcha(accountId: Long, username: String, password: String, captchaCode: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "loginWithCaptcha for account $accountId, username=$username, captchaCode=$captchaCode")
        val epayAuth = getEpayAuth(accountId)
        val success = epayAuth.loginWithCaptcha(username, password, captchaCode)
        Log.d(TAG, "loginWithCaptcha result: $success")
        if (success) {
            secureStorage.saveLoginUrl(accountId, epayAuth.getLoginUrl())
            secureStorage.saveLoginCookie(accountId, epayAuth.getLoginCookie())
            secureStorage.saveEpayCookie(accountId, epayAuth.getEpayCookie())
            Log.d(TAG, "Saved cookies after successful login for account $accountId")
        }
        success
    }

    suspend fun fetchBillPage(accountId: Long, pageNo: Int): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        val result = getEpayAuth(accountId).getBill(pageNo = pageNo.toString())
        Log.d(TAG, "fetchBillPage account=$accountId page=$pageNo resultCode=${result.first}")
        result
    }

    fun parseBillList(html: String): List<Map<String, String>> {
        val parser = BillParser()
        return parser.getBillTr(html).getBillList().map { it as Map<String, String> }
    }

    fun getPageCount(html: String): Int {
        return BillParser().getPageCount(html)
    }
}
