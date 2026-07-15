package dev.sirulex.syncthing.ui.core.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatBytes returns B for small values`() {
        assertThat(formatBytes(0)).isEqualTo("0 B")
        assertThat(formatBytes(512)).isEqualTo("512 B")
        assertThat(formatBytes(1023)).isEqualTo("1023 B")
    }

    @Test
    fun `formatBytes returns KiB`() {
        assertThat(formatBytes(1024)).isEqualTo("1.0 KiB")
        assertThat(formatBytes(1536)).isEqualTo("1.5 KiB")
        assertThat(formatBytes(1024 * 1024 - 1)).contains("KiB")
    }

    @Test
    fun `formatBytes returns MiB`() {
        assertThat(formatBytes(1024 * 1024)).isEqualTo("1.0 MiB")
        assertThat(formatBytes(1024 * 1024 * 512)).isEqualTo("512.0 MiB")
    }

    @Test
    fun `formatBytes returns GiB`() {
        assertThat(formatBytes(1024L * 1024 * 1024)).isEqualTo("1.0 GiB")
    }

    @Test
    fun `formatBytes returns TiB`() {
        assertThat(formatBytes(1024L * 1024 * 1024 * 1024)).isEqualTo("1.0 TiB")
    }

    @Test
    fun `formatBytesDecimal returns KB MB GB`() {
        assertThat(formatBytesDecimal(1000)).isEqualTo("1.0 KB")
        assertThat(formatBytesDecimal(1_500_000)).isEqualTo("1.5 MB")
        assertThat(formatBytesDecimal(2_000_000_000)).isEqualTo("2.0 GB")
    }

    @Test
    fun `formatRelativeTime handles just now`() {
        val now = System.currentTimeMillis()
        assertThat(formatRelativeTime(now)).isEqualTo("just now")
        assertThat(formatRelativeTime(now - 30_000)).isEqualTo("just now")
    }

    @Test
    fun `formatRelativeTime handles minutes`() {
        val now = System.currentTimeMillis()
        assertThat(formatRelativeTime(now - 120_000)).isEqualTo("2 mins ago")
        assertThat(formatRelativeTime(now - 60_000)).isEqualTo("1 min ago")
    }

    @Test
    fun `formatRelativeTime handles hours`() {
        val now = System.currentTimeMillis()
        assertThat(formatRelativeTime(now - 3_600_000 * 2)).isEqualTo("2 hours ago")
        assertThat(formatRelativeTime(now - 3_600_000)).isEqualTo("1 hour ago")
    }

    @Test
    fun `formatRelativeTime handles days`() {
        val now = System.currentTimeMillis()
        assertThat(formatRelativeTime(now - 86_400_000 * 3)).isEqualTo("3 days ago")
    }

    @Test
    fun `formatRelativeTime handles weeks`() {
        val now = System.currentTimeMillis()
        assertThat(formatRelativeTime(now - 86_400_000 * 14)).isEqualTo("2 weeks ago")
    }

    @Test
    fun `stateDisplayLabel maps known states`() {
        assertThat(stateDisplayLabel("idle")).isEqualTo("Idle")
        assertThat(stateDisplayLabel("syncing")).isEqualTo("Syncing")
        assertThat(stateDisplayLabel("error")).isEqualTo("Error")
        assertThat(stateDisplayLabel("paused")).isEqualTo("Paused")
        assertThat(stateDisplayLabel("unknown")).isEqualTo("Unknown")
    }

    @Test
    fun `stateDisplayLabel capitalizes unknown`() {
        assertThat(stateDisplayLabel("foobar")).isEqualTo("Foobar")
    }
}
