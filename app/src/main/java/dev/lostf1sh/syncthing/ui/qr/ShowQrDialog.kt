package dev.lostf1sh.syncthing.ui.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowQrDialog(
    deviceId: String,
    onDismiss: () -> Unit,
) {
    // Generate the QR bitmap off the main thread — at size=512 the encode +
    // pixel fill is ~20-40ms and used to jank the sheet expansion animation.
    val qrBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, deviceId) {
        value = withContext(Dispatchers.Default) {
            QrCodeGenerator.generate(deviceId).asImageBitmap()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Expressive: ModalBottomSheet — drag-to-dismiss, snappy motion
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Device ID",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            val bmp = qrBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "QR code for device ID",
                    modifier = Modifier.size(240.dp),
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = deviceId,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Close")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
