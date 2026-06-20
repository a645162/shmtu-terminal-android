package cn.edu.shmtu.terminal.android.data.ocrserver

import android.content.Context
import android.content.SharedPreferences
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR 服务器设置 (端口 / token / 启停 / 模型版本 / v2 backbone+precision)
 *
 * 与 BillWebServer 共用一个 SharedPreferences 文件 (`web_server_settings`),避免拆分存储。
 * key 前缀 `ocr_server_` 与账单服务区分。
 */
@Singleton
class OcrServerSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("web_server_settings", Context.MODE_PRIVATE)

    private val _enabledFlow = MutableStateFlow(getEnabled())
    private val _portFlow = MutableStateFlow(getPort())
    private val _authTokenFlow = MutableStateFlow(getAuthToken())

    val enabledFlow: Flow<Boolean> = _enabledFlow.asStateFlow()
    val portFlow: Flow<Int> = _portFlow.asStateFlow()
    val authTokenFlow: Flow<String> = _authTokenFlow.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabledFlow.value = value
    }

    fun setPort(value: Int) {
        prefs.edit().putInt(KEY_PORT, value).apply()
        _portFlow.value = value
    }

    fun setAuthToken(value: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        _authTokenFlow.value = value
    }

    fun enabled(): Boolean = _enabledFlow.value
    fun port(): Int = _portFlow.value
    fun authToken(): String = _authTokenFlow.value

    fun modelVersion(): String =
        prefs.getString(KEY_MODEL_VERSION, SHMTU_NCNN_Model.ModelVersion.V2.name)
            ?: SHMTU_NCNN_Model.ModelVersion.V2.name

    fun v2Backbone(): String =
        prefs.getString(KEY_V2_BACKBONE, SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE)
            ?: SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE

    fun v2Precision(): String =
        prefs.getString(KEY_V2_PRECISION, SHMTU_NCNN_Model.V2_DEFAULT_PRECISION)
            ?: SHMTU_NCNN_Model.V2_DEFAULT_PRECISION

    fun setModelVersion(value: String) {
        prefs.edit().putString(KEY_MODEL_VERSION, value).apply()
    }

    fun setV2Backbone(value: String) {
        prefs.edit().putString(KEY_V2_BACKBONE, value).apply()
    }

    fun setV2Precision(value: String) {
        prefs.edit().putString(KEY_V2_PRECISION, value).apply()
    }

    /** 监听范围:loopback_only / lan / custom_ip */
    fun scope(): OcrServerScope {
        val raw = prefs.getString(KEY_SCOPE, OcrServerScope.LAN.name) ?: OcrServerScope.LAN.name
        return runCatching { OcrServerScope.valueOf(raw) }.getOrDefault(OcrServerScope.LAN)
    }

    fun setScope(value: OcrServerScope) {
        prefs.edit().putString(KEY_SCOPE, value.name).apply()
    }

    fun bindAddress(): String =
        prefs.getString(KEY_BIND_ADDR, "") ?: ""

    fun setBindAddress(value: String) {
        prefs.edit().putString(KEY_BIND_ADDR, value).apply()
    }

    /** 根据 scope + bindAddress 解析实际 bind IP 字符串 */
    fun resolvedBindAddress(): String = when (scope()) {
        OcrServerScope.LOOPBACK_ONLY -> "127.0.0.1"
        OcrServerScope.LAN -> "0.0.0.0"
        OcrServerScope.CUSTOM_IP -> {
            val v = bindAddress().trim()
            if (v.isEmpty()) "0.0.0.0" else v
        }
    }

    private fun getEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    private fun getPort(): Int = prefs.getInt(KEY_PORT, DEFAULT_PORT)
    private fun getAuthToken(): String = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""

    companion object {
        const val DEFAULT_PORT = 5000
        private const val KEY_ENABLED = "ocr_server_enabled"
        private const val KEY_PORT = "ocr_server_port"
        private const val KEY_AUTH_TOKEN = "ocr_server_auth_token"
        private const val KEY_MODEL_VERSION = "ocr_server_model_version"
        private const val KEY_V2_BACKBONE = "ocr_server_v2_backbone"
        private const val KEY_V2_PRECISION = "ocr_server_v2_precision"
        private const val KEY_SCOPE = "ocr_server_scope"
        private const val KEY_BIND_ADDR = "ocr_server_bind_addr"
    }
}

/**
 * OCR HTTP 服务器监听范围
 *
 * - [LOOPBACK_ONLY]: 只监听 127.0.0.1,本机其他进程可访问,局域网/公网不可达
 * - [LAN]: 监听 0.0.0.0,局域网内任何设备可达 (默认,适合开发/家庭网络)
 * - [CUSTOM_IP]: 绑定到 [OcrServerSettings.bindAddress] 指定的具体网卡 IP
 */
enum class OcrServerScope {
    LOOPBACK_ONLY,
    LAN,
    CUSTOM_IP,
}