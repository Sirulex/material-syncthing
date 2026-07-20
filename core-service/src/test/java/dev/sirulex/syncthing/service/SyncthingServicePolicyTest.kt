package dev.sirulex.syncthing.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncthingServicePolicyTest {

    @Test
    fun `constraint pause resumes when constraints recover during stop`() {
        assertThat(
            shouldResumeAfterPause(
                suppressFutureAutoStarts = false,
                pausedByConstraint = false,
            )
        ).isTrue()
    }

    @Test
    fun `constraint pause remains paused while constraint is active`() {
        assertThat(
            shouldResumeAfterPause(
                suppressFutureAutoStarts = false,
                pausedByConstraint = true,
            )
        ).isFalse()
    }

    @Test
    fun `manual pause never auto resumes`() {
        assertThat(
            shouldResumeAfterPause(
                suppressFutureAutoStarts = true,
                pausedByConstraint = false,
            )
        ).isFalse()
    }
}
