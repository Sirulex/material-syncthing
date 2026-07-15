package dev.sirulex.syncthing.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sirulex.syncthing.data.SettingsStore
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
            MediumFlexibleTopAppBar(
                title = { Text("Setup") },
                subtitle = { Text("Step ${step + 1} of $totalSteps") },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
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
            LinearWavyProgressIndicator(
                progress = { (step + 1).toFloat() / totalSteps },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // Step content
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val movingForward = targetState > initialState
                    (fadeIn(animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            initialOffsetY = { fullHeight -> if (movingForward) fullHeight / 6 else -fullHeight / 6 },
                        )).togetherWith(
                        fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                            slideOutVertically(
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                targetOffsetY = { fullHeight -> if (movingForward) -fullHeight / 8 else fullHeight / 8 },
                            )
                    ).using(SizeTransform(clip = false))
                },
                label = "wizard",
            ) { currentStep ->
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
                Button(
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        storageGranted = result[Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
            result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    // Refresh permission state whenever the user returns from system Settings.
    // Without this the "Granted" label stays stale until the user leaves and
    // re-enters onboarding.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageGranted = Environment.isExternalStorageManager()
                } else {
                    storageGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) == PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                            } else {
                                storageLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
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
