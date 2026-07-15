package dev.sirulex.syncthing.data

import dev.sirulex.syncthing.api.SyncthingClient
import dev.sirulex.syncthing.api.dto.Folder
import dev.sirulex.syncthing.api.dto.FolderDevice
import dev.sirulex.syncthing.api.dto.FolderStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class FolderRepository(private val client: SyncthingClient) {

    suspend fun folders(): List<Folder> = client.folders()

    suspend fun folderStatus(id: String): FolderStatus = client.folderStatus(id)

    suspend fun addFolder(folder: Folder) = client.addFolder(folder)

    suspend fun updateFolder(folder: Folder) = client.updateFolder(folder)

    suspend fun setFolderDevices(folderId: String, devices: List<FolderDevice>) =
        client.setFolderDevices(folderId, devices)

    fun observeFolders(intervalMs: Long = 3_000): Flow<List<Folder>> = flow {
        while (currentCoroutineContext().isActive) {
            try { emit(client.folders()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(intervalMs)
        }
    }

    fun observeFolderStatus(folderId: String, intervalMs: Long = 3_000): Flow<FolderStatus> = flow {
        while (currentCoroutineContext().isActive) {
            try { emit(client.folderStatus(folderId)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(intervalMs)
        }
    }
}
