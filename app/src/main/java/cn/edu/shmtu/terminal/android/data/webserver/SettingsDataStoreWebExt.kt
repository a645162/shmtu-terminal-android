package cn.edu.shmtu.terminal.android.data.webserver

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web Server 设置扩展 - 为 BillWebServer 提供存储访问
 *
 * 这是一个独立的小型存储类,避免向 SettingsDataStore 注入大量新字段。
 * 实际生产也可合并进 SettingsDataStore;此处选择独立以便未来替换。
 */
@Singleton
class SettingsDataStoreWebExt @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("web_server_settings", Context.MODE_PRIVATE)

    private val _enabledFlow = MutableStateFlow(getEnabled())
    private val _portFlow = MutableStateFlow(getPort())
    private val _tokenFlow = MutableStateFlow(getToken())
    private val _authTokenFlow = MutableStateFlow(getAuthToken())

    val webServerEnabled: Flow<Boolean> = _enabledFlow.asStateFlow()
    val webServerPort: Flow<Int> = _portFlow.asStateFlow()
    val webServerToken: Flow<String> = _tokenFlow.asStateFlow()
    val webServerAuthToken: Flow<String> = _authTokenFlow.asStateFlow()

    fun setWebServerEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabledFlow.value = value
    }

    fun setWebServerPort(value: Int) {
        prefs.edit().putInt(KEY_PORT, value).apply()
        _portFlow.value = value
    }

    fun setWebServerToken(value: String) {
        prefs.edit().putString(KEY_TOKEN, value).apply()
        _tokenFlow.value = value
    }

    fun setWebServerAuthToken(value: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        _authTokenFlow.value = value
    }

    fun webServerEnabledValue(): Boolean = _enabledFlow.value
    fun webServerTokenValue(): String = _tokenFlow.value
    fun webServerPortValue(): Int = _portFlow.value
    fun webServerAuthTokenValue(): String = _authTokenFlow.value

    private fun getEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    private fun getPort(): Int = prefs.getInt(KEY_PORT, 8080)
    private fun getToken(): String = prefs.getString(KEY_TOKEN, "") ?: ""
    private fun getAuthToken(): String = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""

    companion object {
        private const val KEY_ENABLED = "web_server_enabled"
        private const val KEY_PORT = "web_server_port"
        private const val KEY_TOKEN = "web_server_token"
        private const val KEY_AUTH_TOKEN = "web_server_auth_token"
    }
}
