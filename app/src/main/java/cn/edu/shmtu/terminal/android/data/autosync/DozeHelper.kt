package cn.edu.shmtu.terminal.android.data.autosync

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Doze / 电池优化白名单助手。
 *
 * Android 6.0+ 引入 Doze 模式, 设备空闲时网络访问 / 定时任务会被严格限制。
 * WorkManager 自身是 Doze 合规的 (在维护窗口执行), 但若业务想进一步保证及时性,
 * 可申请 [PowerManager.isIgnoringBatteryOptimizations] 白名单。
 */
object DozeHelper {

    private const val TAG = "DozeHelper"

    /**
     * 当前应用是否已加入电池优化白名单。
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 构造请求加入电池优化白名单的 Intent。
     * 失败 / 厂商不支持时返回 null, 调用方应降级到系统设置页。
     */
    @SuppressLint("BatteryLife")
    fun buildRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${'$'}{context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 构造降级 Intent: 跳转到应用详情页 (所有厂商都支持).
     */
    fun buildAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${'$'}{context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * 一步到位: 尝试请求白名单, 不可用时降级到应用详情页。
     * 返回是否成功启动 Intent (即有 Activity 可接收).
     */
    fun requestBatteryOptimizationWhitelist(context: Context): Boolean {
        val req = buildRequestIgnoreBatteryOptimizationsIntent(context)
        val intent = req ?: buildAppDetailsIntent(context)
        return try {
            context.startActivity(intent)
            Log.i(TAG, "已请求电池优化白名单")
            true
        } catch (e: Exception) {
            Log.w(TAG, "请求电池优化白名单失败: ${'$'}{e.message}, 降级到应用详情页")
            try {
                context.startActivity(buildAppDetailsIntent(context))
                true
            } catch (e2: Exception) {
                Log.w(TAG, "降级到应用详情页也失败: ${'$'}{e2.message}")
                false
            }
        }
    }

    /**
     * 厂商特定的"自启动管理" Intent, 用于部分国产 ROM (MIUI / EMUI / ColorOS / FuntouchOS 等)。
     * 调用方需要根据设备品牌路由。
     */
    fun buildVendorAutoStartIntent(context: Context): Intent? {
        val brand = Build.MANUFACTURER.lowercase()
        val component: ComponentName? = when {
            brand.contains("xiaomi") || brand.contains("redmi") -> ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            brand.contains("huawei") || brand.contains("honor") -> ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            brand.contains("oppo") -> ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            brand.contains("vivo") -> ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            else -> null
        }
        return component?.let {
            Intent().apply {
                component = it
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
