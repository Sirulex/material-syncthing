package dev.lostf1sh.syncthing.ui.folders

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Versioning
import kotlinx.coroutines.launch

private enum class EditFolderTypeOption(val wire: String, val label: String) {
    SendReceive("sendreceive", "Send & Receive"),
    SendOnly("sendonly", "Send Only"),
    ReceiveOnly("receiveonly", "Receive Only"),
}

private enum class VersioningTypeOption(val wire: String, val label: String) {
    None("", "None"),
    Trashcan("trashcan", "Trashcan"),
    Simple("simple", "Simple"),
    Staggered("staggered", "Staggered"),
    External("external", "External"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditFolderScreen(
    folderId: String,
    initialLabel: String,
    initialPath: String,
    initialType: String,
    initialVersioning: Versioning?,
    devices: List<Device>,
    localDeviceId: String?,
    initialSharedDeviceIds: Set<String>,
    onSave: suspend (label: String, path: String, type: String, sharedDeviceIds: Set<String>, versioning: Versioning?) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    var path by remember(initialPath) {
        mutableStateOf(
            if (initialPath.isBlank()) {
                "${Environment.getExternalStorageDirectory().absolutePath}/Syncthing"
            } else initialPath
        )
    }
    var selectedType by remember(initialType) {
        mutableStateOf(
            EditFolderTypeOption.entries.firstOrNull { it.wire == initialType }
                ?: EditFolderTypeOption.SendReceive
        )
    }
    var selectedDeviceIds by remember(initialSharedDeviceIds) {
        mutableStateOf(initialSharedDeviceIds)
    }
    var selectedVersioning by remember(initialVersioning) {
        mutableStateOf(
            VersioningTypeOption.entries.firstOrNull { it.wire == initialVersioning?.type }
                ?: VersioningTypeOption.None
        )
    }
    var versioningCleanout by remember(initialVersioning) {
        mutableStateOf(initialVersioning?.params?.get("cleanoutDays") ?: "0")
    }
    var versioningKeep by remember(initialVersioning) {
        mutableStateOf(
            initialVersioning?.params?.get("keep")
                ?: initialVersioning?.params?.get("keepVersions")
                ?: "5"
        )
    }
    var versioningMaxAge by remember(initialVersioning) {
        mutableStateOf(initialVersioning?.params?.get("maxAge") ?: "31536000")
    }
    var versioningCommand by remember(initialVersioning) {
        mutableStateOf(initialVersioning?.params?.get("command") ?: "")
    }
    var isLoading by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            documentTreePath(
                docId,
                Environment.getExternalStorageDirectory().absolutePath,
            )?.let { resolved -> path = resolved }
        }
    }

    val pathValid = isAcceptableEditFolderPath(path)
    val hasSharedDevices = selectedDeviceIds.isNotEmpty() || !localDeviceId.isNullOrBlank()
    val cleanoutValid = versioningCleanout.toIntOrNull()?.let { it >= 0 } == true
    val versioningValid = when (selectedVersioning) {
        VersioningTypeOption.None -> true
        VersioningTypeOption.Trashcan -> cleanoutValid
        VersioningTypeOption.Simple -> cleanoutValid && (versioningKeep.toIntOrNull() ?: 0) > 0
        VersioningTypeOption.Staggered -> cleanoutValid &&
            versioningMaxAge.toLongOrNull()?.let { it >= 0 } == true
        VersioningTypeOption.External -> versioningCommand.isNotBlank()
    }
    val formValid = pathValid && hasSharedDevices && versioningValid

    fun versioningWith(type: String, params: Map<String, String>): Versioning =
        (initialVersioning ?: Versioning()).copy(type = type, params = params)

    val versioning = when (selectedVersioning) {
        VersioningTypeOption.None -> null
        VersioningTypeOption.Trashcan -> versioningWith(
            "trashcan",
            mapOf("cleanoutDays" to versioningCleanout.ifEmpty { "0" }),
        )
        VersioningTypeOption.Simple -> versioningWith(
            "simple",
            params = mapOf(
                "keep" to versioningKeep.ifEmpty { "5" },
                "cleanoutDays" to versioningCleanout.ifEmpty { "0" },
            ),
        )
        VersioningTypeOption.Staggered -> versioningWith(
            "staggered",
            params = mapOf(
                "maxAge" to versioningMaxAge.ifEmpty { "31536000" },
                "cleanoutDays" to versioningCleanout.ifEmpty { "0" },
            ),
        )
        VersioningTypeOption.External -> versioningWith(
            "external",
            mapOf("command" to versioningCommand.trim()),
        )
    }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Edit Folder") },
                subtitle = { Text(folderId) },
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
                            val result = onSave(
                                label.trim(),
                                path.trim(),
                                selectedType.wire,
                                selectedDeviceIds,
                                versioning,
                            )
                            isLoading = false
                            result.fold(
                                onSuccess = { onBack() },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(
                                        message = error.message ?: "Failed to update folder",
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
                value = label,
                onValueChange = { label = it },
                label = { Text("Folder Label") },
                placeholder = { Text("Photos") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = path,
                onValueChange = { path = it.filter { ch -> !ch.isISOControl() } },
                label = { Text("Path") },
                isError = path.isNotBlank() && !pathValid,
                supportingText = {
                    when {
                        path.isBlank() -> Text("Absolute path required")
                        !pathValid -> Text("Enter a safe absolute path")
                        else -> Text("Path looks good")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !isLoading,
            )

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = { folderPicker.launch(null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Browse Folder")
            }

            Spacer(Modifier.height(16.dp))
            Text("Folder Type")
            Spacer(Modifier.height(8.dp))
            EditFolderTypeOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedType == option,
                    onClick = { selectedType = option },
                    label = { Text(option.label) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text("Shared Devices", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            devices.forEach { device ->
                val isLocal = !localDeviceId.isNullOrBlank() && device.deviceID == localDeviceId
                val selected = isLocal || selectedDeviceIds.contains(device.deviceID)
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (isLocal) "Your Device"
                            else device.name.ifBlank { device.deviceID.take(7) }
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (isLocal) "Required for local sync"
                            else device.deviceID
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = selected,
                            enabled = !isLocal,
                            onCheckedChange = { checked ->
                                selectedDeviceIds = if (checked) {
                                    selectedDeviceIds + device.deviceID
                                } else {
                                    selectedDeviceIds - device.deviceID
                                }
                            },
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Versioning", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            VersioningTypeOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedVersioning == option,
                    onClick = { selectedVersioning = option },
                    label = { Text(option.label) },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (selectedVersioning != VersioningTypeOption.None) {
                if (selectedVersioning == VersioningTypeOption.External) {
                    OutlinedTextField(
                        value = versioningCommand,
                        onValueChange = { versioningCommand = it },
                        label = { Text("Command") },
                        isError = versioningCommand.isBlank(),
                        supportingText = {
                            Text("Executable command using %FOLDER_PATH% and %FILE_PATH% if needed")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                } else {
                    if (selectedVersioning == VersioningTypeOption.Simple) {
                        OutlinedTextField(
                            value = versioningKeep,
                            onValueChange = { versioningKeep = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Keep versions") },
                            isError = versioningKeep.toIntOrNull()?.let { it <= 0 } ?: true,
                            supportingText = { Text("Must be at least 1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (selectedVersioning == VersioningTypeOption.Staggered) {
                        OutlinedTextField(
                            value = versioningMaxAge,
                            onValueChange = { versioningMaxAge = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Max age (seconds)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = versioningCleanout,
                        onValueChange = { versioningCleanout = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Cleanout days (0 = never)") },
                        isError = !cleanoutValid,
                        supportingText = { Text("Enter a whole number of days") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun isAcceptableEditFolderPath(path: String): Boolean {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return false
    if (!trimmed.startsWith('/')) return false
    if (trimmed.contains("..")) return false
    return true
}
