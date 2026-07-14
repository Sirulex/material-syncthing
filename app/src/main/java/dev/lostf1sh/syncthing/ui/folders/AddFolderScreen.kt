package dev.lostf1sh.syncthing.ui.folders

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import kotlinx.coroutines.launch

private enum class FolderTypeOption(val wire: String, val label: String) {
    SendReceive("sendreceive", "Send & Receive"),
    SendOnly("sendonly", "Send Only"),
    ReceiveOnly("receiveonly", "Receive Only"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddFolderScreen(
    onAdd: suspend (folderId: String, label: String, path: String, type: String) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var folderLabel by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf("") }
    var folderPath by remember {
        mutableStateOf("${Environment.getExternalStorageDirectory().absolutePath}/Syncthing")
    }
    var selectedType by remember { mutableStateOf(FolderTypeOption.SendReceive) }
    var idEditedManually by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            val resolved = documentTreePath(
                docId,
                Environment.getExternalStorageDirectory().absolutePath,
            )
            if (resolved != null) {
                folderPath = resolved
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "This storage provider does not expose a filesystem path"
                    )
                }
            }
        }
    }

    val idValid = isValidFolderId(folderId)
    val pathValid = isAcceptableFolderPath(folderPath)
    val formValid = idValid && pathValid

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Add Folder") },
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
                            val result = onAdd(
                                folderId.trim(),
                                folderLabel.trim(),
                                folderPath.trim(),
                                selectedType.wire,
                            )
                            isLoading = false
                            result.fold(
                                onSuccess = { onBack() },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(
                                        message = error.message ?: "Failed to add folder",
                                    )
                                },
                            )
                        }
                    },
                    icon = {
                        Icon(
                            if (formValid) Icons.Default.Check else Icons.Default.CreateNewFolder,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (formValid) "Create Folder" else "Complete Form") },
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
                value = folderLabel,
                onValueChange = { raw ->
                    folderLabel = raw
                    if (!idEditedManually) {
                        folderId = suggestFolderId(raw)
                    }
                },
                label = { Text("Folder Label (optional)") },
                placeholder = { Text("Photos") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = folderId,
                onValueChange = { raw ->
                    idEditedManually = true
                    folderId = normalizeFolderId(raw)
                },
                label = { Text("Folder ID") },
                placeholder = { Text("photos") },
                isError = folderId.isNotBlank() && !idValid,
                enabled = !isLoading,
                supportingText = {
                    when {
                        folderId.isBlank() -> Text("Lowercase letters, numbers, -, _, .")
                        !idValid -> Text("Invalid ID format")
                        else -> Text("Valid folder ID")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = folderPath,
                onValueChange = { folderPath = it.filter { ch -> !ch.isISOControl() } },
                label = { Text("Path") },
                placeholder = { Text("/storage/emulated/0/Syncthing/Photos") },
                isError = folderPath.isNotBlank() && !pathValid,
                enabled = !isLoading,
                supportingText = {
                    when {
                        folderPath.isBlank() -> Text("Absolute path required")
                        !pathValid -> Text("Enter a safe absolute path")
                        else -> Text("Path looks good")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FolderTypeOption.entries.forEach { option ->
                    FilterChip(
                        selected = selectedType == option,
                        onClick = { selectedType = option },
                        label = { Text(option.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Tip: You can share this folder with remote devices after creating it.",
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

private fun isValidFolderId(id: String): Boolean {
    val trimmed = id.trim()
    if (trimmed.isEmpty()) return false
    return Regex("^[a-z0-9](?:[a-z0-9._-]{0,63})$").matches(trimmed)
}

private fun normalizeFolderId(raw: String): String {
    val cleaned = buildString(raw.length) {
        raw.trim().lowercase().forEach { ch ->
            when {
                ch.isLetterOrDigit() -> append(ch)
                ch == '-' || ch == '_' || ch == '.' -> append(ch)
                ch.isWhitespace() -> append('-')
            }
        }
    }
    return cleaned
        .replace(Regex("-+"), "-")
        .trim('-', '_', '.')
}

private fun suggestFolderId(label: String): String {
    val suggested = normalizeFolderId(label)
    return if (suggested.isBlank()) "folder" else suggested
}

private fun isAcceptableFolderPath(path: String): Boolean {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return false
    if (!trimmed.startsWith('/')) return false
    if (trimmed.contains("..")) return false
    return true
}
