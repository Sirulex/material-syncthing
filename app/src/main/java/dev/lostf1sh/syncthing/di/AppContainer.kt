package dev.lostf1sh.syncthing.di

import android.content.Context
import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.events.EventStream
import dev.lostf1sh.syncthing.data.DeviceRepository
import dev.lostf1sh.syncthing.data.EventRepository
import dev.lostf1sh.syncthing.data.FolderRepository
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.data.SystemRepository

class AppContainer(private val appContext: Context) {

    val settingsStore = SettingsStore(appContext)
    val syncConstraints = SyncConstraints(appContext)

    /**
     * Bundle of everything that shares a single SyncthingClient lifetime.
     * Creating repositories here — not in per-call getters — gives the rest
     * of the app a stable single source of truth.
     */
    class ClientHandles internal constructor(
        val client: SyncthingClient,
        val folderRepository: FolderRepository,
        val deviceRepository: DeviceRepository,
        val systemRepository: SystemRepository,
        val eventRepository: EventRepository,
    )

    private val lock = Any()
    @Volatile
    private var handles: ClientHandles? = null

    val client: SyncthingClient? get() = handles?.client
    val folderRepository: FolderRepository? get() = handles?.folderRepository
    val deviceRepository: DeviceRepository? get() = handles?.deviceRepository
    val systemRepository: SystemRepository? get() = handles?.systemRepository
    val eventRepository: EventRepository? get() = handles?.eventRepository

    fun initClient(apiKey: String, port: Int = 8384) {
        synchronized(lock) {
            handles?.let {
                it.eventRepository.stop()
                it.client.close()
            }
            val newClient = SyncthingClient(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = apiKey,
            )
            handles = ClientHandles(
                client = newClient,
                folderRepository = FolderRepository(newClient),
                deviceRepository = DeviceRepository(newClient),
                systemRepository = SystemRepository(newClient),
                eventRepository = EventRepository(EventStream(newClient)),
            )
        }
    }

    fun tearDown() {
        synchronized(lock) {
            handles?.let {
                it.eventRepository.stop()
                it.client.close()
            }
            handles = null
        }
    }
}
