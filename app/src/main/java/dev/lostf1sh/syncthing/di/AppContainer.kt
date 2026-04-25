package dev.lostf1sh.syncthing.di

import android.content.Context
import dev.lostf1sh.syncthing.data.NotificationPolicy
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.native.NativeLauncher
import dev.lostf1sh.syncthing.work.SchedulerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(private val appContext: Context) {

    val settingsStore = SettingsStore(appContext)
    val syncConstraints = SyncConstraints(appContext)
    val appState = AppState()

    private val nativeLauncher = NativeLauncher.fromContext(appContext)
    private val notificationPolicy = NotificationPolicy(appContext)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clientManager = ClientManager()
    private val bandwidthTracker = BandwidthTracker(appState)
    private val collectorManager = CollectorManager(
        appContext = appContext,
        appScope = appScope,
        appState = appState,
        settingsStore = settingsStore,
        notificationPolicy = notificationPolicy,
        bandwidthTracker = bandwidthTracker,
    )

    val client get() = clientManager.client
    val folderRepository get() = clientManager.folderRepository
    val deviceRepository get() = clientManager.deviceRepository
    val systemRepository get() = clientManager.systemRepository
    val eventRepository get() = clientManager.eventRepository

    init {
        appScope.launch(Dispatchers.IO) {
            try {
                appState.setLocalDeviceId(nativeLauncher.getDeviceId())
            } catch (_: Exception) {
            }
        }
        appScope.launch {
            settingsStore.notifySyncComplete.collect {
                notificationPolicy.notifySyncComplete = it
            }
        }
        appScope.launch {
            settingsStore.notifyDeviceConnected.collect {
                notificationPolicy.notifyDeviceConnected = it
            }
        }
        appScope.launch {
            settingsStore.notifyErrors.collect {
                notificationPolicy.notifyErrors = it
            }
        }
        appScope.launch {
            settingsStore.schedulerEnabled.collect { enabled ->
                if (enabled) SchedulerWorker.enqueue(appContext)
                else SchedulerWorker.cancel(appContext)
            }
        }
    }

    fun initClient(apiKey: String, port: Int = 8384) {
        val handles = clientManager.replace(apiKey, port)
        collectorManager.start(handles)
    }

    fun tearDown() {
        collectorManager.stop()
        clientManager.clear()
        bandwidthTracker.reset()
        appState.reset()
    }
}
