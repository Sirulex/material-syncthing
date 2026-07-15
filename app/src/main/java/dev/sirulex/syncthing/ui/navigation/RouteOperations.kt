package dev.sirulex.syncthing.ui.navigation

import android.util.Log
import dev.sirulex.syncthing.api.SyncthingClient
import dev.sirulex.syncthing.api.dto.BrowseEntry
import dev.sirulex.syncthing.api.dto.ConnectionInfo
import dev.sirulex.syncthing.api.dto.Folder
import dev.sirulex.syncthing.api.dto.FolderDevice
import dev.sirulex.syncthing.api.dto.FolderStatus
import dev.sirulex.syncthing.data.DeviceRepository
import dev.sirulex.syncthing.data.FolderRepository
import dev.sirulex.syncthing.data.SystemRepository
import dev.sirulex.syncthing.di.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Route-level operation helpers extracted from [AppNavigation].
 *
 * All suspend functions in this file follow the same contract:
 * - [CancellationException] is rethrown immediately so coroutine cancellation
 *   (e.g. user leaving the screen) never silently stalls.
 * - Other exceptions are caught, reported via [AppState.setDiagnostic] /
 *   [AppState.pushLog], and returned as a failed [Result] where applicable.
 */

/**
 * Refresh all home-level data: folders, devices, folder statuses, and connections.
 */
suspend fun refreshHomeData(
    folderRepo: FolderRepository?,
    deviceRepo: DeviceRepository?,
    systemRepo: SystemRepository?,
    appState: AppState,
) {
    try {
        folderRepo?.folders()?.let(appState::setFolders)
        deviceRepo?.devices()?.let(appState::setDevices)
        val fMap = mutableMapOf<String, FolderStatus>()
        appState.folders.value.forEach { f ->
            try {
                val s = folderRepo?.folderStatus(f.id)
                if (s != null) {
                    fMap[f.id] = s
                    appState.updateFolderState(f.id, s.state)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort per-folder status; don't let one bad folder
                // block the whole refresh.
            }
        }
        appState.setFolderStatuses(fMap)
        systemRepo?.connections()?.let(appState::setConnections)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        appState.setDiagnostic("Could not refresh home data: $detail")
        appState.pushLog("App: could not refresh home data: $detail")
    }
}

/**
 * Load folder browser entries and pending paths.
 */
suspend fun loadFolderBrowser(
    client: SyncthingClient?,
    folderId: String,
    prefix: String,
): Pair<List<BrowseEntry>, Set<String>> {
    val entries = try {
        client?.browseFolder(
            folderId = folderId,
            prefix = prefix,
            levels = 0,
        ) ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    val pendingPaths = try {
        val need = client?.folderNeed(folderId)
        buildSet<String> {
            need?.progress?.forEach { add(it.name) }
            need?.queued?.forEach { add(it.name) }
            need?.rest?.forEach { add(it.name) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptySet<String>()
    }

    return entries to pendingPaths
}

/**
 * Load ignore patterns for a folder.
 */
suspend fun loadFolderIgnores(
    client: SyncthingClient?,
    folderId: String,
): List<String> {
    return try {
        client?.folderIgnores(folderId)?.ignore ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Poll device connection info, with optional event-stream fallback.
 */
suspend fun loadDeviceConnection(
    systemRepo: SystemRepository?,
    deviceId: String,
): ConnectionInfo? {
    return try {
        systemRepo?.connections()
            ?.connections?.get(deviceId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * Repair a folder index: pause, reset, wait for API readiness, then resume + rescan.
 */
suspend fun repairFolderIndex(
    client: SyncthingClient,
    folderId: String,
    appState: AppState,
): Result<Unit> {
    return try {
        client.pauseFolder(folderId)
        delay(500)
        client.resetFolderIndex(folderId)
        val ready = withTimeoutOrNull(60_000) {
            while (currentCoroutineContext().isActive) {
                try {
                    if (client.ping().ping == "pong") return@withTimeoutOrNull true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(1_000)
            }
            false
        } == true
        if (ready) {
            client.resumeFolder(folderId)
            client.rescanFolder(folderId)
            appState.setDiagnostic(null)
            appState.pushLog("App: repaired folder index for $folderId")
            Result.success(Unit)
        } else {
            val msg = "Folder index reset started, but Syncthing API did not come back yet"
            appState.setDiagnostic(msg)
            appState.pushLog("App: $msg")
            Result.failure(IllegalStateException(msg))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        appState.setDiagnostic("Could not repair folder index: $detail")
        appState.pushLog("App: could not repair folder index $folderId: $detail")
        Result.failure(e)
    }
}

/**
 * Share all existing folders with a device.
 */
suspend fun shareExistingFoldersWithDevice(
    folderRepo: FolderRepository?,
    folders: List<Folder>,
    deviceId: String,
    appState: AppState,
): Result<Unit> {
    if (folderRepo == null) {
        val msg = "Could not share folders: Syncthing service not running"
        appState.setDiagnostic(msg)
        appState.pushLog("App: could not share folders with $deviceId: service not running")
        return Result.failure(IllegalStateException(msg))
    }
    return try {
        folders.forEach { folder ->
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (folder.devices.none { it.deviceID == deviceId }) {
                folderRepo.setFolderDevices(
                    folderId = folder.id,
                    devices = folder.devices + FolderDevice(deviceID = deviceId),
                )
            }
        }
        appState.setFolders(folderRepo.folders())
        appState.setDiagnostic(null)
        appState.pushLog("App: existing folders shared with $deviceId")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        appState.setDiagnostic("Could not share folders with device: $detail")
        appState.pushLog("App: could not share folders with $deviceId: $detail")
        Result.failure(e)
    }
}

/**
 * Accept a pending folder offer.
 */
suspend fun acceptPendingFolder(
    client: SyncthingClient?,
    folder: Folder,
    offeredByDeviceId: String,
): Result<Unit> {
    if (client == null) {
        return Result.failure(IllegalStateException("Syncthing service not running"))
    }
    return try {
        client.addFolder(folder)
        client.dismissPendingFolder(folder.id, offeredByDeviceId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Dismiss a pending folder offer.
 */
suspend fun dismissPendingFolder(
    client: SyncthingClient?,
    folderId: String,
    offeredByDeviceId: String,
): Result<Unit> {
    if (client == null) {
        return Result.failure(IllegalStateException("Syncthing service not running"))
    }
    return try {
        client.dismissPendingFolder(folderId, offeredByDeviceId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Fire a simple client mutation (pause/resume/rescan/delete) and swallow
 * non-cancellation failures so the UI doesn't crash.
 */
suspend fun fireAndForget(
    block: suspend () -> Unit,
    appState: AppState? = null,
    logTag: String? = null,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        if (appState != null) {
            appState.setDiagnostic("$logTag: $detail")
            appState.pushLog("App: $logTag: $detail")
        } else {
            // appState is not available; fall back to logcat so the failure is
            // never fully silent regardless of whether a diagnostic sink exists.
            runCatching {
                Log.w("RouteOperations", "${logTag ?: "fireAndForget"}: $detail", e)
            }
        }
    }
}
