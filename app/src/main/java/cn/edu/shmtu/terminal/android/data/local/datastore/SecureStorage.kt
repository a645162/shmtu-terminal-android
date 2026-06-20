package cn.edu.shmtu.terminal.android.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy { loadOrCreateSecretKey() }

    fun savePassword(accountId: Long, password: String) {
        putEncrypted("account_password_$accountId", password)
    }

    fun getPassword(accountId: Long): String? {
        return getDecrypted("account_password_$accountId")
    }

    fun removePassword(accountId: Long) {
        prefs.edit().remove("account_password_$accountId").apply()
    }

    fun saveEpayCookie(accountId: Long, cookie: String) {
        putEncrypted("account_epay_cookie_$accountId", cookie)
    }

    fun getEpayCookie(accountId: Long): String? {
        return getDecrypted("account_epay_cookie_$accountId")
    }

    fun removeEpayCookie(accountId: Long) {
        prefs.edit().remove("account_epay_cookie_$accountId").apply()
    }

    fun saveLoginUrl(accountId: Long, url: String) {
        putEncrypted("account_login_url_$accountId", url)
    }

    fun getLoginUrl(accountId: Long): String? {
        return getDecrypted("account_login_url_$accountId")
    }

    fun removeLoginUrl(accountId: Long) {
        prefs.edit().remove("account_login_url_$accountId").apply()
    }

    fun saveLoginCookie(accountId: Long, cookie: String) {
        putEncrypted("account_login_cookie_$accountId", cookie)
    }

    fun getLoginCookie(accountId: Long): String? {
        return getDecrypted("account_login_cookie_$accountId")
    }

    fun removeLoginCookie(accountId: Long) {
        prefs.edit().remove("account_login_cookie_$accountId").apply()
    }

    fun saveWechatCookie(accountId: Long, cookie: String) {
        putEncrypted("account_wechat_cookie_$accountId", cookie)
    }

    fun getWechatCookie(accountId: Long): String? {
        return getDecrypted("account_wechat_cookie_$accountId")
    }

    fun removeWechatCookie(accountId: Long) {
        prefs.edit().remove("account_wechat_cookie_$accountId").apply()
    }

    fun saveWechatLoginUrl(accountId: Long, url: String) {
        putEncrypted("account_wechat_login_url_$accountId", url)
    }

    fun getWechatLoginUrl(accountId: Long): String? {
        return getDecrypted("account_wechat_login_url_$accountId")
    }

    fun removeWechatLoginUrl(accountId: Long) {
        prefs.edit().remove("account_wechat_login_url_$accountId").apply()
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

    private fun putEncrypted(key: String, value: String) {
        prefs.edit()
            .putString(key, encrypt(value))
            .apply()
    }

    private fun getDecrypted(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }
            .getOrElse {
                prefs.edit().remove(key).apply()
                null
            }
    }

    // Stored format is "base64(iv):base64(ciphertext)".
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$iv:$payload"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload" }

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "secure_storage"
        const val KEY_ALIAS = "shmtu_secure_storage_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
