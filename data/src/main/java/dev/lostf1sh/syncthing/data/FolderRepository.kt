package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FolderRepository(private val client: SyncthingClient) {

    suspend fun folders(): List<Folder> = client.folders()

    suspend fun folderStatus(id: String): FolderStatus = client.folderStatus(id)

    suspend fun addFolder(folder: Folder) = client.addFolder(folder)

    suspend fun updateFolder(folder: Folder) = client.updateFolder(folder)

    fun observeFolders(intervalMs: Long = 3_000): Flow<List<Folder>> = flow {
        while (true) {
            try { emit(client.folders()) } catch (_: Exception) { }
            delay(intervalMs)
        }
    }

    fun observeFolderStatus(folderId: String, intervalMs: Long = 3_000): Flow<FolderStatus> = flow {
        while (true) {
            try { emit(client.folderStatus(folderId)) } catch (_: Exception) { }
            delay(intervalMs)
        }
    }
}
