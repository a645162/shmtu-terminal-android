package cn.edu.shmtu.terminal.android.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _captchaModeFlow = MutableStateFlow(getCaptchaMode())
    private val _useLocalOcrFlow = MutableStateFlow(getUseLocalOcr())
    private val _ocrServerUrlFlow = MutableStateFlow(getOcrServerUrl())
    private val _sessionCheckIntervalFlow = MutableStateFlow(getSessionCheckInterval())
    private val _currentIdentityIdFlow = MutableStateFlow(getCurrentIdentityId())

    // P2P settings flows
    private val _p2pAutoStartFlow = MutableStateFlow(getP2PAutoStart())
    private val _p2pDeviceNameFlow = MutableStateFlow(getP2PDeviceName())
    private val _p2pPortFlow = MutableStateFlow(getP2PPort())
    private val _p2pAutoAcceptFlow = MutableStateFlow(getP2PAutoAccept())

    val captchaMode: Flow<CaptchaMode> = _captchaModeFlow.asStateFlow()
    val useLocalOcr: Flow<Boolean> = _useLocalOcrFlow.asStateFlow()
    val ocrServerUrl: Flow<String> = _ocrServerUrlFlow.asStateFlow()
    val sessionCheckInterval: Flow<Int> = _sessionCheckIntervalFlow.asStateFlow()
    val currentIdentityId: Flow<Long?> = _currentIdentityIdFlow.asStateFlow()

    // P2P settings
    val p2pAutoStart: Flow<Boolean> = _p2pAutoStartFlow.asStateFlow()
    val p2pDeviceName: Flow<String> = _p2pDeviceNameFlow.asStateFlow()
    val p2pPort: Flow<Int> = _p2pPortFlow.asStateFlow()
    val p2pAutoAccept: Flow<Boolean> = _p2pAutoAcceptFlow.asStateFlow()

    fun setCaptchaMode(mode: CaptchaMode) {
        prefs.edit().putString(KEY_CAPTCHA_MODE, when (mode) {
            CaptchaMode.MANUAL -> "manual"
            CaptchaMode.AUTO_OCR -> "auto"
        }).apply()
        _captchaModeFlow.value = mode
    }

    fun setUseLocalOcr(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_OCR, value).apply()
        _useLocalOcrFlow.value = value
    }

    fun setOcrServerUrl(url: String) {
        prefs.edit().putString(KEY_OCR_SERVER_URL, url).apply()
        _ocrServerUrlFlow.value = url
    }

    fun setSessionCheckInterval(minutes: Int) {
        prefs.edit().putInt(KEY_SESSION_CHECK_INTERVAL, minutes).apply()
        _sessionCheckIntervalFlow.value = minutes
    }

    fun setCurrentIdentityId(identityId: Long?) {
        prefs.edit().apply {
            if (identityId == null) {
                remove(KEY_CURRENT_IDENTITY_ID)
            } else {
                putLong(KEY_CURRENT_IDENTITY_ID, identityId)
            }
        }.apply()
        _currentIdentityIdFlow.value = identityId
    }

    // P2P settings setters
    fun setP2PAutoStart(value: Boolean) {
        prefs.edit().putBoolean(KEY_P2P_AUTO_START, value).apply()
        _p2pAutoStartFlow.value = value
    }

    fun setP2PDeviceName(name: String) {
        prefs.edit().putString(KEY_P2P_DEVICE_NAME, name).apply()
        _p2pDeviceNameFlow.value = name
    }

    fun setP2PPort(port: Int) {
        prefs.edit().putInt(KEY_P2P_PORT, port).apply()
        _p2pPortFlow.value = port
    }

    fun setP2PAutoAccept(value: Boolean) {
        prefs.edit().putBoolean(KEY_P2P_AUTO_ACCEPT, value).apply()
        _p2pAutoAcceptFlow.value = value
    }

    private fun getSessionCheckInterval(): Int =
        prefs.getInt(KEY_SESSION_CHECK_INTERVAL, 10)

    private fun getCaptchaMode(): CaptchaMode {
        return when (prefs.getString(KEY_CAPTCHA_MODE, "manual")) {
            "auto" -> CaptchaMode.AUTO_OCR
            else -> CaptchaMode.MANUAL
        }
    }

    private fun getUseLocalOcr(): Boolean =
        prefs.getBoolean(KEY_USE_LOCAL_OCR, true)

    private fun getOcrServerUrl(): String =
        prefs.getString(KEY_OCR_SERVER_URL, "127.0.0.1:21601") ?: "127.0.0.1:21601"

    private fun getCurrentIdentityId(): Long? =
        if (prefs.contains(KEY_CURRENT_IDENTITY_ID)) {
            prefs.getLong(KEY_CURRENT_IDENTITY_ID, 0L)
        } else {
            null
        }

    private fun getP2PAutoStart(): Boolean =
        prefs.getBoolean(KEY_P2P_AUTO_START, false)

    private fun getP2PDeviceName(): String =
        prefs.getString(KEY_P2P_DEVICE_NAME, android.os.Build.MODEL ?: "SHMTU Device")
            ?: (android.os.Build.MODEL ?: "SHMTU Device")

    private fun getP2PPort(): Int =
        prefs.getInt(KEY_P2P_PORT, 19827)

    private fun getP2PAutoAccept(): Boolean =
        prefs.getBoolean(KEY_P2P_AUTO_ACCEPT, false)

    companion object {
        private const val KEY_CAPTCHA_MODE = "captcha_mode"
        private const val KEY_USE_LOCAL_OCR = "use_local_ocr"
        private const val KEY_OCR_SERVER_URL = "ocr_server_url"
        private const val KEY_SESSION_CHECK_INTERVAL = "session_check_interval"
        private const val KEY_CURRENT_IDENTITY_ID = "current_identity_id"
        // P2P settings keys
        private const val KEY_P2P_AUTO_START = "p2p_auto_start_server"
        private const val KEY_P2P_DEVICE_NAME = "p2p_device_name"
        private const val KEY_P2P_PORT = "p2p_port"
        private const val KEY_P2P_AUTO_ACCEPT = "p2p_auto_accept"
    }
}

enum class CaptchaMode {
    MANUAL,
    AUTO_OCR
}
