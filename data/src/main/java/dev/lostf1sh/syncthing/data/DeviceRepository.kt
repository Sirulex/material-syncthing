package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.dto.Device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class DeviceRepository(private val client: SyncthingClient) {

    suspend fun devices(): List<Device> = client.devices()

    suspend fun addDevice(device: Device) = client.addDevice(device)

    suspend fun updateDevice(device: Device) = client.updateDevice(device)

    suspend fun pauseDevice(id: String) = client.pauseDevice(id)

    suspend fun resumeDevice(id: String) = client.resumeDevice(id)

    fun observeDevices(intervalMs: Long = 3_000): Flow<List<Device>> = flow {
        while (currentCoroutineContext().isActive) {
            try { emit(client.devices()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(intervalMs)
        }
    }
}
