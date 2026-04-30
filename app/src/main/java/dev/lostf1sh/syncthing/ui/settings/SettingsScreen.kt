package dev.lostf1sh.syncthing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onProfilesClick: (() -> Unit)? = null,
    onDiagnosticsClick: (() -> Unit)? = null,
    onErrorCenterClick: (() -> Unit)? = null,
    onBatteryWizardClick: (() -> Unit)? = null,
    onWebGuiClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // Collect all settings as state
    val runOnBoot by settingsStore.runOnBoot.collectAsStateWithLifecycle(initialValue = false)
    val wifiOnly by settingsStore.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val allowMetered by settingsStore.allowMetered.collectAsStateWithLifecycle(initialValue = true)
    val chargingOnly by settingsStore.chargingOnly.collectAsStateWithLifecycle(initialValue = false)
    val respectBatterySaver by settingsStore.respectBatterySaver.collectAsStateWithLifecycle(initialValue = true)
    val notifySyncComplete by settingsStore.notifySyncComplete.collectAsStateWithLifecycle(initialValue = true)
    val notifyDeviceConnected by settingsStore.notifyDeviceConnected.collectAsStateWithLifecycle(initialValue = false)
    val schedulerEnabled by settingsStore.schedulerEnabled.collectAsStateWithLifecycle(initialValue = false)
    val schedulerStartHour by settingsStore.schedulerStartHour.collectAsStateWithLifecycle(initialValue = 23)
    val schedulerStartMinute by settingsStore.schedulerStartMinute.collectAsStateWithLifecycle(initialValue = 0)
    val schedulerEndHour by settingsStore.schedulerEndHour.collectAsStateWithLifecycle(initialValue = 6)
    val schedulerEndMinute by settingsStore.schedulerEndMinute.collectAsStateWithLifecycle(initialValue = 0)
    val notifyErrors by settingsStore.notifyErrors.collectAsStateWithLifecycle(initialValue = true)
    val theme by settingsStore.theme.collectAsStateWithLifecycle(initialValue = "system")
    val biometricEnabled by settingsStore.biometricEnabled.collectAsStateWithLifecycle(initialValue = false)
    var query by rememberSaveable { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = settingsStore.exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@launch
            settingsStore.importFromJson(json)
        }
    }

    val queryTrimmed = query.trim()

    Scaffold(
        topBar = {
            // Expressive: MediumFlexibleTopAppBar
            MediumFlexibleTopAppBar(
                title = { Text("Settings") },
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
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search settings") },
                singleLine = true,
            )

            // --- Run Conditions ---
            if (queryTrimmed.isBlank() || "run conditions wifi battery boot".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Run Conditions")
                SectionCard {
                    SettingsSwitch(
                        title = "Start on boot",
                        description = "Start Syncthing when device boots",
                        checked = runOnBoot,
                        onCheckedChange = { scope.launch { settingsStore.setRunOnBoot(it) } },
                    )
                    HorizontalDivider()
                    SettingsSwitch(
                        title = "Wi-Fi only",
                        description = "Only sync when connected to Wi-Fi",
                        checked = wifiOnly,
                        onCheckedChange = { scope.launch { settingsStore.setWifiOnly(it) } },
                    )
                    if (wifiOnly) {
                        HorizontalDivider()
                        SettingsSwitch(
                            title = "Allow metered Wi-Fi",
                            description = "Sync on metered Wi-Fi networks",
                            checked = allowMetered,
                            onCheckedChange = { scope.launch { settingsStore.setAllowMetered(it) } },
                        )
                    }
                    HorizontalDivider()
                    SettingsSwitch(
                        title = "Charging only",
                        description = "Only sync while charging",
                        checked = chargingOnly,
                        onCheckedChange = { scope.launch { settingsStore.setChargingOnly(it) } },
                    )
                    HorizontalDivider()
                    SettingsSwitch(
                        title = "Respect battery saver",
                        description = "Pause syncing when battery saver is active",
                        checked = respectBatterySaver,
                        onCheckedChange = { scope.launch { settingsStore.setRespectBatterySaver(it) } },
                    )
                }
            }

            // --- Scheduler ---
            if (queryTrimmed.isBlank() || "scheduler time schedule".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Scheduler")
                SectionCard {
                    SettingsSwitch(
                        title = "Enable time scheduler",
                        description = "Only allow sync within set hours",
                        checked = schedulerEnabled,
                        onCheckedChange = { scope.launch { settingsStore.setSchedulerEnabled(it) } },
                    )
                    if (schedulerEnabled) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Start time") },
                            supportingContent = { Text(formatClock(schedulerStartHour, schedulerStartMinute)) },
                            modifier = Modifier.clickable { showStartPicker = true },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("End time") },
                            supportingContent = { Text(formatClock(schedulerEndHour, schedulerEndMinute)) },
                            modifier = Modifier.clickable { showEndPicker = true },
                            colors = transparentListItemColors(),
                        )
                    }
                }
            }

            // --- Notifications ---
            if (queryTrimmed.isBlank() || "notification".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Notifications")
                SectionCard {
                    SettingsSwitch(
                        title = "Sync complete",
                        description = "Notify when folder finishes syncing",
                        checked = notifySyncComplete,
                        onCheckedChange = { scope.launch { settingsStore.setNotifySyncComplete(it) } },
                    )
                    HorizontalDivider()
                    SettingsSwitch(
                        title = "Device connected",
                        description = "Notify when device connects",
                        checked = notifyDeviceConnected,
                        onCheckedChange = { scope.launch { settingsStore.setNotifyDeviceConnected(it) } },
                    )
                    HorizontalDivider()
                    SettingsSwitch(
                        title = "Errors",
                        description = "Notify on sync errors",
                        checked = notifyErrors,
                        onCheckedChange = { scope.launch { settingsStore.setNotifyErrors(it) } },
                    )
                }
            }

            // --- Tools ---
            if (queryTrimmed.isBlank() || "tools profile diagnostics error language".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Tools")
                SectionCard {
                    if (onProfilesClick != null) {
                        ListItem(
                            headlineContent = { Text("Sync Profiles") },
                            supportingContent = { Text("Wi-Fi only, charging, night sync") },
                            modifier = Modifier.clickable { onProfilesClick() },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                    }
                    if (onErrorCenterClick != null) {
                        ListItem(
                            headlineContent = { Text("Error Center") },
                            supportingContent = { Text("View and resolve sync issues") },
                            modifier = Modifier.clickable { onErrorCenterClick() },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                    }
                    if (onDiagnosticsClick != null) {
                        ListItem(
                            headlineContent = { Text("Diagnostics") },
                            supportingContent = { Text("Export debug info for troubleshooting") },
                            modifier = Modifier.clickable { onDiagnosticsClick() },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                    }
                    if (onBatteryWizardClick != null) {
                        ListItem(
                            headlineContent = { Text("Reliability") },
                            supportingContent = { Text("Battery optimization + OEM autostart") },
                            modifier = Modifier.clickable { onBatteryWizardClick() },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    ListItem(
                        headlineContent = { Text("Language") },
                        supportingContent = { Text("App language / Uygulama dili") },
                        modifier = Modifier.clickable {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
                                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                try { context.startActivity(intent) } catch (_: Exception) { }
                            } else {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                try { context.startActivity(intent) } catch (_: Exception) { }
                            }
                        },
                        colors = transparentListItemColors(),
                    )
                    HorizontalDivider()
                    AssistChip(
                        onClick = { onDiagnosticsClick?.invoke() },
                        label = { Text("Learn more") },
                        leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // --- Appearance ---
            if (queryTrimmed.isBlank() || "theme dark light appearance".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Appearance")
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                            FilterChip(
                                selected = theme == key,
                                onClick = { scope.launch { settingsStore.setTheme(key) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            // --- Advanced ---
            if (queryTrimmed.isBlank() || "advanced web gui config backup".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("Advanced")
                SectionCard {
                    if (onWebGuiClick != null) {
                        ListItem(
                            headlineContent = { Text("Open Web GUI") },
                            supportingContent = { Text("Launch Syncthing Web GUI in browser") },
                            modifier = Modifier.clickable { onWebGuiClick() },
                            colors = transparentListItemColors(),
                        )
                        HorizontalDivider()
                    }
                    ListItem(
                        headlineContent = { Text("Export settings") },
                        supportingContent = { Text("Backup app preferences as JSON") },
                        modifier = Modifier.clickable { exportLauncher.launch("syncthing-settings.json") },
                        colors = transparentListItemColors(),
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Import settings") },
                        supportingContent = { Text("Restore app preferences from JSON") },
                        modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) },
                        colors = transparentListItemColors(),
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("App lock") },
                        supportingContent = { Text("Require biometric or device credential") },
                        trailingContent = {
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { scope.launch { settingsStore.setBiometricEnabled(it) } },
                            )
                        },
                        colors = transparentListItemColors(),
                    )
                }
            }

            // --- About ---
            if (queryTrimmed.isBlank() || "about version license".contains(queryTrimmed, ignoreCase = true)) {
                SectionHeader("About")
                SectionCard {
                    ListItem(
                        headlineContent = { Text("Version") },
                        supportingContent = { Text("0.1.0") },
                        colors = transparentListItemColors(),
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("License") },
                        supportingContent = { Text("MPL-2.0") },
                        colors = transparentListItemColors(),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showStartPicker) {
        SchedulerTimePickerDialog(
            title = "Select start time",
            initialHour = schedulerStartHour,
            initialMinute = schedulerStartMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { hour, minute ->
                scope.launch {
                    settingsStore.setSchedulerStartHour(hour)
                    settingsStore.setSchedulerStartMinute(minute)
                }
            },
        )
    }

    if (showEndPicker) {
        SchedulerTimePickerDialog(
            title = "Select end time",
            initialHour = schedulerEndHour,
            initialMinute = schedulerEndMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { hour, minute ->
                scope.launch {
                    settingsStore.setSchedulerEndHour(hour)
                    settingsStore.setSchedulerEndMinute(minute)
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

// Expressive: grouped section card with large rounded corners
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
)

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = transparentListItemColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulerTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour, state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatClock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
