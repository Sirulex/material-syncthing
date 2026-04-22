package dev.lostf1sh.syncthing.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore?,
    onBack: () -> Unit,
    onProfilesClick: (() -> Unit)? = null,
    onDiagnosticsClick: (() -> Unit)? = null,
    onErrorCenterClick: (() -> Unit)? = null,
    onBatteryWizardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // Collect all settings as state
    val runOnBoot by settingsStore?.runOnBoot?.collectAsStateWithLifecycle(initialValue = false)
        ?: return
    val wifiOnly by settingsStore.wifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val allowMetered by settingsStore.allowMetered.collectAsStateWithLifecycle(initialValue = true)
    val chargingOnly by settingsStore.chargingOnly.collectAsStateWithLifecycle(initialValue = false)
    val respectBatterySaver by settingsStore.respectBatterySaver.collectAsStateWithLifecycle(initialValue = true)
    val notifySyncComplete by settingsStore.notifySyncComplete.collectAsStateWithLifecycle(initialValue = true)
    val notifyDeviceConnected by settingsStore.notifyDeviceConnected.collectAsStateWithLifecycle(initialValue = false)
    val schedulerEnabled by settingsStore.schedulerEnabled.collectAsStateWithLifecycle(initialValue = false)
    val schedulerStartHour by settingsStore.schedulerStartHour.collectAsStateWithLifecycle(initialValue = 23)
    val schedulerEndHour by settingsStore.schedulerEndHour.collectAsStateWithLifecycle(initialValue = 6)
    val notifyErrors by settingsStore.notifyErrors.collectAsStateWithLifecycle(initialValue = true)

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
            // --- Run Conditions ---
            SectionHeader("Run Conditions")
            SectionCard {
                SettingsSwitch(
                    title = "Start on boot",
                    description = "Start Syncthing when device boots",
                    checked = runOnBoot,
                    onCheckedChange = { scope.launch { settingsStore.setRunOnBoot(it) } },
                )
                SettingsSwitch(
                    title = "Wi-Fi only",
                    description = "Only sync when connected to Wi-Fi",
                    checked = wifiOnly,
                    onCheckedChange = { scope.launch { settingsStore.setWifiOnly(it) } },
                )
                if (wifiOnly) {
                    SettingsSwitch(
                        title = "Allow metered Wi-Fi",
                        description = "Sync on metered Wi-Fi networks",
                        checked = allowMetered,
                        onCheckedChange = { scope.launch { settingsStore.setAllowMetered(it) } },
                    )
                }
                SettingsSwitch(
                    title = "Charging only",
                    description = "Only sync while charging",
                    checked = chargingOnly,
                    onCheckedChange = { scope.launch { settingsStore.setChargingOnly(it) } },
                )
                SettingsSwitch(
                    title = "Respect battery saver",
                    description = "Pause syncing when battery saver is active",
                    checked = respectBatterySaver,
                    onCheckedChange = { scope.launch { settingsStore.setRespectBatterySaver(it) } },
                )
            }

            // --- Scheduler ---
            SectionHeader("Scheduler")
            SectionCard {
                SettingsSwitch(
                    title = "Enable time scheduler",
                    description = "Only allow sync within set hours",
                    checked = schedulerEnabled,
                    onCheckedChange = { scope.launch { settingsStore.setSchedulerEnabled(it) } },
                )
                if (schedulerEnabled) {
                    ListItem(
                        headlineContent = { Text("Start hour: ${schedulerStartHour}:00") },
                        trailingContent = {
                            Slider(
                                value = schedulerStartHour.toFloat(),
                                onValueChange = { scope.launch { settingsStore.setSchedulerStartHour(it.toInt()) } },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.width(120.dp),
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("End hour: ${schedulerEndHour}:00") },
                        trailingContent = {
                            Slider(
                                value = schedulerEndHour.toFloat(),
                                onValueChange = { scope.launch { settingsStore.setSchedulerEndHour(it.toInt()) } },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.width(120.dp),
                            )
                        },
                    )
                }
            }

            // --- Notifications ---
            SectionHeader("Notifications")
            SectionCard {
                SettingsSwitch(
                    title = "Sync complete",
                    description = "Notify when folder finishes syncing",
                    checked = notifySyncComplete,
                    onCheckedChange = { scope.launch { settingsStore.setNotifySyncComplete(it) } },
                )
                SettingsSwitch(
                    title = "Device connected",
                    description = "Notify when device connects",
                    checked = notifyDeviceConnected,
                    onCheckedChange = { scope.launch { settingsStore.setNotifyDeviceConnected(it) } },
                )
                SettingsSwitch(
                    title = "Errors",
                    description = "Notify on sync errors",
                    checked = notifyErrors,
                    onCheckedChange = { scope.launch { settingsStore.setNotifyErrors(it) } },
                )
            }

            // --- Tools ---
            SectionHeader("Tools")
            SectionCard {
                if (onProfilesClick != null) {
                    ListItem(
                        headlineContent = { Text("Sync Profiles") },
                        supportingContent = { Text("Wi-Fi only, charging, night sync") },
                        modifier = Modifier.clickable { onProfilesClick() },
                        colors = transparentListItemColors(),
                    )
                }
                if (onErrorCenterClick != null) {
                    ListItem(
                        headlineContent = { Text("Error Center") },
                        supportingContent = { Text("View and resolve sync issues") },
                        modifier = Modifier.clickable { onErrorCenterClick() },
                        colors = transparentListItemColors(),
                    )
                }
                if (onDiagnosticsClick != null) {
                    ListItem(
                        headlineContent = { Text("Diagnostics") },
                        supportingContent = { Text("Export debug info for troubleshooting") },
                        modifier = Modifier.clickable { onDiagnosticsClick() },
                        colors = transparentListItemColors(),
                    )
                }
                if (onBatteryWizardClick != null) {
                    ListItem(
                        headlineContent = { Text("Reliability") },
                        supportingContent = { Text("Battery optimization + OEM autostart") },
                        modifier = Modifier.clickable { onBatteryWizardClick() },
                        colors = transparentListItemColors(),
                    )
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
            }

            // --- About ---
            SectionHeader("About")
            SectionCard {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("0.1.0") },
                    colors = transparentListItemColors(),
                )
                ListItem(
                    headlineContent = { Text("License") },
                    supportingContent = { Text("MPL-2.0") },
                    colors = transparentListItemColors(),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
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
