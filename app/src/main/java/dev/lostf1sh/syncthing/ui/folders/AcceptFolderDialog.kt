package dev.lostf1sh.syncthing.ui.folders

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PendingFolderUi(
    val folderId: String,
    val label: String,
    val offeredByDevice: String,
    val offeredByName: String,
)

/**
 * Basic defensive check on user-entered destination paths.
 * Rejects empty strings, control characters, and paths that don't live under
 * something sensible — callers may tighten this further (e.g. must be under
 * the external storage root or an app-specific directory).
 */
private fun isAcceptableFolderPath(path: String): Boolean {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.any { it.isISOControl() }) return false
    if (!trimmed.startsWith("/")) return false
    // Block obvious attempts at traversal out of the chosen root.
    if (trimmed.contains("..")) return false
    return true
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AcceptFolderDialog(
    pending: PendingFolderUi,
    onAccept: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultPath = "${Environment.getExternalStorageDirectory().absolutePath}/${pending.label.ifBlank { pending.folderId }}"
    var selectedPath by remember { mutableStateOf(defaultPath) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            val path = if (docId.startsWith("primary:")) {
                "${Environment.getExternalStorageDirectory().absolutePath}/${docId.removePrefix("primary:")}"
            } else {
                docId
            }
            selectedPath = path
        }
    }

    // Expressive: ModalBottomSheet instead of AlertDialog
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Share Folder",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${pending.offeredByName.ifBlank { pending.offeredByDevice.take(7) }} wants to share \"${pending.label.ifBlank { pending.folderId }}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Save to:",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            val pathValid = isAcceptableFolderPath(selectedPath)
            OutlinedTextField(
                value = selectedPath,
                onValueChange = { raw ->
                    // Strip control chars / newlines; user-entered paths should
                    // not contain them and they break the native config parser.
                    selectedPath = raw.filter { !it.isISOControl() }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                isError = !pathValid,
                supportingText = if (!pathValid) {
                    { Text("Choose an absolute path that you control.") }
                } else null,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { folderPicker.launch(null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Browse...")
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Ignore") }
                Spacer(Modifier.size(8.dp))
                // Expressive primary CTA — shape morph on press
                Button(
                    onClick = { onAccept(selectedPath.trim()) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                    enabled = pathValid,
                ) { Text("Accept") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
