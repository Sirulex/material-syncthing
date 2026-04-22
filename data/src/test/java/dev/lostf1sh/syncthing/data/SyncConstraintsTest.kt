package dev.lostf1sh.syncthing.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncConstraintsTest {
    @Test
    fun `equal scheduler start and end is never active`() {
        assertThat(
            schedulerWindowContains(
                startMinuteOfDay = 23 * 60,
                endMinuteOfDay = 23 * 60,
                currentMinuteOfDay = 23 * 60,
            )
        ).isFalse()
    }

    @Test
    fun `overnight scheduler window includes late and early minutes`() {
        assertThat(schedulerWindowContains(23 * 60, 6 * 60, 23 * 60 + 30)).isTrue()
        assertThat(schedulerWindowContains(23 * 60, 6 * 60, 5 * 60 + 30)).isTrue()
        assertThat(schedulerWindowContains(23 * 60, 6 * 60, 12 * 60)).isFalse()
    }
}
