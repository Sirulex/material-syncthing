package dev.sirulex.syncthing.data

import dev.sirulex.syncthing.api.SyncthingClient
import dev.sirulex.syncthing.api.dto.Connections
import dev.sirulex.syncthing.api.dto.SystemStatus
import dev.sirulex.syncthing.api.dto.SystemVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SystemRepository(private val client: SyncthingClient) {

    suspend fun ping(): Boolean = try {
        client.ping().ping == "pong"
    } catch (e: CancellationException) {
        throw e
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
        while (currentCoroutineContext().isActive) {
            try {
                emit(client.systemVersion())
            } catch (e: CancellationException) {
                throw e
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
        while (currentCoroutineContext().isActive) {
            try {
                emit(client.connections())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
            delay(intervalMs)
        }
    }
}
