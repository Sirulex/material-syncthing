package dev.lostf1sh.syncthing.data

import android.content.Context
import android.util.Log
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

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
    val schedulerStartMinute: Flow<Int> = pref(Keys.SCHEDULER_START_MINUTE, 0)
    val schedulerEndHour: Flow<Int> = pref(Keys.SCHEDULER_END_HOUR, 6)
    val schedulerEndMinute: Flow<Int> = pref(Keys.SCHEDULER_END_MINUTE, 0)

    // Per-folder sync conditions (JSON map)
    val folderConditions: Flow<String> = pref(Keys.FOLDER_CONDITIONS, "{}")

    // Advanced
    val guiPort: Flow<Int> = pref(Keys.GUI_PORT, 8384)
    val biometricEnabled: Flow<Boolean> = pref(Keys.BIOMETRIC_ENABLED, false)

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
    suspend fun setBiometricEnabled(value: Boolean) = set(Keys.BIOMETRIC_ENABLED, value)

    suspend fun setSchedulerEnabled(value: Boolean) = set(Keys.SCHEDULER_ENABLED, value)
    suspend fun setSchedulerStartHour(value: Int) = set(Keys.SCHEDULER_START_HOUR, value)
    suspend fun setSchedulerStartMinute(value: Int) = set(Keys.SCHEDULER_START_MINUTE, value)
    suspend fun setSchedulerEndHour(value: Int) = set(Keys.SCHEDULER_END_HOUR, value)
    suspend fun setSchedulerEndMinute(value: Int) = set(Keys.SCHEDULER_END_MINUTE, value)
    suspend fun setFolderConditions(json: String) = set(Keys.FOLDER_CONDITIONS, json)

    private companion object {
        private const val TAG = "SettingsStore"
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
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val GUI_PORT = intPreferencesKey("gui_port")
        val SCHEDULER_ENABLED = booleanPreferencesKey("scheduler_enabled")
        val SCHEDULER_START_HOUR = intPreferencesKey("scheduler_start_hour")
        val SCHEDULER_START_MINUTE = intPreferencesKey("scheduler_start_minute")
        val SCHEDULER_END_HOUR = intPreferencesKey("scheduler_end_hour")
        val SCHEDULER_END_MINUTE = intPreferencesKey("scheduler_end_minute")
        val FOLDER_CONDITIONS = stringPreferencesKey("folder_conditions")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    /** Export all known preferences as JSON. */
    suspend fun exportToJson(): String {
        val prefs = context.dataStore.data.first()
        val map = buildMap<String, Any?> {
            put("run_on_boot", prefs[Keys.RUN_ON_BOOT] ?: false)
            put("wifi_only", prefs[Keys.WIFI_ONLY] ?: false)
            put("allow_metered", prefs[Keys.ALLOW_METERED] ?: true)
            put("charging_only", prefs[Keys.CHARGING_ONLY] ?: false)
            put("respect_battery_saver", prefs[Keys.RESPECT_BATTERY_SAVER] ?: true)
            put("onboarding_complete", prefs[Keys.ONBOARDING_COMPLETE] ?: false)
            put("device_name", prefs[Keys.DEVICE_NAME] ?: "")
            put("notify_sync_complete", prefs[Keys.NOTIFY_SYNC_COMPLETE] ?: true)
            put("notify_device_connected", prefs[Keys.NOTIFY_DEVICE_CONNECTED] ?: false)
            put("notify_errors", prefs[Keys.NOTIFY_ERRORS] ?: true)
            put("theme", prefs[Keys.THEME] ?: "system")
            put("active_profile", prefs[Keys.ACTIVE_PROFILE] ?: "default")
            put("gui_port", prefs[Keys.GUI_PORT] ?: 8384)
            put("scheduler_enabled", prefs[Keys.SCHEDULER_ENABLED] ?: false)
            put("scheduler_start_hour", prefs[Keys.SCHEDULER_START_HOUR] ?: 23)
            put("scheduler_start_minute", prefs[Keys.SCHEDULER_START_MINUTE] ?: 0)
            put("scheduler_end_hour", prefs[Keys.SCHEDULER_END_HOUR] ?: 6)
            put("scheduler_end_minute", prefs[Keys.SCHEDULER_END_MINUTE] ?: 0)
            put("folder_conditions", prefs[Keys.FOLDER_CONDITIONS] ?: "{}")
            put("biometric_enabled", prefs[Keys.BIOMETRIC_ENABLED] ?: false)
        }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(map.mapValues {
                when (val v = it.value) {
                    is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                    is String -> kotlinx.serialization.json.JsonPrimitive(v)
                    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                }
            })
        )
    }

    /** Import preferences from JSON. */
    suspend fun importFromJson(json: String) {
        val element = try {
            kotlinx.serialization.json.Json.parseToJsonElement(json)
        } catch (e: Exception) {
            Log.w(TAG, "importFromJson: could not parse JSON — aborting import", e)
            return
        }
        val obj = element as? kotlinx.serialization.json.JsonObject
        if (obj == null) {
            Log.w(
                TAG,
                "importFromJson: top-level element is not a JSON object (was ${element::class.simpleName}) — aborting import"
            )
            return
        }
        context.dataStore.edit { prefs ->
            obj["run_on_boot"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.RUN_ON_BOOT] = it }
            obj["wifi_only"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.WIFI_ONLY] = it }
            obj["allow_metered"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.ALLOW_METERED] = it }
            obj["charging_only"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.CHARGING_ONLY] = it }
            obj["respect_battery_saver"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.RESPECT_BATTERY_SAVER] = it }
            obj["onboarding_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.ONBOARDING_COMPLETE] = it }
            obj["device_name"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.DEVICE_NAME] = it }
            obj["notify_sync_complete"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.NOTIFY_SYNC_COMPLETE] = it }
            obj["notify_device_connected"]?.jsonPrimitive?.booleanOrNull?.let {
                prefs[Keys.NOTIFY_DEVICE_CONNECTED] = it
            }
            obj["notify_errors"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.NOTIFY_ERRORS] = it }
            obj["theme"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.THEME] = it }
            obj["active_profile"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.ACTIVE_PROFILE] = it }
            obj["gui_port"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.GUI_PORT] = it }
            obj["scheduler_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.SCHEDULER_ENABLED] = it }
            obj["scheduler_start_hour"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_START_HOUR] = it }
            obj["scheduler_start_minute"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_START_MINUTE] = it }
            obj["scheduler_end_hour"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_END_HOUR] = it }
            obj["scheduler_end_minute"]?.jsonPrimitive?.intOrNull?.let { prefs[Keys.SCHEDULER_END_MINUTE] = it }
            obj["folder_conditions"]?.jsonPrimitive?.contentOrNull?.let { prefs[Keys.FOLDER_CONDITIONS] = it }
            obj["biometric_enabled"]?.jsonPrimitive?.booleanOrNull?.let { prefs[Keys.BIOMETRIC_ENABLED] = it }
        }
    }
}
