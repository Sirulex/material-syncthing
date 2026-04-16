package dev.lostf1sh.syncthing.native

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RunStateTest {

    @Test
    fun `Stopped is a valid state`() {
        val state: RunState = RunState.Stopped
        assertThat(state).isEqualTo(RunState.Stopped)
    }

    @Test
    fun `Running carries apiKey and port`() {
        val state = RunState.Running(apiKey = "abc123", port = 8384)
        assertThat(state.apiKey).isEqualTo("abc123")
        assertThat(state.port).isEqualTo(8384)
    }

    @Test
    fun `Crashed carries exitCode and reason`() {
        val state = RunState.Crashed(exitCode = 1, reason = "error")
        assertThat(state.exitCode).isEqualTo(1)
        assertThat(state.reason).isEqualTo("error")
    }

    @Test
    fun `Paused carries reason`() {
        val state = RunState.Paused(reason = "Wi-Fi required")
        assertThat(state.reason).isEqualTo("Wi-Fi required")
    }

    @Test
    fun `sealed hierarchy covers all states`() {
        val states = listOf<RunState>(
            RunState.Stopped,
            RunState.Starting,
            RunState.Running("key", 8384),
            RunState.Crashed(1, "error"),
            RunState.Paused("reason"),
        )
        assertThat(states).hasSize(5)
    }
}
