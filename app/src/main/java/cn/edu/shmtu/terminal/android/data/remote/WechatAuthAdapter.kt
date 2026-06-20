package cn.edu.shmtu.terminal.android.data.remote

import android.util.Log
import cn.edu.shmtu.cas.auth.WechatAuth
import cn.edu.shmtu.cas.parser.HotWaterParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
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

    fun invalidateSession(accountId: Long) {
        secureStorage.removeWechatCookie(accountId)
        secureStorage.removeWechatLoginUrl(accountId)
        instances.remove(accountId)
        Log.d(TAG, "Invalidated wechat session for account $accountId")
    }

    private fun createWechatAuthWithCookies(accountId: Long): WechatAuth {
        val wechatAuth = WechatAuth()

        // 恢复会话 cookies
        secureStorage.getWechatCookie(accountId)?.let { cookie ->
            val result = wechatAuth.restoreSession(cookie)
            if (result.isSuccess) {
                Log.d(TAG, "Restored wechatCookie for account $accountId")
            }
        }

        return wechatAuth
    }

    /**
     * 探测热水登录状态
     */
    suspend fun probeLogin(accountId: Long): Result<SessionProbe> = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).probeLogin()
        Log.d(TAG, "probeLogin for account $accountId: $result")
        result
    }

    /**
     * 获取验证码图片
     */
    suspend fun prepareChallenge(accountId: Long) = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).prepareChallenge()
        Log.d(TAG, "prepareChallenge for account $accountId")
        result
    }

    /**
     * 提交登录（自动获取 execution）
     */
    suspend fun submitLogin(accountId: Long, username: String, password: String, captchaCode: String): Result<LoginSubmitResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "submitLogin for account $accountId")
        val wechatAuth = getWechatAuth(accountId)

        // 先获取 execution
        val challengeResult = wechatAuth.prepareChallenge()
        if (challengeResult.isFailure) {
            Log.e(TAG, "prepareChallenge failed: ${challengeResult.exceptionOrNull()?.message}")
            return@withContext Result.failure(challengeResult.exceptionOrNull() ?: Exception("获取验证码失败"))
        }

        val challenge = challengeResult.getOrNull()
        if (challenge == null) {
            return@withContext Result.failure(Exception("获取验证码失败"))
        }

        // 提交登录
        val result = wechatAuth.submitLogin(username, password, captchaCode, challenge.execution)

        if (result.isSuccess && result.getOrNull() is LoginSubmitResult.Success) {
            val cookiesJson = wechatAuth.extractSession()
            secureStorage.saveWechatCookie(accountId, cookiesJson)
            // 新 cas_lib 中 loginWUrl 由 probeLogin() 返回值携带;此处尝试再次 probe 以获取并保存
            val probe = wechatAuth.probeLogin()
            if (probe.isSuccess) {
                val p = probe.getOrNull()
                if (p is SessionProbe.AlreadyLoggedIn || p is SessionProbe.NeedLogin) {
                    val url = (p as? SessionProbe.NeedLogin)?.loginUrl ?: ""
                    if (url.isNotBlank()) secureStorage.saveWechatLoginUrl(accountId, url)
                }
            }
            Log.d(TAG, "Saved cookies after successful login for account $accountId")
        }

        result
    }

    /**
     * 测试登录状态
     */
    suspend fun testLoginStatus(accountId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).testLoginStatus()
        Log.d(TAG, "testLoginStatus for account $accountId: $result")
        result
    }

    /**
     * 获取热水数据
     */
    suspend fun fetchHotWater(accountId: Long): Result<String> = withContext(Dispatchers.IO) {
        val result = getWechatAuth(accountId).getHotWater()
        Log.d(TAG, "fetchHotWater account=$accountId")
        result
    }

    fun parseHotWaterList(html: String): List<Triple<Float, Float, Int>> {
        return HotWaterParser(html).getHotWaterList()
    }
}
