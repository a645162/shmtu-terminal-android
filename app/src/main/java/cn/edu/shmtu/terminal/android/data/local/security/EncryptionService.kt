package cn.edu.shmtu.terminal.android.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密服务 — 提供密码加密存储、Cookie 加密存储等功能
 * 对齐 C# 版本的 EncryptionService
 * 使用 Android Keystore + AES-256-GCM
 */
@Singleton
class EncryptionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "shmtu_terminal_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // 16 bytes
        private const val GCM_IV_LENGTH = 12

        // 固定 salt 用于特定用途的加密
        private const val PASSWORD_SALT = "shmtu-account-password"
        private const val COOKIE_SALT = "shmtu-session-cookie"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keySpec)
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * 加密数据
     */
    fun encrypt(plainText: String, fixedSalt: String? = null): String {
        if (plainText.isEmpty()) return ""
        
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val salt = if (fixedSalt != null) {
            fixedSalt.toByteArray().copyOf(16)
        } else {
            ByteArray(16).also { SecureRandom().nextBytes(it) }
        }
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)
        
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)
        
        // 组合: salt(16) + iv(12) + cipher + tag
        val result = ByteArray(salt.size + iv.size + cipherBytes.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(cipherBytes, 0, result, salt.size + iv.size, cipherBytes.size)
        
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    /**
     * 解密数据
     */
    fun decrypt(cipherText: String, fixedSalt: String? = null): String {
        if (cipherText.isEmpty()) return ""
        
        return try {
            val fullCipher = Base64.decode(cipherText, Base64.NO_WRAP)
            
            val salt = ByteArray(16)
            val iv = ByteArray(GCM_IV_LENGTH)
            val tagLength = GCM_TAG_LENGTH / 8
            val cipherLength = fullCipher.size - 16 - GCM_IV_LENGTH - tagLength
            
            System.arraycopy(fullCipher, 0, salt, 0, 16)
            System.arraycopy(fullCipher, 16, iv, 0, GCM_IV_LENGTH)
            
            val cipherBytes = ByteArray(cipherLength)
            val tag = ByteArray(tagLength)
            System.arraycopy(fullCipher, 16 + GCM_IV_LENGTH, cipherBytes, 0, cipherLength)
            System.arraycopy(fullCipher, fullCipher.size - tagLength, tag, 0, tagLength)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw DecryptionFailedException("解密失败：${e.message}", e)
        }
    }

    // 便捷方法
    fun encryptPassword(password: String): String = encrypt(password, PASSWORD_SALT)
    fun decryptPassword(encryptedPassword: String): String = decrypt(encryptedPassword, PASSWORD_SALT)
    fun encryptCookie(cookieData: String): String = encrypt(cookieData, COOKIE_SALT)
    fun decryptCookie(encryptedCookie: String): String = decrypt(encryptedCookie, COOKIE_SALT)

    /**
     * 清除所有加密数据（用于解密失败后的恢复）
     */
    fun clearAllData() {
        try {
            // 清除 Keystore 密钥
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Exception) {
            // 忽略
        }
    }
}

class DecryptionFailedException(message: String, cause: Throwable? = null) : Exception(message, cause)
