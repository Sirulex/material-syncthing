// Ported from Catfriend1/syncthing-android (MPL-2.0): service/RunConditionMonitor.java
// Rewritten with NetworkCallback + Flow instead of BroadcastReceiver polling.
package dev.lostf1sh.syncthing.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

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

    /**
     * Emits constraint decisions based on current device state + preferences.
     */
    fun observe(settings: SettingsStore): Flow<ConstraintState> {
        return combine(
            observeNetwork(),
            settings.wifiOnly,
            settings.allowMetered,
            settings.chargingOnly,
            settings.respectBatterySaver,
        ) { networkState, wifiOnly, allowMetered, chargingOnly, respectBatterySaver ->
            decide(networkState, wifiOnly, allowMetered, chargingOnly, respectBatterySaver)
        }.distinctUntilChanged()
    }

    private fun decide(
        network: NetworkState,
        wifiOnly: Boolean,
        allowMetered: Boolean,
        chargingOnly: Boolean,
        respectBatterySaver: Boolean,
    ): ConstraintState {
        // Battery saver check
        if (respectBatterySaver && isBatterySaverOn()) {
            return ConstraintState.ShouldPause("Battery saver active")
        }

        // Charging check
        if (chargingOnly && !isCharging()) {
            return ConstraintState.ShouldPause("Not charging")
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

    private fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }

    private fun isBatterySaverOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }
}
