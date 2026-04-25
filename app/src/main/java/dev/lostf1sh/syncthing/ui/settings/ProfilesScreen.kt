package dev.lostf1sh.syncthing.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.SettingsStore
import kotlinx.coroutines.launch

private data class ProfileDef(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val category: ProfileCategory,
)

private enum class ProfileCategory { All, Network, Power }

private val profiles = listOf(
    ProfileDef("default", "Default", "Sync anytime on any network", Icons.Default.SignalCellularAlt, ProfileCategory.All),
    ProfileDef("wifi_only", "Wi-Fi Only", "Sync only on Wi-Fi", Icons.Default.Wifi, ProfileCategory.Network),
    ProfileDef("charging", "Charging Only", "Sync while plugged in", Icons.Default.BatteryChargingFull, ProfileCategory.Power),
    ProfileDef("night", "Night Sync", "Sync overnight (11 PM - 6 AM)", Icons.Default.Nightlight, ProfileCategory.Power),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfilesScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val activeProfile by settingsStore.activeProfile.collectAsStateWithLifecycle(initialValue = "default")
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf(ProfileCategory.All) }
    var query by rememberSaveable { mutableStateOf("") }

    val filteredProfiles = profiles.filter { profile ->
        val categoryMatches = selectedCategory == ProfileCategory.All || profile.category == selectedCategory
        val queryMatches = query.isBlank() ||
            profile.label.contains(query, ignoreCase = true) ||
            profile.description.contains(query, ignoreCase = true)
        categoryMatches && queryMatches
    }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Sync Profiles") },
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Profiles") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Tips") },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (selectedTab == 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Choose a sync profile", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Profiles combine network and power rules so you can switch behavior quickly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                return@Column
            }

            Text(
                "Choose when syncing is allowed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search profiles") },
            )
            Spacer(Modifier.height(12.dp))
            Row {
                FilterChip(
                    selected = selectedCategory == ProfileCategory.All,
                    onClick = { selectedCategory = ProfileCategory.All },
                    label = { Text("All") },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = selectedCategory == ProfileCategory.Network,
                    onClick = { selectedCategory = ProfileCategory.Network },
                    label = { Text("Network") },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = selectedCategory == ProfileCategory.Power,
                    onClick = { selectedCategory = ProfileCategory.Power },
                    label = { Text("Power") },
                )
            }
            Spacer(Modifier.height(12.dp))

            filteredProfiles.forEach { profile ->
                val selected = activeProfile == profile.id
                Card(
                    onClick = {
                        scope.launch {
                            settingsStore.setActiveProfile(profile.id)
                            when (profile.id) {
                                "wifi_only" -> {
                                    settingsStore.setWifiOnly(true)
                                    settingsStore.setChargingOnly(false)
                                    settingsStore.setSchedulerEnabled(false)
                                }
                                "charging" -> {
                                    settingsStore.setWifiOnly(false)
                                    settingsStore.setChargingOnly(true)
                                    settingsStore.setSchedulerEnabled(false)
                                }
                                "default" -> {
                                    settingsStore.setWifiOnly(false)
                                    settingsStore.setChargingOnly(false)
                                    settingsStore.setSchedulerEnabled(false)
                                }
                                "night" -> {
                                    settingsStore.setWifiOnly(false)
                                    settingsStore.setChargingOnly(false)
                                    settingsStore.setSchedulerEnabled(true)
                                    settingsStore.setSchedulerStartHour(23)
                                    settingsStore.setSchedulerStartMinute(0)
                                    settingsStore.setSchedulerEndHour(6)
                                    settingsStore.setSchedulerEndMinute(0)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = if (selected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) else CardDefaults.cardColors(),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Icon(
                            profile.icon, null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
