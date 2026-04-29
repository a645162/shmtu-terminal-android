package cn.edu.shmtu.terminal.android.data.remote

import android.util.Log
import cn.edu.shmtu.cas.auth.WechatAuth
import cn.edu.shmtu.cas.parser.HotWaterParser
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WechatAuthAdapter @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val TAG = "WechatAuthAdapter"

    private val instances = mutableMapOf<Long, WechatAuth>()

    fun getWechatAuth(accountId: Long): WechatAuth {
        return instances.getOrPut(accountId) { createWechatAuthWithCookies(accountId) }
    }

    private fun createWechatAuthWithCookies(accountId: Long): WechatAuth {
        val wechatAuth = WechatAuth()
        secureStorage.getWechatLoginUrl(accountId)?.let { url ->
            wechatAuth.setLoginWUrl(url)
        }
        secureStorage.getWechatCookie(accountId)?.let { cookie ->
            wechatAuth.setCookie(cookie)
        }
        return wechatAuth
    }

    suspend fun testLoginStatus(accountId: Long): Boolean = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).testLoginStatus()
        Log.d(TAG, "testLoginStatus for account $accountId: $result")
        result
    }

    suspend fun loginWithCaptcha(accountId: Long, username: String, password: String, captchaCode: String, jSessionId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "loginWithCaptcha for account $accountId")
        val wechatAuth = getWechatAuth(accountId)
        val success = wechatAuth.loginWithCaptcha(username, password, captchaCode, jSessionId)
        Log.d(TAG, "loginWithCaptcha result: $success")
        if (success) {
            secureStorage.saveWechatLoginUrl(accountId, wechatAuth.getLoginWUrl())
            secureStorage.saveWechatCookie(accountId, wechatAuth.getCookie())
        }
        success
    }

    suspend fun fetchHotWater(accountId: Long): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).getHotWater()
        Log.d(TAG, "fetchHotWater account=$accountId resultCode=${result.first}")
        result
    }

    fun parseHotWaterList(html: String): List<Triple<Float, Float, Int>> {
        return HotWaterParser(html).getHotWaterList()
    }
}
