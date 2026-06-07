package cn.edu.shmtu.terminal.android.data.local.datastore

import android.content.Context
import android.content.SharedPreferences
import cn.edu.shmtu.terminal.android.data.notification.NotificationConfig
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
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
    private val _p2pAutoReconnectFlow = MutableStateFlow(getP2PAutoReconnect())

    // Notification config flow
    private val _notificationConfigFlow = MutableStateFlow(getNotificationConfig())

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
    val p2pAutoReconnect: Flow<Boolean> = _p2pAutoReconnectFlow.asStateFlow()

    // Notification config
    val notificationConfig: Flow<NotificationConfig> = _notificationConfigFlow.asStateFlow()

    fun notificationConfigValue(): NotificationConfig = _notificationConfigFlow.value

    fun p2pDeviceNameFlowValue(): String = _p2pDeviceNameFlow.value
    fun p2pPortFlowValue(): Int = _p2pPortFlow.value

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

    fun setP2PAutoReconnect(value: Boolean) {
        prefs.edit().putBoolean(KEY_P2P_AUTO_RECONNECT, value).apply()
        _p2pAutoReconnectFlow.value = value
    }

    fun getP2PAutoReconnectNow(): Boolean = _p2pAutoReconnectFlow.value

    // Notification config setter
    fun setNotificationConfig(config: NotificationConfig) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_NOTIF_SYNC_COMPLETE, config.syncCompleteEnabled)
        editor.putBoolean(KEY_NOTIF_NEW_BILLS, config.newBillsFoundEnabled)
        editor.putBoolean(KEY_NOTIF_P2P_TRANSFER, config.p2pTransferEnabled)
        editor.putBoolean(KEY_NOTIF_P2P_PAIR, config.p2pPairRequestEnabled)
        editor.putBoolean(KEY_NOTIF_PERSISTENT, config.persistentStatusEnabled)
        editor.putBoolean(KEY_NOTIF_HEADS_UP, config.useHeadsUp)
        editor.putBoolean(KEY_NOTIF_SILENT_NIGHT, config.silentOnNight)
        editor.putInt(KEY_NOTIF_NIGHT_START, config.nightStartHour)
        editor.putInt(KEY_NOTIF_NIGHT_END, config.nightEndHour)
        editor.putFloat(KEY_NOTIF_THRESHOLD, config.newBillThresholdAmount.toFloat())
        editor.putBoolean(KEY_NOTIF_WEBHOOK_ENABLED, config.webhookEnabled)
        editor.putString(KEY_NOTIF_WEBHOOK_TYPE, config.webhookType.name)
        editor.putString(KEY_NOTIF_WEBHOOK_URL, config.webhookUrl)
        editor.putString(KEY_NOTIF_WEBHOOK_TEMPLATE, config.webhookMessageTemplate)
        editor.apply()
        _notificationConfigFlow.value = config
    }

    fun getNotificationConfig(): NotificationConfig {
        return NotificationConfig(
            syncCompleteEnabled = prefs.getBoolean(KEY_NOTIF_SYNC_COMPLETE, true),
            newBillsFoundEnabled = prefs.getBoolean(KEY_NOTIF_NEW_BILLS, true),
            p2pTransferEnabled = prefs.getBoolean(KEY_NOTIF_P2P_TRANSFER, true),
            p2pPairRequestEnabled = prefs.getBoolean(KEY_NOTIF_P2P_PAIR, true),
            persistentStatusEnabled = prefs.getBoolean(KEY_NOTIF_PERSISTENT, true),
            useHeadsUp = prefs.getBoolean(KEY_NOTIF_HEADS_UP, true),
            silentOnNight = prefs.getBoolean(KEY_NOTIF_SILENT_NIGHT, false),
            nightStartHour = prefs.getInt(KEY_NOTIF_NIGHT_START, 22),
            nightEndHour = prefs.getInt(KEY_NOTIF_NIGHT_END, 7),
            newBillThresholdAmount = prefs.getFloat(KEY_NOTIF_THRESHOLD, 0.0f).toDouble(),
            webhookEnabled = prefs.getBoolean(KEY_NOTIF_WEBHOOK_ENABLED, false),
            webhookType = runCatching {
                WebhookType.valueOf(prefs.getString(KEY_NOTIF_WEBHOOK_TYPE, "NONE") ?: "NONE")
            }.getOrDefault(WebhookType.NONE),
            webhookUrl = prefs.getString(KEY_NOTIF_WEBHOOK_URL, "") ?: "",
            webhookMessageTemplate = prefs.getString(
                KEY_NOTIF_WEBHOOK_TEMPLATE,
                "【海大账单】{time} 消费 {amount} 元 @ {merchant}"
            ) ?: "【海大账单】{time} 消费 {amount} 元 @ {merchant}"
        )
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

    private fun getP2PAutoReconnect(): Boolean =
        prefs.getBoolean(KEY_P2P_AUTO_RECONNECT, false)

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
        private const val KEY_P2P_AUTO_RECONNECT = "p2p_auto_reconnect"
        // Notification config keys
        private const val KEY_NOTIF_SYNC_COMPLETE = "notif_sync_complete"
        private const val KEY_NOTIF_NEW_BILLS = "notif_new_bills"
        private const val KEY_NOTIF_P2P_TRANSFER = "notif_p2p_transfer"
        private const val KEY_NOTIF_P2P_PAIR = "notif_p2p_pair"
        private const val KEY_NOTIF_PERSISTENT = "notif_persistent"
        private const val KEY_NOTIF_HEADS_UP = "notif_heads_up"
        private const val KEY_NOTIF_SILENT_NIGHT = "notif_silent_night"
        private const val KEY_NOTIF_NIGHT_START = "notif_night_start"
        private const val KEY_NOTIF_NIGHT_END = "notif_night_end"
        private const val KEY_NOTIF_THRESHOLD = "notif_threshold"
        private const val KEY_NOTIF_WEBHOOK_ENABLED = "notif_webhook_enabled"
        private const val KEY_NOTIF_WEBHOOK_TYPE = "notif_webhook_type"
        private const val KEY_NOTIF_WEBHOOK_URL = "notif_webhook_url"
        private const val KEY_NOTIF_WEBHOOK_TEMPLATE = "notif_webhook_template"
    }
}

enum class CaptchaMode {
    MANUAL,
    AUTO_OCR
}
