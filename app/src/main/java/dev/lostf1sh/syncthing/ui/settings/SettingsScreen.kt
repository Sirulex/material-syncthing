package dev.lostf1sh.syncthing.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    // Collect all settings as state
    val runOnBoot by settingsStore?.runOnBoot?.collectAsState(initial = false) ?: return
    val wifiOnly by settingsStore.wifiOnly.collectAsState(initial = false)
    val allowMetered by settingsStore.allowMetered.collectAsState(initial = true)
    val chargingOnly by settingsStore.chargingOnly.collectAsState(initial = false)
    val respectBatterySaver by settingsStore.respectBatterySaver.collectAsState(initial = true)

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
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Run Conditions ---
            SectionHeader("Run Conditions")

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- About ---
            SectionHeader("About")

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("0.1.0") },
            )
            ListItem(
                headlineContent = { Text("License") },
                supportingContent = { Text("MPL-2.0") },
            )

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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

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
    )
}
