package dev.lostf1sh.syncthing

import com.google.common.truth.Truth.assertThat
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.work.shouldSchedulerIssuePause
import org.junit.Test

class StartupPolicyTest {

    @Test
    fun `auto start requires onboarding complete and no user suppression`() {
        assertThat(
            shouldAutoStartAfterOnboarding(
                onboardingComplete = true,
                startSuppressedByUser = false,
            )
        ).isTrue()
        assertThat(
            shouldAutoStartAfterOnboarding(
                onboardingComplete = true,
                startSuppressedByUser = true,
            )
        ).isFalse()
        assertThat(
            shouldAutoStartAfterOnboarding(
                onboardingComplete = false,
                startSuppressedByUser = false,
            )
        ).isFalse()
    }

    @Test
    fun `scheduler pause is only issued for active daemon states`() {
        assertThat(shouldSchedulerIssuePause(RunState.Running("key", 8384))).isTrue()
        assertThat(shouldSchedulerIssuePause(RunState.Starting)).isTrue()
        assertThat(shouldSchedulerIssuePause(RunState.Paused("manual"))).isFalse()
        assertThat(shouldSchedulerIssuePause(RunState.Stopped)).isFalse()
        assertThat(shouldSchedulerIssuePause(RunState.Crashed(1, "boom"))).isFalse()
    }
}
