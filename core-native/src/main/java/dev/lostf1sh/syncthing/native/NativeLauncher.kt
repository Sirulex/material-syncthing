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
import java.util.concurrent.TimeUnit

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
        private const val ONESHOT_TIMEOUT_SEC = 30L

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

    private val processLock = Any()

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
        // First check — fast-path rejection before doing any I/O.
        synchronized(processLock) {
            check(!isRunning) { "Syncthing is already running" }
        }
        check(binaryPath.exists()) { "Binary missing: $binaryPath" }

        trimLogFile()

        val env = buildEnvironment()
        val command = listOf(
            binaryPath.absolutePath,
            "serve",
            "--home=${configDir.absolutePath}",
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

        var proc: Process? = null
        var logThread: Thread? = null
        try {
            val pb = ProcessBuilder(command).apply {
                environment().putAll(env)
                redirectErrorStream(true)
            }
            proc = pb.start()
            // Double-checked assignment: another concurrent start() could have
            // slipped past the first check while this one was building the
            // ProcessBuilder. Holding processLock makes the isRunning read and
            // the process write a single atomic unit.
            synchronized(processLock) {
                check(!isRunning) { "Syncthing is already running (race)" }
                process = proc
            }

            // Pipe output to log file in background
            logThread = Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                        java.io.FileOutputStream(logFile, true).buffered().use { out ->
                            reader.forEachLine { line ->
                                out.write(line.toByteArray(Charsets.UTF_8))
                                out.write('\n'.code)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Stream closed when process died/destroyed; ignore.
                }
            }, "syncthing-logger").apply {
                isDaemon = true
                start()
            }

            val exitCode = proc.waitFor()
            logThread.join(2_000)
            Log.i(TAG, "Syncthing exited with code $exitCode")
            exitCode
        } finally {
            // If we're bailing due to cancellation/throw and the child is still alive,
            // kill it so we don't leak the OS process + pipes.
            val p = proc
            if (p != null && p.isAlive) {
                try {
                    p.destroyForcibly()
                    p.waitFor(2, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.w(TAG, "destroyForcibly failed", e)
                }
            }
            logThread?.interrupt()
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
        0, 137, 143 -> RunState.Stopped // 137=SIGKILL, 143=SIGTERM
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
    suspend fun generateConfig(): String = runOneShot(
        args = listOf("generate", "--home=${configDir.absolutePath}"),
        label = "syncthing generate",
    )

    /**
     * Runs `syncthing device-id` to retrieve local device ID.
     */
    suspend fun getDeviceId(): String = runOneShot(
        args = listOf("device-id", "--home=${configDir.absolutePath}"),
        label = "syncthing device-id",
    ).trim()

    /**
     * Runs a one-shot syncthing command with bounded timeout and guaranteed cleanup.
     * Used for `generate` and `device-id`. A hung subprocess here would otherwise
     * stall the foreground-service startup path and trigger ANR.
     */
    private suspend fun runOneShot(args: List<String>, label: String): String =
        withContext(Dispatchers.IO) {
            check(binaryPath.exists()) { "Binary missing: $binaryPath" }
            val pb = ProcessBuilder(listOf(binaryPath.absolutePath) + args).apply {
                environment()["HOME"] = configDir.absolutePath
                redirectErrorStream(true)
            }
            val proc = pb.start()
            val output = StringBuilder()
            val outputLock = Any()
            val readerThread = Thread({
                try {
                    proc.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            synchronized(outputLock) {
                                if (output.length < 16_384) {
                                    output.appendLine(line)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Process was killed or stream closed.
                }
            }, "$label-output").apply {
                isDaemon = true
                start()
            }
            try {
                if (!proc.waitFor(ONESHOT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    readerThread.join(2_000)
                    error("$label timed out after ${ONESHOT_TIMEOUT_SEC}s")
                }
                readerThread.join(2_000)
                val exitCode = proc.exitValue()
                val captured = synchronized(outputLock) { output.toString() }
                if (exitCode != 0) {
                    error("$label failed (exit $exitCode): $captured")
                }
                captured
            } finally {
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
                readerThread.interrupt()
            }
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
            // Stream with a ring buffer to avoid loading multi-MB logs into memory.
            val ring = ArrayDeque<String>(LOG_MAX_LINES + 1)
            var total = 0
            logFile.bufferedReader(Charsets.UTF_8).useLines { seq ->
                for (line in seq) {
                    total++
                    ring.addLast(line)
                    if (ring.size > LOG_MAX_LINES) ring.removeFirst()
                }
            }
            if (total <= LOG_MAX_LINES) return
            val temp = File(logFile.parentFile, "${logFile.name}.trim")
            temp.bufferedWriter(Charsets.UTF_8).use { out ->
                for (line in ring) {
                    out.write(line)
                    out.write("\n")
                }
            }
            if (!temp.renameTo(logFile)) {
                // Best-effort: fall back to overwrite; delete temp to avoid litter.
                logFile.writeText(ring.joinToString("\n") + "\n")
                temp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trim log file", e)
        }
    }
}
