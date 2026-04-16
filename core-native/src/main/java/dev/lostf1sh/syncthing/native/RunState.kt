// Ported from Catfriend1/syncthing-android (MPL-2.0): service/SyncthingService.java
// State enum extracted and modernized as sealed interface.
package dev.lostf1sh.syncthing.native

sealed interface RunState {
    data object Stopped : RunState
    data object Starting : RunState
    data class Running(val apiKey: String, val port: Int) : RunState
    data class Crashed(val exitCode: Int, val reason: String) : RunState
    data class Paused(val reason: String) : RunState
}
