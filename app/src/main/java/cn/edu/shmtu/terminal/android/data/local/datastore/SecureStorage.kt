package cn.edu.shmtu.terminal.android.data.local.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_storage",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(accountId: Long, password: String) {
        prefs.edit().putString("account_password_$accountId", password).apply()
    }

    fun getPassword(accountId: Long): String? {
        return prefs.getString("account_password_$accountId", null)
    }

    fun removePassword(accountId: Long) {
        prefs.edit().remove("account_password_$accountId").apply()
    }

    fun saveEpayCookie(accountId: Long, cookie: String) {
        prefs.edit().putString("account_epay_cookie_$accountId", cookie).apply()
    }

    fun getEpayCookie(accountId: Long): String? {
        return prefs.getString("account_epay_cookie_$accountId", null)
    }

    fun removeEpayCookie(accountId: Long) {
        prefs.edit().remove("account_epay_cookie_$accountId").apply()
    }

    fun saveLoginUrl(accountId: Long, url: String) {
        prefs.edit().putString("account_login_url_$accountId", url).apply()
    }

    fun getLoginUrl(accountId: Long): String? {
        return prefs.getString("account_login_url_$accountId", null)
    }

    fun removeLoginUrl(accountId: Long) {
        prefs.edit().remove("account_login_url_$accountId").apply()
    }

    fun saveLoginCookie(accountId: Long, cookie: String) {
        prefs.edit().putString("account_login_cookie_$accountId", cookie).apply()
    }

    fun getLoginCookie(accountId: Long): String? {
        return prefs.getString("account_login_cookie_$accountId", null)
    }

    fun removeLoginCookie(accountId: Long) {
        prefs.edit().remove("account_login_cookie_$accountId").apply()
    }

    fun saveWechatCookie(accountId: Long, cookie: String) {
        prefs.edit().putString("account_wechat_cookie_$accountId", cookie).apply()
    }

    fun getWechatCookie(accountId: Long): String? {
        return prefs.getString("account_wechat_cookie_$accountId", null)
    }

    fun saveWechatLoginUrl(accountId: Long, url: String) {
        prefs.edit().putString("account_wechat_login_url_$accountId", url).apply()
    }

    fun getWechatLoginUrl(accountId: Long): String? {
        return prefs.getString("account_wechat_login_url_$accountId", null)
    }

    fun clearAccountData(accountId: Long) {
        prefs.edit()
            .remove("account_password_$accountId")
            .remove("account_epay_cookie_$accountId")
            .remove("account_login_url_$accountId")
            .remove("account_login_cookie_$accountId")
            .remove("account_wechat_cookie_$accountId")
            .remove("account_wechat_login_url_$accountId")
            .apply()
    }
}
