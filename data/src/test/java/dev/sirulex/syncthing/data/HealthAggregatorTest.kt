package dev.sirulex.syncthing.data

import dev.sirulex.syncthing.api.dto.Folder
import dev.sirulex.syncthing.api.dto.FolderCompletionInfo
import dev.sirulex.syncthing.api.dto.FolderStatus
import dev.sirulex.syncthing.data.model.SyncHealth
import dev.sirulex.syncthing.data.model.SyncIssue
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class HealthAggregatorTest {

    @Test
    fun `empty state returns up to date`() {
        val health = HealthAggregator.aggregate(
            folders = emptyList(),
            folderStates = emptyMap(),
            folderStatuses = emptyMap(),
            deviceCount = 0,
            connectedDevices = 0,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.UP_TO_DATE)
        assertThat(health.issues).isEmpty()
    }

    @Test
    fun `syncing folder sets status to syncing`() {
        val folder = Folder(id = "abc", label = "Music")
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = mapOf("abc" to "syncing"),
            folderStatuses = mapOf("abc" to FolderStatus(state = "syncing")),
            deviceCount = 1,
            connectedDevices = 1,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.SYNCING)
        assertThat(health.syncingFolders).isEqualTo(1)
    }

    @Test
    fun `error folder creates issue`() {
        val folder = Folder(id = "xyz", label = "Photos")
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = mapOf("xyz" to "error"),
            folderStatuses = emptyMap(),
            deviceCount = 2,
            connectedDevices = 1,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.ERROR)
        assertThat(health.errorFolders).isEqualTo(1)
        assertThat(health.issues).hasSize(1)
        assertThat(health.issues[0].type).isEqualTo(SyncIssue.Type.FOLDER_ERROR)
    }

    @Test
    fun `pull errors create issues`() {
        val folder = Folder(id = "f1", label = "Docs")
        val status = FolderStatus(state = "idle", pullErrors = 3)
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = mapOf("f1" to "idle"),
            folderStatuses = mapOf("f1" to status),
            deviceCount = 1,
            connectedDevices = 1,
        )
        assertThat(health.issues).hasSize(1)
        assertThat(health.issues[0].type).isEqualTo(SyncIssue.Type.PULL_ERROR)
        assertThat(health.issues[0].message).contains("3 pull error(s)")
    }

    @Test
    fun `all paused shows paused status`() {
        val folder = Folder(id = "f1", paused = true)
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = emptyMap(),
            folderStatuses = emptyMap(),
            deviceCount = 1,
            connectedDevices = 0,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.PAUSED)
        assertThat(health.pausedFolders).isEqualTo(1)
    }

    @Test
    fun `scanning folder detected`() {
        val folder = Folder(id = "f1")
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = mapOf("f1" to "scanning"),
            folderStatuses = emptyMap(),
            deviceCount = 1,
            connectedDevices = 1,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.SCANNING)
    }

    @Test
    fun `remote incomplete folder sets status to syncing`() {
        val folder = Folder(id = "f1")
        val health = HealthAggregator.aggregate(
            folders = listOf(folder),
            folderStates = mapOf("f1" to "idle"),
            folderStatuses = mapOf("f1" to FolderStatus(state = "idle")),
            folderCompletions = mapOf(
                "f1:device-a" to FolderCompletionInfo(
                    completion = 0.1,
                    needBytes = 14_000_000_000,
                    needItems = 712,
                )
            ),
            deviceCount = 2,
            connectedDevices = 1,
        )
        assertThat(health.overall).isEqualTo(SyncHealth.Status.SYNCING)
        assertThat(health.syncingFolders).isEqualTo(1)
    }

    @Test
    fun `device counts propagated`() {
        val health = HealthAggregator.aggregate(
            folders = emptyList(),
            folderStates = emptyMap(),
            folderStatuses = emptyMap(),
            deviceCount = 5,
            connectedDevices = 3,
        )
        assertThat(health.deviceCount).isEqualTo(5)
        assertThat(health.connectedDevices).isEqualTo(3)
    }
}
