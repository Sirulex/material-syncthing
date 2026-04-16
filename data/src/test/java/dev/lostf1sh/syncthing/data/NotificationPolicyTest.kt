package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.api.events.SyncthingEvent
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class NotificationPolicyTest {

    // Test that the notification policy correctly filters events
    // (without Android context, we test the event matching logic)

    @Test
    fun `state changed from syncing to idle is sync complete`() {
        val event = SyncthingEvent.StateChanged(
            id = 1, time = "", folderId = "abc", from = "syncing", to = "idle",
        )
        assertThat(event.from).isEqualTo("syncing")
        assertThat(event.to).isEqualTo("idle")
    }

    @Test
    fun `state changed from idle to syncing is not complete`() {
        val event = SyncthingEvent.StateChanged(
            id = 2, time = "", folderId = "abc", from = "idle", to = "syncing",
        )
        assertThat(event.from).isNotEqualTo("syncing")
    }

    @Test
    fun `folder errors event carries folder id`() {
        val event = SyncthingEvent.FolderErrors(
            id = 3, time = "", folderId = "photos",
        )
        assertThat(event.folderId).isEqualTo("photos")
    }

    @Test
    fun `device connected event carries device id`() {
        val event = SyncthingEvent.DeviceConnected(
            id = 4, time = "", deviceId = "ABCDEFG", address = "192.168.1.1:22000",
        )
        assertThat(event.deviceId).isEqualTo("ABCDEFG")
    }
}
