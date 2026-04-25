package cn.edu.shmtu.terminal.android.data.local.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
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
}
