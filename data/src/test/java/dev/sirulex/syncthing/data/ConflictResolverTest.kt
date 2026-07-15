package dev.sirulex.syncthing.data

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConflictResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `keep both renames conflict marker and preserves both files`() {
        val root = temporaryFolder.newFolder("sync")
        File(root, "note.txt").writeText("current")
        val conflict = File(root, "note.sync-conflict-20260101-120000-ABC1234.txt")
        conflict.writeText("remote")

        val result = ConflictResolver.keepBoth(root.path, conflict.name)

        assertThat(result).isEqualTo(ConflictResolver.Result.Success)
        assertThat(File(root, "note.txt").readText()).isEqualTo("current")
        assertThat(root.listFiles()!!.map { it.name })
            .contains("note (conflict ABC1234).txt")
        assertThat(conflict.exists()).isFalse()
    }

    @Test
    fun `resolver rejects paths outside folder`() {
        val root = temporaryFolder.newFolder("safe")
        val outside = temporaryFolder.newFile("outside.sync-conflict-20260101-120000-ABC1234.txt")

        val result = ConflictResolver.keepCurrent(root.path, "../${outside.name}")

        assertThat(result).isInstanceOf(ConflictResolver.Result.Failure::class.java)
        assertThat(outside.exists()).isTrue()
    }
}
