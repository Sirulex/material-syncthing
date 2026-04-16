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

    // Lazily created when service provides apiKey
    private var _client: SyncthingClient? = null
    val client: SyncthingClient? get() = _client

    val folderRepository: FolderRepository? get() = _client?.let { FolderRepository(it) }
    val deviceRepository: DeviceRepository? get() = _client?.let { DeviceRepository(it) }
    val systemRepository: SystemRepository? get() = _client?.let { SystemRepository(it) }

    private var _eventRepository: EventRepository? = null
    val eventRepository: EventRepository? get() = _eventRepository

    fun initClient(apiKey: String, port: Int = 8384) {
        _eventRepository?.stop()
        _client?.close()
        val newClient = SyncthingClient(
            baseUrl = "http://127.0.0.1:$port",
            apiKey = apiKey,
        )
        _client = newClient
        _eventRepository = EventRepository(EventStream(newClient))
    }

    fun tearDown() {
        _eventRepository?.stop()
        _eventRepository = null
        _client?.close()
        _client = null
    }
}
