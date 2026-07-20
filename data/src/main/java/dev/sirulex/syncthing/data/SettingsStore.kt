package dev.sirulex.syncthing.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsUiState(
    val runOnBoot: Boolean = false,
    val wifiOnly: Boolean = false,
    val allowMetered: Boolean = true,
    val chargingOnly: Boolean = false,
    val respectBatterySaver: Boolean = true,
    val notifySyncComplete: Boolean = true,
    val notifyDeviceConnected: Boolean = false,
    val schedulerEnabled: Boolean = false,
    val schedulerStartHour: Int = 23,
    val schedulerStartMinute: Int = 0,
    val schedulerEndHour: Int = 6,
    val schedulerEndMinute: Int = 0,
    val notifyErrors: Boolean = true,
    val theme: String = "system",
    val biometricEnabled: Boolean = false,
    val hideSyncingCard: Boolean = false,
)

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
    val hideSyncingCard: Flow<Boolean> = pref(Keys.HIDE_SYNCING_CARD, false)

    // Sync profiles
    val activeProfile: Flow<String> = pref(Keys.ACTIVE_PROFILE, "default")

    // Scheduler (time range when syncing is allowed)
    val schedulerEnabled: Flow<Boolean> = pref(Keys.SCHEDULER_ENABLED, false)
    val schedulerStartHour: Flow<Int> = pref(Keys.SCHEDULER_START_HOUR, 23)
    val schedulerStartMinute: Flow<Int> = pref(Keys.SCHEDULER_START_MINUTE, 0)
    val schedulerEndHour: Flow<Int> = pref(Keys.SCHEDULER_END_HOUR, 6)
    val schedulerEndMinute: Flow<Int> = pref(Keys.SCHEDULER_END_MINUTE, 0)

    // Per-folder sync conditions (JSON map)
    val folderConditions: Flow<String> = pref(Keys.FOLDER_CONDITIONS, "{}")

    // Advanced
    val guiPort: Flow<Int> = pref(Keys.GUI_PORT, 8384)
    val biometricEnabled: Flow<Boolean> = pref(Keys.BIOMETRIC_ENABLED, false)
    val startSuppressedByUser: Flow<Boolean> = pref(Keys.START_SUPPRESSED_BY_USER, false)
    val settingsUiState: Flow<SettingsUiState> = context.dataStore.data.map { prefs ->
        SettingsUiState(
            runOnBoot = prefs[Keys.RUN_ON_BOOT] ?: false,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            allowMetered = prefs[Keys.ALLOW_METERED] ?: true,
            chargingOnly = prefs[Keys.CHARGING_ONLY] ?: false,
            respectBatterySaver = prefs[Keys.RESPECT_BATTERY_SAVER] ?: true,
            notifySyncComplete = prefs[Keys.NOTIFY_SYNC_COMPLETE] ?: true,
            notifyDeviceConnected = prefs[Keys.NOTIFY_DEVICE_CONNECTED] ?: false,
            schedulerEnabled = prefs[Keys.SCHEDULER_ENABLED] ?: false,
            schedulerStartHour = prefs[Keys.SCHEDULER_START_HOUR] ?: 23,
            schedulerStartMinute = prefs[Keys.SCHEDULER_START_MINUTE] ?: 0,
            schedulerEndHour = prefs[Keys.SCHEDULER_END_HOUR] ?: 6,
            schedulerEndMinute = prefs[Keys.SCHEDULER_END_MINUTE] ?: 0,
            notifyErrors = prefs[Keys.NOTIFY_ERRORS] ?: true,
            theme = prefs[Keys.THEME] ?: "system",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            hideSyncingCard = prefs[Keys.HIDE_SYNCING_CARD] ?: false,
        )
    }

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
    suspend fun setHideSyncingCard(value: Boolean) = set(Keys.HIDE_SYNCING_CARD, value)
    suspend fun setActiveProfile(value: String) = set(Keys.ACTIVE_PROFILE, value)
    suspend fun setGuiPort(value: Int) = set(Keys.GUI_PORT, value)
    suspend fun setBiometricEnabled(value: Boolean) = set(Keys.BIOMETRIC_ENABLED, value)
    suspend fun setStartSuppressedByUser(value: Boolean) = set(Keys.START_SUPPRESSED_BY_USER, value)

    suspend fun setSchedulerEnabled(value: Boolean) = set(Keys.SCHEDULER_ENABLED, value)
    suspend fun setSchedulerStartHour(value: Int) = set(Keys.SCHEDULER_START_HOUR, value)
    suspend fun setSchedulerStartMinute(value: Int) = set(Keys.SCHEDULER_START_MINUTE, value)
    suspend fun setSchedulerEndHour(value: Int) = set(Keys.SCHEDULER_END_HOUR, value)
    suspend fun setSchedulerEndMinute(value: Int) = set(Keys.SCHEDULER_END_MINUTE, value)
    suspend fun setFolderConditions(json: String) = set(Keys.FOLDER_CONDITIONS, json)

    /** Updates one folder without overwriting condition changes written concurrently. */
    suspend fun setFolderCondition(folderId: String, condition: FolderCondition) {
        context.dataStore.edit { prefs ->
            val current = parseFolderConditions(prefs[Keys.FOLDER_CONDITIONS] ?: "{}")
            prefs[Keys.FOLDER_CONDITIONS] = serializeFolderConditions(
                current + (folderId to condition),
            )
        }
    }

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
        val HIDE_SYNCING_CARD = booleanPreferencesKey("hide_syncing_card")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val GUI_PORT = intPreferencesKey("gui_port")
        val SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val SCHEDULER_START_HOUR = intPreferencesKey("scheduler_start_hour")
        val SCHEDULER_START_MINUTE = intPreferencesKey("scheduler_start_minute")
        val SCHEDULER_END_HOUR = intPreferencesKey("scheduler_end_hour")
        val SCHEDULER_END_MINUTE = intPreferencesKey("scheduler_end_minute")
        val FOLDER_CONDITIONS = stringPreferencesKey("folder_conditions")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val START_SUPPRESSED_BY_USER = booleanPreferencesKey("start_suppressed_by_user")
    }

    /** Export all known preferences as a versioned JSON document. */
    suspend fun exportToJson(): String =
        SettingsBackupCodec.encodeSettings(exportPreferences())

    suspend fun exportPreferences(): JsonObject {
        val prefs = context.dataStore.data.first()
        return JsonObject(buildMap {
            put("run_on_boot", JsonPrimitive(prefs[Keys.RUN_ON_BOOT] ?: false))
            put("wifi_only", JsonPrimitive(prefs[Keys.WIFI_ONLY] ?: false))
            put("allow_metered", JsonPrimitive(prefs[Keys.ALLOW_METERED] ?: true))
            put("charging_only", JsonPrimitive(prefs[Keys.CHARGING_ONLY] ?: false))
            put("respect_battery_saver", JsonPrimitive(prefs[Keys.RESPECT_BATTERY_SAVER] ?: true))
            put("onboarding_complete", JsonPrimitive(prefs[Keys.ONBOARDING_COMPLETE] ?: false))
            put("device_name", JsonPrimitive(prefs[Keys.DEVICE_NAME] ?: ""))
            put("notify_sync_complete", JsonPrimitive(prefs[Keys.NOTIFY_SYNC_COMPLETE] ?: true))
            put("notify_device_connected", JsonPrimitive(prefs[Keys.NOTIFY_DEVICE_CONNECTED] ?: false))
            put("notify_errors", JsonPrimitive(prefs[Keys.NOTIFY_ERRORS] ?: true))
            put("theme", JsonPrimitive(prefs[Keys.THEME] ?: "system"))
            put("hide_syncing_card", JsonPrimitive(prefs[Keys.HIDE_SYNCING_CARD] ?: false))
            put("active_profile", JsonPrimitive(prefs[Keys.ACTIVE_PROFILE] ?: "default"))
            put("gui_port", JsonPrimitive(prefs[Keys.GUI_PORT] ?: 8384))
            put("scheduler_enabled", JsonPrimitive(prefs[Keys.SCHEDULER_ENABLED] ?: false))
            put("scheduler_start_hour", JsonPrimitive(prefs[Keys.SCHEDULER_START_HOUR] ?: 23))
            put("scheduler_start_minute", JsonPrimitive(prefs[Keys.SCHEDULER_START_MINUTE] ?: 0))
            put("scheduler_end_hour", JsonPrimitive(prefs[Keys.SCHEDULER_END_HOUR] ?: 6))
            put("scheduler_end_minute", JsonPrimitive(prefs[Keys.SCHEDULER_END_MINUTE] ?: 0))
            put("folder_conditions", JsonPrimitive(prefs[Keys.FOLDER_CONDITIONS] ?: "{}"))
            put("biometric_enabled", JsonPrimitive(prefs[Keys.BIOMETRIC_ENABLED] ?: false))
            put("start_suppressed_by_user", JsonPrimitive(prefs[Keys.START_SUPPRESSED_BY_USER] ?: false))
        })
    }

    /** Import preferences from JSON and return the number of applied values. */
    suspend fun importFromJson(json: String): Int =
        importPreferences(SettingsBackupCodec.decodeSettings(json))

    suspend fun importPreferences(input: JsonObject): Int {
        val obj = SettingsBackupCodec.sanitizePreferences(input)
        var imported = 0
        context.dataStore.edit { prefs ->
            fun applied() { imported += 1 }
            obj["run_on_boot"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.RUN_ON_BOOT] = it; applied() }
            obj["wifi_only"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.WIFI_ONLY] = it; applied() }
            obj["allow_metered"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.ALLOW_METERED] = it; applied() }
            obj["charging_only"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.CHARGING_ONLY] = it; applied() }
            obj["respect_battery_saver"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.RESPECT_BATTERY_SAVER] = it; applied() }
            obj["onboarding_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.ONBOARDING_COMPLETE] = it; applied() }
            obj["device_name"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.DEVICE_NAME] = it; applied() }
            obj["notify_sync_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.NOTIFY_SYNC_COMPLETE] = it; applied() }
            obj["notify_device_connected"]?.jsonPrimitive?.booleanOrNull?.let {
                prefs[Keys.NOTIFY_DEVICE_CONNECTED] = it; applied()
            }
            obj["notify_errors"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.NOTIFY_ERRORS] = it; applied() }
            obj["theme"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.THEME] = it; applied() }
            obj["hide_syncing_card"]?.jsonPrimitive?.booleanOrNull?.let {
                prefs[Keys.HIDE_SYNCING_CARD] = it; applied()
            }
            obj["active_profile"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.ACTIVE_PROFILE] = it; applied() }
            obj["gui_port"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.GUI_PORT] = it; applied() }
            obj["scheduler_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.SCHEDULER_ENABLED] = it; applied() }
            obj["scheduler_start_hour"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_START_HOUR] = it; applied() }
            obj["scheduler_start_minute"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_START_MINUTE] = it; applied() }
            obj["scheduler_end_hour"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_END_HOUR] = it; applied() }
            obj["scheduler_end_minute"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_END_MINUTE] = it; applied() }
            obj["folder_conditions"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.FOLDER_CONDITIONS] = it; applied() }
            obj["biometric_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.BIOMETRIC_ENABLED] = it; applied() }
            obj["start_suppressed_by_user"]?.jsonPrimitive?.booleanOrNull?.let {
                prefs[Keys.START_SUPPRESSED_BY_USER] = it; applied()
            }
        }
        return imported
    }
}
