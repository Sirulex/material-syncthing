package dev.lostf1sh.syncthing.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PendingFolderUi(
    val folderId: String,
    val label: String,
    val offeredByDevice: String,
    val offeredByName: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AcceptFolderDialog(
    pending: PendingFolderUi,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = MaterialTheme.shapes.extraLarge,
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
                    text = "${pending.offeredByName.ifBlank { pending.offeredByDevice.take(7) }} wants to share folder \"${pending.label.ifBlank { pending.folderId }}\" with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Folder ID: ${pending.folderId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Ignore")
                    }
                    Spacer(Modifier.size(8.dp))
                    FilledTonalButton(
                        onClick = onAccept,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Accept")
                    }
                }
            }
        }
    }
}
