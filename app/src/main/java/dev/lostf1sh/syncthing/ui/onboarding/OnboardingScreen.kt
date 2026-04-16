package dev.lostf1sh.syncthing.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    settingsStore: SettingsStore,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    var deviceName by remember { mutableStateOf(Build.MODEL) }
    var wifiOnly by remember { mutableStateOf(false) }
    var runOnBoot by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setup") })
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Progress
            Column {
                LinearProgressIndicator(
                    progress = { (step + 1).toFloat() / totalSteps },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Step ${step + 1} of $totalSteps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Step content
            AnimatedContent(targetState = step, label = "wizard") { currentStep ->
                when (currentStep) {
                    0 -> WelcomeStep()
                    1 -> PermissionsStep()
                    2 -> DeviceNameStep(
                        deviceName = deviceName,
                        onNameChange = { deviceName = it },
                    )
                    3 -> PreferencesStep(
                        wifiOnly = wifiOnly,
                        onWifiOnlyChange = { wifiOnly = it },
                        runOnBoot = runOnBoot,
                        onRunOnBootChange = { runOnBoot = it },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))

            // Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = {
                        if (step < totalSteps - 1) {
                            step++
                        } else {
                            scope.launch {
                                settingsStore.setDeviceName(deviceName)
                                settingsStore.setWifiOnly(wifiOnly)
                                settingsStore.setRunOnBoot(runOnBoot)
                                settingsStore.setOnboardingComplete(true)
                                onComplete()
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (step < totalSteps - 1) "Next" else "Get Started")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Icon(
                        if (step < totalSteps - 1) Icons.AutoMirrored.Filled.ArrowForward
                        else Icons.Default.Check,
                        null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Syncthing",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Keep your files in sync across all your devices. Private, secure, no cloud required.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var storageGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
        )
    }
    var notifGranted by remember { mutableStateOf(true) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    Column {
        Text(
            "Permissions",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Syncthing needs access to files and notifications.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        ListItem(
            leadingContent = {
                Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = { Text("File Access") },
            supportingContent = {
                Text(if (storageGranted) "Granted" else "Required to sync files")
            },
            trailingContent = {
                if (storageGranted) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Grant") }
                }
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text("Notifications") },
                supportingContent = {
                    Text(if (notifGranted) "Granted" else "For sync status updates")
                },
                trailingContent = {
                    if (notifGranted) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalButton(
                            onClick = {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Grant") }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceNameStep(
    deviceName: String,
    onNameChange: (String) -> Unit,
) {
    Column {
        Icon(
            Icons.Default.Devices,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Device Name",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Other devices will see this name.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = deviceName,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreferencesStep(
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    runOnBoot: Boolean,
    onRunOnBootChange: (Boolean) -> Unit,
) {
    Column {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Preferences",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Set defaults. Change anytime in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        ListItem(
            headlineContent = { Text("Wi-Fi only") },
            supportingContent = { Text("Sync only on Wi-Fi") },
            trailingContent = {
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
            },
        )
        ListItem(
            headlineContent = { Text("Start on boot") },
            supportingContent = { Text("Keep syncing after reboot") },
            trailingContent = {
                Switch(checked = runOnBoot, onCheckedChange = onRunOnBootChange)
            },
        )
    }
}
