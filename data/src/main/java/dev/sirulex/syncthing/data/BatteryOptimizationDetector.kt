package dev.sirulex.syncthing.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Detect whether the app is battery-optimization-whitelisted, and provide
 * OEM-specific intents to the autostart / keep-alive settings pages.
 *
 * OEM intents are notoriously fragile (they can be renamed or removed per
 * firmware). Always pair with the app-info settings fallback.
 */
object BatteryOptimizationDetector {

    fun isIgnoringOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun buildRequestIgnoreIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    }

    fun buildAppInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Return a manufacturer-specific autostart / keep-alive settings intent
     * if known. Target activities change between firmware versions — callers
     * must gracefully fall back to [buildAppInfoIntent] on ActivityNotFoundException.
     */
    fun buildOemAutostartIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val component = when {
            manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ->
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            manufacturer.contains("samsung") ->
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity",
                )
            manufacturer.contains("oneplus") ->
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                )
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )
            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                )
            manufacturer.contains("vivo") ->
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                )
            else -> return null
        }
        return Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun manufacturerFriendlyName(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi / MIUI"
            m.contains("samsung") -> "Samsung / OneUI"
            m.contains("oneplus") -> "OnePlus / OxygenOS"
            m.contains("huawei") || m.contains("honor") -> "Huawei / EMUI"
            m.contains("oppo") || m.contains("realme") -> "Oppo / ColorOS"
            m.contains("vivo") -> "Vivo / FuntouchOS"
            else -> Build.MANUFACTURER.ifBlank { "Unknown" }
        }
    }
}
