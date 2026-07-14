package dev.lostf1sh.syncthing.di

import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.events.EventStream
import dev.lostf1sh.syncthing.data.DeviceRepository
import dev.lostf1sh.syncthing.data.EventRepository
import dev.lostf1sh.syncthing.data.FolderRepository
import dev.lostf1sh.syncthing.data.SystemRepository

class ClientManager {
    private val lock = Any()

    @Volatile
    private var handles: ClientHandles? = null

    val current: ClientHandles? get() = handles
    val client: SyncthingClient? get() = handles?.client
    val folderRepository: FolderRepository? get() = handles?.folderRepository
    val deviceRepository: DeviceRepository? get() = handles?.deviceRepository
    val systemRepository: SystemRepository? get() = handles?.systemRepository
    val eventRepository: EventRepository? get() = handles?.eventRepository

    fun replace(apiKey: String, port: Int = 8384): ClientHandles = synchronized(lock) {
        closeCurrent()
        val newClient = SyncthingClient(
            baseUrl = "http://127.0.0.1:$port",
            apiKey = apiKey,
        )
        ClientHandles(
            client = newClient,
            folderRepository = FolderRepository(newClient),
            deviceRepository = DeviceRepository(newClient),
            systemRepository = SystemRepository(newClient),
            eventRepository = EventRepository(
                eventStream = EventStream(newClient),
                diskEventStream = EventStream(newClient, EventStream.Source.DISK),
            ),
            port = port,
        ).also { handles = it }
    }

    fun clear() = synchronized(lock) {
        closeCurrent()
        handles = null
    }

    private fun closeCurrent() {
        handles?.let {
            it.eventRepository.stop()
            it.client.close()
        }
    }
}

class ClientHandles internal constructor(
    val client: SyncthingClient,
    val folderRepository: FolderRepository,
    val deviceRepository: DeviceRepository,
    val systemRepository: SystemRepository,
    val eventRepository: EventRepository,
    val port: Int,
)
