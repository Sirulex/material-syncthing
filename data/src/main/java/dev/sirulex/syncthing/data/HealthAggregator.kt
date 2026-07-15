package dev.sirulex.syncthing.data

import dev.sirulex.syncthing.api.dto.Folder
import dev.sirulex.syncthing.api.dto.FolderCompletionInfo
import dev.sirulex.syncthing.api.dto.FolderStatus
import dev.sirulex.syncthing.data.model.SyncHealth
import dev.sirulex.syncthing.data.model.SyncIssue

/**
 * Aggregates folder/device state into a single health snapshot.
 */
object HealthAggregator {

    fun aggregate(
        folders: List<Folder>,
        folderStates: Map<String, String>,
        folderStatuses: Map<String, FolderStatus>,
        folderCompletions: Map<String, FolderCompletionInfo> = emptyMap(),
        deviceCount: Int,
        connectedDevices: Int,
    ): SyncHealth {
        val issues = mutableListOf<SyncIssue>()
        var syncing = 0
        var errors = 0
        var paused = 0

        for (folder in folders) {
            val state = folderStates[folder.id] ?: "unknown"
            val status = folderStatuses[folder.id]

            val remoteSyncing = folderCompletions
                .filterKeys { it.startsWith("${folder.id}:") }
                .values
                .any { it.isIncomplete() }

            when {
                folder.paused -> paused++
                state == "error" -> {
                    errors++
                    issues += SyncIssue(
                        id = "folder-error-${folder.id}",
                        type = SyncIssue.Type.FOLDER_ERROR,
                        folderId = folder.id,
                        message = "Folder error: ${folder.label.ifBlank { folder.id }}",
                    )
                }
                state == "syncing" || remoteSyncing -> syncing++
            }

            if (status != null && status.pullErrors > 0) {
                issues += SyncIssue(
                    id = "pull-errors-${folder.id}",
                    type = SyncIssue.Type.PULL_ERROR,
                    folderId = folder.id,
                    message = "${status.pullErrors} pull error(s) in ${folder.label.ifBlank { folder.id }}",
                )
            }
        }

        val overall = when {
            errors > 0 -> SyncHealth.Status.ERROR
            syncing > 0 -> SyncHealth.Status.SYNCING
            paused == folders.size && folders.isNotEmpty() -> SyncHealth.Status.PAUSED
            folders.any { folderStates[it.id] == "scanning" } -> SyncHealth.Status.SCANNING
            else -> SyncHealth.Status.UP_TO_DATE
        }

        return SyncHealth(
            overall = overall,
            folderCount = folders.size,
            deviceCount = deviceCount,
            connectedDevices = connectedDevices,
            syncingFolders = syncing,
            errorFolders = errors,
            pausedFolders = paused,
            issues = issues,
        )
    }

    private fun FolderCompletionInfo.isIncomplete(): Boolean =
        needBytes > 0 ||
            needItems > 0 ||
            needDeletes > 0 ||
            completion < 99.999
}
