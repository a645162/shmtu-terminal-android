package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 特性设置存储 - 独立于 [cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore],
 * 存 Tauri 对齐的多级设置项(主题/同步/安全/更新/身份等)。避开 KSP 解析新 enum 的 bug。
 */
@Singleton
class FeatureSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("feature_settings", Context.MODE_PRIVATE)

    // 主题
    val themeMode: StateFlow<String> = MutableStateFlow(prefs.getString("theme_mode", "system")!!)
    val decimalPlaces: StateFlow<Int> = MutableStateFlow(prefs.getInt("decimal_places", 2))

    fun setThemeMode(v: String) { prefs.edit().putString("theme_mode", v).apply(); (themeMode as MutableStateFlow).value = v }
    fun setDecimalPlaces(n: Int) { prefs.edit().putInt("decimal_places", n).apply(); (decimalPlaces as MutableStateFlow).value = n }

    // 首页图表
    val homeTrendRange: StateFlow<String> = MutableStateFlow(prefs.getString("home_trend", "week")!!)
    val homeCategoryRange: StateFlow<String> = MutableStateFlow(prefs.getString("home_category", "month")!!)

    fun setHomeTrendRange(v: String) { prefs.edit().putString("home_trend", v).apply(); (homeTrendRange as MutableStateFlow).value = v }
    fun setHomeCategoryRange(v: String) { prefs.edit().putString("home_category", v).apply(); (homeCategoryRange as MutableStateFlow).value = v }

    // 同步
    val syncMaxPages: StateFlow<Int> = MutableStateFlow(prefs.getInt("sync_max_pages", 100))
    val syncEarlyStop: StateFlow<Int> = MutableStateFlow(prefs.getInt("sync_early_stop", 5))
    val syncSkipGraduated: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("sync_skip_grad", true))
    val syncAutoMerge: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("sync_auto_merge", true))
    val autoSyncEnabled: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("auto_sync_enabled", false))
    val autoSyncInterval: StateFlow<Int> = MutableStateFlow(prefs.getInt("auto_sync_interval", 60))
    val autoSyncRange: StateFlow<String> = MutableStateFlow(prefs.getString("auto_sync_range", "month")!!)

    fun setSyncMaxPages(n: Int) { prefs.edit().putInt("sync_max_pages", n).apply(); (syncMaxPages as MutableStateFlow).value = n }
    fun setSyncEarlyStop(n: Int) { prefs.edit().putInt("sync_early_stop", n).apply(); (syncEarlyStop as MutableStateFlow).value = n }
    fun setSyncSkipGraduated(v: Boolean) { prefs.edit().putBoolean("sync_skip_grad", v).apply(); (syncSkipGraduated as MutableStateFlow).value = v }
    fun setSyncAutoMerge(v: Boolean) { prefs.edit().putBoolean("sync_auto_merge", v).apply(); (syncAutoMerge as MutableStateFlow).value = v }
    fun setAutoSyncEnabled(v: Boolean) { prefs.edit().putBoolean("auto_sync_enabled", v).apply(); (autoSyncEnabled as MutableStateFlow).value = v }
    fun setAutoSyncInterval(n: Int) { prefs.edit().putInt("auto_sync_interval", n).apply(); (autoSyncInterval as MutableStateFlow).value = n }
    fun setAutoSyncRange(v: String) { prefs.edit().putString("auto_sync_range", v).apply(); (autoSyncRange as MutableStateFlow).value = v }

    // 安全
    val enableStartupProtection: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("startup_protection", false))
    val startupPasswordHash: StateFlow<String?> = MutableStateFlow(prefs.getString("startup_password_hash", null))

    fun setEnableStartupProtection(v: Boolean) { prefs.edit().putBoolean("startup_protection", v).apply(); (enableStartupProtection as MutableStateFlow).value = v }
    fun setStartupPasswordHash(v: String?) { prefs.edit().apply { if (v == null) remove("startup_password_hash") else putString("startup_password_hash", v) }.apply(); (startupPasswordHash as MutableStateFlow).value = v }

    // 验证码
    val captchaMode: StateFlow<String> = MutableStateFlow(prefs.getString("captcha_mode_v2", "manual")!!)
    val ocrRetryCount: StateFlow<Int> = MutableStateFlow(prefs.getInt("ocr_retry", 5))
    val ocrHttpUrl: StateFlow<String> = MutableStateFlow(prefs.getString("ocr_http_url", "http://127.0.0.1:5000")!!)

    fun setCaptchaModeV2(v: String) { prefs.edit().putString("captcha_mode_v2", v).apply(); (captchaMode as MutableStateFlow).value = v }
    fun setOcrRetryCount(n: Int) { prefs.edit().putInt("ocr_retry", n).apply(); (ocrRetryCount as MutableStateFlow).value = n }
    fun setOcrHttpUrl(v: String) { prefs.edit().putString("ocr_http_url", v).apply(); (ocrHttpUrl as MutableStateFlow).value = v }

    // 更新
    val autoCheckUpdate: StateFlow<Boolean> = MutableStateFlow(prefs.getBoolean("auto_check_update", true))
    val checkIntervalHours: StateFlow<Int> = MutableStateFlow(prefs.getInt("check_interval_hours", 24))

    fun setAutoCheckUpdate(v: Boolean) { prefs.edit().putBoolean("auto_check_update", v).apply(); (autoCheckUpdate as MutableStateFlow).value = v }
    fun setCheckIntervalHours(n: Int) { prefs.edit().putInt("check_interval_hours", n).apply(); (checkIntervalHours as MutableStateFlow).value = n }

    // 身份
    val identityStartupMode: StateFlow<String> = MutableStateFlow(prefs.getString("identity_startup_mode", "last_used")!!)
    val defaultIdentityId: StateFlow<Long> = MutableStateFlow(prefs.getLong("default_identity_id", 0L))

    fun setIdentityStartupMode(v: String) { prefs.edit().putString("identity_startup_mode", v).apply(); (identityStartupMode as MutableStateFlow).value = v }
    fun setDefaultIdentityId(v: Long) { prefs.edit().putLong("default_identity_id", v).apply(); (defaultIdentityId as MutableStateFlow).value = v }
}
