package dev.lostf1sh.syncthing.ui.navigation

import com.google.common.truth.Truth.assertThat
import dev.lostf1sh.syncthing.di.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RouteOperationsTest {

    @Test
    fun `fireAndForget swallows non-cancellation exceptions and updates diagnostic`() = runTest {
        val appState = AppState()
        var blockCalled = false
        fireAndForget(
            block = {
                blockCalled = true
                throw RuntimeException("boom")
            },
            appState = appState,
            logTag = "test",
        )
        assertThat(blockCalled).isTrue()
        assertThat(appState.diagnostic.value).contains("boom")
    }

    @Test
    fun `fireAndForget rethrows CancellationException`() = runTest {
        var threw = false
        try {
            fireAndForget(
                block = { throw CancellationException("cancelled") },
            )
        } catch (_: CancellationException) {
            threw = true
        }
        assertThat(threw).isTrue()
    }

    @Test
    fun `fireAndForget updates diagnostic with class name when message blank`() = runTest {
        val appState = AppState()
        fireAndForget(
            block = { throw RuntimeException() },
            appState = appState,
            logTag = "test",
        )
        assertThat(appState.diagnostic.value).contains("RuntimeException")
    }

    @Test
    fun `fireAndForget without appState still swallows exception`() = runTest {
        var blockCalled = false
        fireAndForget(
            block = {
                blockCalled = true
                throw IllegalStateException("err")
            },
        )
        assertThat(blockCalled).isTrue()
    }
}
