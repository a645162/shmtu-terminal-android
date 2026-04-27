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
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _captchaModeFlow = MutableStateFlow(getCaptchaMode())
    private val _useLocalOcrFlow = MutableStateFlow(getUseLocalOcr())
    private val _ocrServerUrlFlow = MutableStateFlow(getOcrServerUrl())

    val captchaMode: Flow<CaptchaMode> = _captchaModeFlow.asStateFlow()
    val useLocalOcr: Flow<Boolean> = _useLocalOcrFlow.asStateFlow()
    val ocrServerUrl: Flow<String> = _ocrServerUrlFlow.asStateFlow()

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

    companion object {
        private const val KEY_CAPTCHA_MODE = "captcha_mode"
        private const val KEY_USE_LOCAL_OCR = "use_local_ocr"
        private const val KEY_OCR_SERVER_URL = "ocr_server_url"
    }
}

enum class CaptchaMode {
    MANUAL,
    AUTO_OCR
}
