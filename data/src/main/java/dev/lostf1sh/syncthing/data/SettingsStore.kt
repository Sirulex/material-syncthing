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

    // Appearance
    val theme: Flow<String> = pref(Keys.THEME, "system")

    // Advanced
    val guiPort: Flow<Int> = pref(Keys.GUI_PORT, 8384)

    suspend fun setRunOnBoot(value: Boolean) = set(Keys.RUN_ON_BOOT, value)
    suspend fun setWifiOnly(value: Boolean) = set(Keys.WIFI_ONLY, value)
    suspend fun setAllowMetered(value: Boolean) = set(Keys.ALLOW_METERED, value)
    suspend fun setChargingOnly(value: Boolean) = set(Keys.CHARGING_ONLY, value)
    suspend fun setRespectBatterySaver(value: Boolean) = set(Keys.RESPECT_BATTERY_SAVER, value)
    suspend fun setTheme(value: String) = set(Keys.THEME, value)
    suspend fun setGuiPort(value: Int) = set(Keys.GUI_PORT, value)

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
        val THEME = stringPreferencesKey("theme")
        val GUI_PORT = intPreferencesKey("gui_port")
    }
}
