package dev.lostf1sh.syncthing.ui.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.lostf1sh.syncthing.api.dto.SystemStatus
import dev.lostf1sh.syncthing.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiagnosticsScreen(
    settingsStore: SettingsStore?,
    systemStatus: SystemStatus? = null,
    logs: List<String> = emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            Icon(
                Icons.Default.BugReport, null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Export Diagnostics", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Generate a diagnostics bundle for troubleshooting. API keys and sensitive data are redacted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (systemStatus != null) {
                Spacer(Modifier.height(16.dp))
                Text("Service Health", style = MaterialTheme.typography.titleSmall)
                ListItem(
                    headlineContent = { Text("Memory") },
                    supportingContent = { Text("${systemStatus.alloc / 1024 / 1024} MB") },
                )
                ListItem(
                    headlineContent = { Text("Uptime") },
                    supportingContent = {
                        val uptime = systemStatus.uptime
                        Text(
                            "${uptime / 3600}h ${(uptime % 3600) / 60}m ${uptime % 60}s"
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Goroutines") },
                    supportingContent = { Text("${systemStatus.goroutines}") },
                )
            }

            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent Logs", style = MaterialTheme.typography.titleSmall)
                logs.takeLast(20).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Bundle includes:", style = MaterialTheme.typography.titleSmall)
            ListItem(headlineContent = { Text("App version and device info") })
            ListItem(headlineContent = { Text("Redacted config snapshot") })
            ListItem(headlineContent = { Text("Recent log output") })
            ListItem(headlineContent = { Text("Current sync state") })

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = {
                    exporting = true
                    scope.launch {
                        val zipFile = exportDiagnostics(context)
                        exporting = false
                        if (zipFile != null) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                zipFile,
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostics"))
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting,
            ) {
                Icon(Icons.Default.Share, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (exporting) "Exporting..." else "Export & Share")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private suspend fun exportDiagnostics(context: android.content.Context): File? =
    withContext(Dispatchers.IO) {
        try {
            val outDir = File(context.cacheDir, "diagnostics")
            outDir.mkdirs()
            val zipFile = File(outDir, "syncthing-diagnostics.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                // Device info
                zip.putNextEntry(ZipEntry("device-info.txt"))
                val info = buildString {
                    appendLine("App: dev.lostf1sh.syncthing")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("Time: ${java.time.Instant.now()}")
                }
                zip.write(info.toByteArray())
                zip.closeEntry()

                // Redacted config
                val configFile = File(context.filesDir, "config.xml")
                if (configFile.exists()) {
                    zip.putNextEntry(ZipEntry("config-redacted.xml"))
                    val redacted = configFile.readText()
                        .replace(Regex("<apikey>[^<]*</apikey>"), "<apikey>REDACTED</apikey>")
                        .replace(Regex("<password>[^<]*</password>"), "<password>REDACTED</password>")
                    zip.write(redacted.toByteArray())
                    zip.closeEntry()
                }

                // Log file
                val logFile = File(context.filesDir, "syncthing.log")
                if (logFile.exists()) {
                    zip.putNextEntry(ZipEntry("syncthing.log"))
                    val lines = logFile.readLines()
                    val tail = lines.takeLast(1000).joinToString("\n")
                    zip.write(tail.toByteArray())
                    zip.closeEntry()
                }
            }

            zipFile
        } catch (e: Exception) {
            null
        }
    }
