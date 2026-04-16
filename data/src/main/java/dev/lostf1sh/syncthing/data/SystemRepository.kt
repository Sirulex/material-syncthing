package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.dto.Connections
import dev.lostf1sh.syncthing.api.dto.SystemStatus
import dev.lostf1sh.syncthing.api.dto.SystemVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SystemRepository(private val client: SyncthingClient) {

    suspend fun ping(): Boolean = try {
        client.ping().ping == "pong"
    } catch (_: Exception) {
        false
    }

    suspend fun status(): SystemStatus = client.systemStatus()

    suspend fun version(): SystemVersion = client.systemVersion()

    suspend fun connections(): Connections = client.connections()

    /**
     * Polls system version every [intervalMs] and emits updates.
     * Replaced by events in Phase 7.
     */
    fun observeVersion(intervalMs: Long = 3_000): Flow<SystemVersion> = flow {
        while (true) {
            try {
                emit(client.systemVersion())
            } catch (_: Exception) {
                // Service may not be ready yet
            }
            delay(intervalMs)
        }
    }

    /**
     * Polls connections every [intervalMs].
     */
    fun observeConnections(intervalMs: Long = 3_000): Flow<Connections> = flow {
        while (true) {
            try {
                emit(client.connections())
            } catch (_: Exception) { }
            delay(intervalMs)
        }
    }
}
