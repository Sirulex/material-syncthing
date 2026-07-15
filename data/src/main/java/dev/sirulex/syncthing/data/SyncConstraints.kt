// Ported from Catfriend1/syncthing-android (MPL-2.0): service/RunConditionMonitor.java
// Rewritten with NetworkCallback + Flow instead of BroadcastReceiver polling.
package dev.sirulex.syncthing.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.Calendar

/**
 * Monitors device state (network, charging, battery saver) and
 * emits whether Syncthing should run based on user preferences.
 *
 * Ported from Catfriend1 RunConditionMonitor — key condition checks
 * preserved, converted from BroadcastReceiver to NetworkCallback + Flow.
 */
class SyncConstraints(private val context: Context) {

    companion object {
        private const val TAG = "SyncConstraints"
    }

    sealed interface ConstraintState {
        data object ShouldRun : ConstraintState
        data class ShouldPause(val reason: String) : ConstraintState
    }

    // Named intermediate types used by the nested combine pipeline below.
    // Keeping them private prevents them from leaking into the public API and
    // makes the combine lambdas fully type-safe — no Array<Any?> or unchecked
    // casts, so reordering combine arguments produces a compile error instead
    // of a silent wrong-type cast at runtime.
    private data class RunConditions(
        val wifiOnly: Boolean,
        val allowMetered: Boolean,
        val chargingOnly: Boolean,
        val respectBatterySaver: Boolean,
    )

    private data class SchedulerConditions(
        val enabled: Boolean,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
    )

    private data class BatteryState(
        val charging: Boolean,
        val batterySaver: Boolean,
    )

    /**
     * Emits constraint decisions based on current device state + preferences.
     *
     * Uses nested typed [combine] calls (max 5 arguments each) so every lambda
     * parameter has a concrete compile-time type.  The previous single
     * 13-argument [combine] required [Array]<[Any]?> + unchecked casts; any
     * reordering of arguments would have silently cast the wrong type.
     */
    fun observe(settings: SettingsStore): Flow<ConstraintState> {
        val runConditions = combine(
            settings.wifiOnly,
            settings.allowMetered,
            settings.chargingOnly,
            settings.respectBatterySaver,
        ) { wifiOnly, allowMetered, chargingOnly, respectBatterySaver ->
            RunConditions(wifiOnly, allowMetered, chargingOnly, respectBatterySaver)
        }

        val schedulerConditions = combine(
            settings.schedulerEnabled,
            settings.schedulerStartHour,
            settings.schedulerStartMinute,
            settings.schedulerEndHour,
            settings.schedulerEndMinute,
        ) { enabled, startHour, startMinute, endHour, endMinute ->
            SchedulerConditions(enabled, startHour, startMinute, endHour, endMinute)
        }

        val batteryState = combine(
            observeCharging(),
            observeBatterySaver(),
        ) { charging, batterySaver ->
            BatteryState(charging, batterySaver)
        }

        // Five arguments — fits the typed combine(F1,F2,F3,F4,F5) overload.
        return combine(
            observeNetwork(),
            batteryState,
            runConditions,
            schedulerConditions,
            observeCurrentMinuteOfDay(),
        ) { network, battery, conditions, scheduler, currentMinuteOfDay ->
            decide(
                network = network,
                wifiOnly = conditions.wifiOnly,
                allowMetered = conditions.allowMetered,
                chargingOnly = conditions.chargingOnly,
                respectBatterySaver = conditions.respectBatterySaver,
                schedulerEnabled = scheduler.enabled,
                schedulerStartHour = scheduler.startHour,
                schedulerStartMinute = scheduler.startMinute,
                schedulerEndHour = scheduler.endHour,
                schedulerEndMinute = scheduler.endMinute,
                charging = battery.charging,
                batterySaver = battery.batterySaver,
                currentMinuteOfDay = currentMinuteOfDay,
            )
        }.distinctUntilChanged()
    }

    private fun decide(
        network: NetworkState,
        wifiOnly: Boolean,
        allowMetered: Boolean,
        chargingOnly: Boolean,
        respectBatterySaver: Boolean,
        schedulerEnabled: Boolean = false,
        schedulerStartHour: Int = 23,
        schedulerStartMinute: Int = 0,
        schedulerEndHour: Int = 6,
        schedulerEndMinute: Int = 0,
        charging: Boolean,
        batterySaver: Boolean,
        currentMinuteOfDay: Int,
    ): ConstraintState {
        // Battery saver check
        if (respectBatterySaver && batterySaver) {
            return ConstraintState.ShouldPause("Battery saver active")
        }

        // Charging check
        if (chargingOnly && !charging) {
            return ConstraintState.ShouldPause("Not charging")
        }

        // Scheduler check. Equal start/end is a zero-width interval, so it
        // intentionally never matches instead of meaning "all day".
        if (schedulerEnabled) {
            val startMinuteOfDay = (schedulerStartHour.coerceIn(0, 23) * 60) + schedulerStartMinute.coerceIn(0, 59)
            val endMinuteOfDay = (schedulerEndHour.coerceIn(0, 23) * 60) + schedulerEndMinute.coerceIn(0, 59)
            val inRange = schedulerWindowContains(
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                currentMinuteOfDay = currentMinuteOfDay,
            )
            if (!inRange) {
                return ConstraintState.ShouldPause(
                    "Outside scheduled hours (${formatTime(schedulerStartHour, schedulerStartMinute)} - ${
                        formatTime(
                            schedulerEndHour,
                            schedulerEndMinute
                        )
                    })"
                )
            }
        }

        // Network checks
        if (!network.connected) {
            return ConstraintState.ShouldPause("No network")
        }

        if (wifiOnly && !network.isWifi) {
            return ConstraintState.ShouldPause("Wi-Fi required")
        }

        // Metered check applies on any transport, not just Wi-Fi. Cellular is
        // almost always metered, and allowMetered is the user's opt-in for it.
        if (network.isMetered && !allowMetered) {
            return ConstraintState.ShouldPause("Metered connection")
        }

        return ConstraintState.ShouldRun
    }

    private data class NetworkState(
        val connected: Boolean = false,
        val isWifi: Boolean = false,
        val isMetered: Boolean = false,
    )

    private fun observeNetwork(): Flow<NetworkState> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val state = NetworkState(
                    connected = true,
                    isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                )
                trySend(state)
            }

            override fun onLost(network: Network) {
                trySend(NetworkState(connected = false))
            }
        }

        // Listen to the DEFAULT network only. A broad INTERNET-capability callback
        // races against every qualifying network (Wi-Fi + LTE + IMS on phones
        // with mobile data), and whichever emits last wins — that previously
        // misreported isWifi=false even on Wi-Fi, tripping "Wi-Fi required".
        cm.registerDefaultNetworkCallback(callback)

        // Seed an initial state from the current default, since the first
        // callback is asynchronous.
        val activeNetwork = cm.activeNetwork
        val activeCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        send(
            if (activeCaps != null) {
                NetworkState(
                    connected = true,
                    isWifi = activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    isMetered = !activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                )
            } else {
                NetworkState(connected = false)
            }
        )

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    private fun observeCharging(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isCharging())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        trySend(isCharging())
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    private fun observeBatterySaver(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isBatterySaverOn())
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        )
        trySend(isBatterySaverOn())
        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()

    private fun observeCurrentMinuteOfDay(): Flow<Int> = flow {
        while (currentCoroutineContext().isActive) {
            val now = Calendar.getInstance()
            emit(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private fun formatTime(hour: Int, minute: Int): String {
        return "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun isBatterySaverOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }
}

internal fun schedulerWindowContains(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    currentMinuteOfDay: Int,
): Boolean = when {
    startMinuteOfDay < endMinuteOfDay -> currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
    startMinuteOfDay > endMinuteOfDay -> currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay
    // Zero-width interval means never.
    else -> false
}
