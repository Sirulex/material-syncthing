package dev.sirulex.syncthing.ui.folders

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentTreePathTest {

    @Test
    fun `resolves primary storage document`() {
        assertThat(documentTreePath("primary:Sync/Photos", "/storage/emulated/0"))
            .isEqualTo("/storage/emulated/0/Sync/Photos")
    }

    @Test
    fun `resolves removable storage document`() {
        assertThat(documentTreePath("1234-ABCD:Sync", "/storage/emulated/0"))
            .isEqualTo("/storage/1234-ABCD/Sync")
    }

    @Test
    fun `rejects virtual providers`() {
        assertThat(documentTreePath("home:Documents", "/storage/emulated/0")).isNull()
    }
}
