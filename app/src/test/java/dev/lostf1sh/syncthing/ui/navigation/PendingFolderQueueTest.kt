package dev.lostf1sh.syncthing.ui.navigation

import com.google.common.truth.Truth.assertThat
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.PendingFolder
import dev.lostf1sh.syncthing.api.dto.PendingFolderInfo
import org.junit.Test

class PendingFolderQueueTest {

    private val offers = linkedMapOf(
        "photos" to PendingFolder(
            offeredBy = mapOf("remote-a" to PendingFolderInfo(label = "Photos")),
        ),
        "documents" to PendingFolder(
            offeredBy = mapOf("remote-a" to PendingFolderInfo(label = "Documents")),
        ),
        "music" to PendingFolder(
            offeredBy = mapOf("remote-a" to PendingFolderInfo(label = "Music")),
        ),
    )

    @Test
    fun `returns pending folder offers one after another`() {
        val devices = listOf(Device(deviceID = "remote-a", name = "Laptop"))
        val first = nextPendingFolderOffer(offers, devices, emptySet())!!
        val second = nextPendingFolderOffer(offers, devices, setOf(first.folderId))!!
        val third = nextPendingFolderOffer(
            offers,
            devices,
            setOf(first.folderId, second.folderId),
        )!!

        assertThat(listOf(first.folderId, second.folderId, third.folderId))
            .containsExactly("photos", "documents", "music")
            .inOrder()
        assertThat(first.offeredByName).isEqualTo("Laptop")
    }

    @Test
    fun `does not offer the same folder id twice for different devices`() {
        val sharedOffer = mapOf(
            "shared" to PendingFolder(
                offeredBy = linkedMapOf(
                    "remote-a" to PendingFolderInfo(label = "Shared"),
                    "remote-b" to PendingFolderInfo(label = "Shared"),
                ),
            ),
        )
        val first = nextPendingFolderOffer(sharedOffer, emptyList(), emptySet())!!
        val next = nextPendingFolderOffer(sharedOffer, emptyList(), setOf(first.folderId))

        assertThat(first.offeredByDevice).isEqualTo("remote-a")
        assertThat(next).isNull()
    }

    @Test
    fun `skips empty pending entries instead of blocking later offers`() {
        val withEmptyEntry = linkedMapOf(
            "empty" to PendingFolder(),
            "documents" to offers.getValue("documents"),
        )

        val next = nextPendingFolderOffer(withEmptyEntry, emptyList(), emptySet())

        assertThat(next?.folderId).isEqualTo("documents")
    }
}
