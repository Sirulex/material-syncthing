package dev.sirulex.syncthing.ui.devices

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import dev.sirulex.syncthing.api.dto.Device
import kotlinx.coroutines.launch

private enum class CompressionOption(val wire: String, val label: String) {
    Metadata("metadata", "Metadata only"),
    Always("always", "Always"),
    Never("never", "Never"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDeviceScreen(
    deviceId: String,
    initialName: String,
    initialAddresses: List<String>,
    initialCompression: String,
    initialIntroducer: Boolean,
    initialAutoAcceptFolders: Boolean,
    nameOnly: Boolean = false,
    onSave: suspend (name: String, addresses: List<String>, compression: String, introducer: Boolean, autoAcceptFolders: Boolean) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(initialName) { mutableStateOf(initialName) }
    var addressesText by remember(initialAddresses) { mutableStateOf(initialAddresses.joinToString(", ")) }
    var selectedCompression by remember(initialCompression) {
        mutableStateOf(
            CompressionOption.entries.firstOrNull { it.wire == initialCompression }
                ?: CompressionOption.Metadata
        )
    }
    var introducer by remember(initialIntroducer) { mutableStateOf(initialIntroducer) }
    var autoAcceptFolders by remember(initialAutoAcceptFolders) { mutableStateOf(initialAutoAcceptFolders) }
    var isLoading by remember { mutableStateOf(false) }

    val addressesValid = addressesText.isBlank() || addressesText.split(",").all { it.trim().isNotBlank() }
    val formValid = addressesValid

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(if (nameOnly) "Edit Device Name" else "Edit Device") },
                subtitle = { Text(deviceId.take(7)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isLoading) {
                MediumExtendedFloatingActionButton(
                    onClick = {
                        if (!formValid) return@MediumExtendedFloatingActionButton
                        isLoading = true
                        scope.launch {
                            val addresses = addressesText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .ifEmpty { listOf("dynamic") }
                            val result = onSave(
                                name.trim(),
                                addresses,
                                selectedCompression.wire,
                                introducer,
                                autoAcceptFolders,
                            )
                            isLoading = false
                            result.fold(
                                onSuccess = { onBack() },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(
                                        message = error.message ?: "Failed to update device",
                                    )
                                },
                            )
                        }
                    },
                    icon = {
                        Icon(
                            if (formValid) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (formValid) "Save Changes" else "Complete Form") },
                )
            }
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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Device Name") },
                placeholder = { Text("My Phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
            )

            if (!nameOnly) {
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = addressesText,
                    onValueChange = { addressesText = it },
                    label = { Text("Addresses") },
                    supportingText = {
                        if (addressesText.isBlank()) Text("Leave blank for automatic discovery")
                        else if (!addressesValid) Text("Enter comma-separated addresses")
                        else Text("Comma-separated list of addresses")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    enabled = !isLoading,
                )

                Spacer(Modifier.height(16.dp))
                Text("Compression", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                CompressionOption.entries.forEach { option ->
                    FilterChip(
                        selected = selectedCompression == option,
                        onClick = { selectedCompression = option },
                        label = { Text(option.label) },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text("Options", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                ListItem(
                    headlineContent = { Text("Introducer") },
                    supportingContent = {
                        Text("Automatically add devices it shares mutual folders with")
                    },
                    trailingContent = {
                        Switch(
                            checked = introducer,
                            onCheckedChange = { introducer = it },
                            enabled = !isLoading,
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Auto-Accept Folders") },
                    supportingContent = {
                        Text("Automatically accept folders shared by this device")
                    },
                    trailingContent = {
                        Switch(
                            checked = autoAcceptFolders,
                            onCheckedChange = { autoAcceptFolders = it },
                            enabled = !isLoading,
                        )
                    },
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
