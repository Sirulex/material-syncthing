package dev.lostf1sh.syncthing.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    // Run conditions
    val runOnBoot: Flow<Boolean> = pref(Keys.RUN_ON_BOOT, false)
    val wifiOnly: Flow<Boolean> = pref(Keys.WIFI_ONLY, false)
    val allowMetered: Flow<Boolean> = pref(Keys.ALLOW_METERED, true)
    val chargingOnly: Flow<Boolean> = pref(Keys.CHARGING_ONLY, false)
    val respectBatterySaver: Flow<Boolean> = pref(Keys.RESPECT_BATTERY_SAVER, true)

    // Onboarding
    val onboardingComplete: Flow<Boolean> = pref(Keys.ONBOARDING_COMPLETE, false)
    val deviceName: Flow<String> = pref(Keys.DEVICE_NAME, "")

    // Notifications
    val notifySyncComplete: Flow<Boolean> = pref(Keys.NOTIFY_SYNC_COMPLETE, true)
    val notifyDeviceConnected: Flow<Boolean> = pref(Keys.NOTIFY_DEVICE_CONNECTED, false)
    val notifyErrors: Flow<Boolean> = pref(Keys.NOTIFY_ERRORS, true)

    // Appearance
    val theme: Flow<String> = pref(Keys.THEME, "system")

    // Sync profiles
    val activeProfile: Flow<String> = pref(Keys.ACTIVE_PROFILE, "default")

    // Scheduler (time range when syncing is allowed)
    val schedulerEnabled: Flow<Boolean> = pref(Keys.SCHEDULER_ENABLED, false)
    val schedulerStartHour: Flow<Int> = pref(Keys.SCHEDULER_START_HOUR, 23)
    val schedulerEndHour: Flow<Int> = pref(Keys.SCHEDULER_END_HOUR, 6)

    // Per-folder sync conditions (JSON map)
    val folderConditions: Flow<String> = pref(Keys.FOLDER_CONDITIONS, "{}")

    // Advanced
    val guiPort: Flow<Int> = pref(Keys.GUI_PORT, 8384)

    suspend fun setRunOnBoot(value: Boolean) = set(Keys.RUN_ON_BOOT, value)
    suspend fun setWifiOnly(value: Boolean) = set(Keys.WIFI_ONLY, value)
    suspend fun setAllowMetered(value: Boolean) = set(Keys.ALLOW_METERED, value)
    suspend fun setChargingOnly(value: Boolean) = set(Keys.CHARGING_ONLY, value)
    suspend fun setRespectBatterySaver(value: Boolean) = set(Keys.RESPECT_BATTERY_SAVER, value)
    suspend fun setOnboardingComplete(value: Boolean) = set(Keys.ONBOARDING_COMPLETE, value)
    suspend fun setDeviceName(value: String) = set(Keys.DEVICE_NAME, value)
    suspend fun setNotifySyncComplete(value: Boolean) = set(Keys.NOTIFY_SYNC_COMPLETE, value)
    suspend fun setNotifyDeviceConnected(value: Boolean) = set(Keys.NOTIFY_DEVICE_CONNECTED, value)
    suspend fun setNotifyErrors(value: Boolean) = set(Keys.NOTIFY_ERRORS, value)
    suspend fun setTheme(value: String) = set(Keys.THEME, value)
    suspend fun setActiveProfile(value: String) = set(Keys.ACTIVE_PROFILE, value)
    suspend fun setGuiPort(value: Int) = set(Keys.GUI_PORT, value)

    suspend fun setSchedulerEnabled(value: Boolean) = set(Keys.SCHEDULER_ENABLED, value)
    suspend fun setSchedulerStartHour(value: Int) = set(Keys.SCHEDULER_START_HOUR, value)
    suspend fun setSchedulerEndHour(value: Int) = set(Keys.SCHEDULER_END_HOUR, value)
    suspend fun setFolderConditions(json: String) = set(Keys.FOLDER_CONDITIONS, json)

    private fun pref(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        context.dataStore.data.map { it[key] ?: default }

    private fun pref(key: Preferences.Key<String>, default: String): Flow<String> =
        context.dataStore.data.map { it[key] ?: default }

    private fun pref(key: Preferences.Key<Int>, default: Int): Flow<Int> =
        context.dataStore.data.map { it[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private object Keys {
        val RUN_ON_BOOT = booleanPreferencesKey("run_on_boot")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val ALLOW_METERED = booleanPreferencesKey("allow_metered")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val RESPECT_BATTERY_SAVER = booleanPreferencesKey("respect_battery_saver")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val NOTIFY_SYNC_COMPLETE = booleanPreferencesKey("notify_sync_complete")
        val NOTIFY_DEVICE_CONNECTED = booleanPreferencesKey("notify_device_connected")
        val NOTIFY_ERRORS = booleanPreferencesKey("notify_errors")
        val THEME = stringPreferencesKey("theme")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val GUI_PORT = intPreferencesKey("gui_port")
        val SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val SCHEDULER_START_HOUR = intPreferencesKey("scheduler_start_hour")
        val SCHEDULER_END_HOUR = intPreferencesKey("scheduler_end_hour")
        val FOLDER_CONDITIONS = stringPreferencesKey("folder_conditions")
    }
}
