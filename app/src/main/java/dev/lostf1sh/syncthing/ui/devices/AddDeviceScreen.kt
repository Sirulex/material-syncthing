package dev.lostf1sh.syncthing.ui.devices

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import dev.lostf1sh.syncthing.ui.qr.DeviceIdValidator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddDeviceScreen(
    initialDeviceId: String = "",
    onAdd: suspend (deviceId: String, name: String, shareExistingFolders: Boolean) -> Result<Unit>,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var deviceId by remember { mutableStateOf(initialDeviceId) }
    var deviceName by remember { mutableStateOf("") }
    var shareExistingFolders by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    val isValid = DeviceIdValidator.isValid(deviceId)

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Add Device") },
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
                LargeExtendedFloatingActionButton(
                    onClick = {
                        if (!isValid) return@LargeExtendedFloatingActionButton
                        isLoading = true
                        scope.launch {
                            val result = onAdd(
                                deviceId.trim().uppercase(),
                                deviceName.trim(),
                                shareExistingFolders,
                            )
                            isLoading = false
                            result.fold(
                                onSuccess = { onBack() },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(
                                        message = error.message ?: "Failed to add device",
                                    )
                                },
                            )
                        }
                    },
                    icon = {
                        Icon(
                            if (isValid) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (isValid) "Add Device" else "Enter Valid ID") },
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

            if (isLoading) {
                ContainedLoadingIndicator(
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            OutlinedTextField(
                value = deviceId,
                onValueChange = { raw ->
                    // Sanitize pasted/typed input: strip whitespace, zero-width chars,
                    // normalize unicode dashes, uppercase. Also harvests a valid-looking
                    // ID out of surrounding text on paste (e.g. "My ID: MFZWI…").
                    val cleaned = DeviceIdValidator.sanitize(raw).uppercase()
                    deviceId = DeviceIdValidator.extract(cleaned) ?: cleaned
                },
                label = { Text("Device ID") },
                placeholder = { Text("XXXXXXX-XXXXXXX-XXXXXXX-...") },
                isError = deviceId.isNotBlank() && !isValid,
                enabled = !isLoading,
                supportingText = {
                    when {
                        deviceId.isBlank() -> Text("Paste or type the remote device ID")
                        !isValid -> Text(
                            "Invalid format: need 8 groups of 7 characters",
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Text(
                            "Valid device ID",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
            )

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onScanQr,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Scan QR Code Instead")
            }

            Spacer(Modifier.height(16.dp))

            TextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name (optional)") },
                placeholder = { Text("My Laptop") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
            )

            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Share existing folders") },
                supportingContent = {
                    Text("Add this device to current folder configs so data can sync.")
                },
                trailingContent = {
                    Checkbox(
                        checked = shareExistingFolders,
                        onCheckedChange = { shareExistingFolders = it },
                        enabled = !isLoading,
                    )
                },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "The remote device must also add this device, and each folder must be shared on both sides.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}
