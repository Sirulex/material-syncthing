// Ported from Catfriend1/syncthing-android (MPL-2.0): service/SyncthingRunnable.java
// Rewritten with coroutines, Process.destroy() instead of shell kill, MulticastLock.
package dev.lostf1sh.syncthing.native

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Inet4Address

/**
 * Launches and manages the Syncthing native binary as a subprocess.
 *
 * @param binaryPath Path to libsyncthingnative.so (from nativeLibraryDir)
 * @param configDir  STHOMEDIR — where config.xml, keys, and database live
 * @param cacheDir   Used for SQLITE_TMPDIR and TMPDIR
 * @param logFile    File to append native stdout/stderr output
 */
class NativeLauncher(
    private val binaryPath: File,
    private val configDir: File,
    private val cacheDir: File,
    private val logFile: File,
    private val contextProvider: () -> Context,
) {

    companion object {
        private const val TAG = "NativeLauncher"
        private const val LOG_MAX_LINES = 200_000
        private const val STOP_TIMEOUT_MS = 3_000L

        fun fromContext(context: Context): NativeLauncher {
            val appInfo = context.applicationInfo
            return NativeLauncher(
                binaryPath = File(appInfo.nativeLibraryDir, "libsyncthingnative.so"),
                configDir = context.filesDir,
                cacheDir = context.cacheDir,
                logFile = File(context.filesDir, "syncthing.log"),
                contextProvider = { context },
            )
        }
    }

    @Volatile
    private var process: Process? = null

    val isRunning: Boolean
        get() = process?.isAlive == true

    /**
     * Starts the Syncthing binary in "serve" mode.
     * Blocks on IO dispatcher until the process exits.
     * Returns the exit code.
     */
    suspend fun start(): Int = withContext(Dispatchers.IO) {
        check(!isRunning) { "Syncthing is already running" }
        check(binaryPath.exists()) { "Binary missing: $binaryPath" }

        trimLogFile()

        val env = buildEnvironment()
        val command = listOf(
            binaryPath.absolutePath,
            "serve",
            "--no-browser",
            "--no-restart",
        )

        // MulticastLock for local discovery (Android 11+ blocks without it)
        val wifi = contextProvider().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifi.createMulticastLock("syncthing_multicast").apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            val pb = ProcessBuilder(command).apply {
                environment().putAll(env)
                redirectErrorStream(true)
            }
            val proc = pb.start()
            process = proc

            // Pipe output to log file in background
            val logThread = Thread({
                BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                    logFile.outputStream().buffered().use { out ->
                        reader.forEachLine { line ->
                            out.write(line.toByteArray(Charsets.UTF_8))
                            out.write('\n'.code)
                        }
                    }
                }
            }, "syncthing-logger")
            logThread.isDaemon = true
            logThread.start()

            val exitCode = proc.waitFor()
            logThread.join(2_000)
            Log.i(TAG, "Syncthing exited with code $exitCode")
            exitCode
        } finally {
            process = null
            if (multicastLock.isHeld) {
                multicastLock.release()
            }
        }
    }

    /**
     * Interprets exit code from Syncthing binary.
     * Matches Catfriend1 behavior.
     */
    fun interpretExitCode(exitCode: Int): RunState = when (exitCode) {
        0, 137 -> RunState.Stopped
        3 -> RunState.Starting // restart requested
        else -> RunState.Crashed(exitCode, exitCodeReason(exitCode))
    }

    private fun exitCodeReason(code: Int): String = when (code) {
        1 -> "Error — another instance may be running"
        2 -> "No upgrade available"
        9 -> "Force killed"
        64 -> "Invalid command line"
        else -> "Unexpected exit (code $code)"
    }

    /**
     * Stops the running binary gracefully (SIGTERM), with SIGKILL fallback.
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val proc = process ?: return@withContext
        Log.i(TAG, "Stopping Syncthing...")
        proc.destroy() // SIGTERM
        delay(STOP_TIMEOUT_MS)
        if (proc.isAlive) {
            Log.w(TAG, "SIGTERM did not stop Syncthing, sending SIGKILL")
            proc.destroyForcibly()
            proc.waitFor()
        }
        process = null
    }

    /**
     * Runs `syncthing generate` to create initial config, keys, and cert.
     */
    suspend fun generateConfig(): String = withContext(Dispatchers.IO) {
        check(binaryPath.exists()) { "Binary missing: $binaryPath" }
        val command = listOf(
            binaryPath.absolutePath,
            "generate",
            "--home=${configDir.absolutePath}",
        )
        val pb = ProcessBuilder(command).apply {
            environment()["HOME"] = configDir.absolutePath
        }
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            error("syncthing generate failed (exit $exitCode): $output")
        }
        output
    }

    /**
     * Runs `syncthing device-id` to retrieve local device ID.
     */
    suspend fun getDeviceId(): String = withContext(Dispatchers.IO) {
        check(binaryPath.exists()) { "Binary missing: $binaryPath" }
        val command = listOf(
            binaryPath.absolutePath,
            "device-id",
            "--home=${configDir.absolutePath}",
        )
        val pb = ProcessBuilder(command).apply {
            environment()["HOME"] = configDir.absolutePath
        }
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            error("syncthing device-id failed (exit $exitCode): $output")
        }
        output
    }

    private fun buildEnvironment(): Map<String, String> = buildMap {
        put("HOME", configDir.absolutePath)
        put("STHOMEDIR", configDir.absolutePath)
        put("STNOUPGRADE", "1")
        put("STNORESTART", "1")
        put("STMONITORED", "1")
        put("TMPDIR", cacheDir.absolutePath)
        put("SQLITE_TMPDIR", cacheDir.absolutePath)

        // Android 14+ restricts reading gateway IP; provide fallback
        getGatewayIpV4()?.let { put("FALLBACK_NET_GATEWAY_IPV4", it) }
    }

    /**
     * Detects IPv4 default gateway for FALLBACK_NET_GATEWAY_IPV4 env var.
     * Ported from Catfriend1 SyncthingRunnable.getGatewayIpV4().
     */
    private fun getGatewayIpV4(): String? {
        return try {
            val cm = contextProvider().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val props = cm.getLinkProperties(network) ?: return null
            props.routes.firstOrNull { route ->
                route.isDefaultRoute && route.gateway is Inet4Address
            }?.gateway?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get gateway IP", e)
            null
        }
    }

    /**
     * Trims log file to last LOG_MAX_LINES lines.
     * Ported from Catfriend1 SyncthingRunnable.trimSyncthingLogFile().
     */
    private fun trimLogFile() {
        if (!logFile.exists()) return
        try {
            val lines = logFile.readLines()
            if (lines.size > LOG_MAX_LINES) {
                val trimmed = lines.takeLast(LOG_MAX_LINES)
                logFile.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trim log file", e)
        }
    }
}
