package dev.sirulex.syncthing.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrScannerScreen(
    onDeviceIdScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Camera permission is required to scan QR codes")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Scan Device QR") },
                subtitle = { Text("Point the camera at a Syncthing device ID") },
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
        modifier = modifier,
    ) { innerPadding ->
        if (hasCameraPermission) {
            QrCameraPreview(
                onDeviceIdScanned = onDeviceIdScanned,
                onInvalidScan = {
                    scope.launch {
                        snackbarHostState.showSnackbar("QR code does not contain a valid Syncthing device ID")
                    }
                },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            CameraPermissionPrompt(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text("Camera access is needed to scan a device QR code")
            Button(
                onClick = onRequestPermission,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Grant Camera Access")
            }
        }
    }
}

@Composable
private fun QrCameraPreview(
    onDeviceIdScanned: (String) -> Unit,
    onInvalidScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDeviceIdScanned by rememberUpdatedState(onDeviceIdScanned)
    val currentOnInvalidScan by rememberUpdatedState(onInvalidScan)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val delivered = remember { AtomicBoolean(false) }
    val analyzing = remember { AtomicBoolean(false) }
    val invalidReported = remember { AtomicBoolean(false) }
    val active = remember { AtomicBoolean(true) }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (!active.get()) return@Runnable
            try {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            scanImageProxy(
                                scanner = scanner,
                                imageProxy = imageProxy,
                                analyzing = analyzing,
                                delivered = delivered,
                                invalidReported = invalidReported,
                                active = active,
                                onDeviceIdScanned = currentOnDeviceIdScanned,
                                onInvalidScan = currentOnInvalidScan,
                            )
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer,
                )
            } catch (e: Exception) {
                Log.w("QrScannerScreen", "Could not bind camera preview", e)
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            try {
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        FilledTonalButton(
            onClick = { },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            enabled = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text("Scanning...")
        }
    }
}

private fun scanImageProxy(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    analyzing: AtomicBoolean,
    delivered: AtomicBoolean,
    invalidReported: AtomicBoolean,
    active: AtomicBoolean,
    onDeviceIdScanned: (String) -> Unit,
    onInvalidScan: () -> Unit,
) {
    if (!active.get() || delivered.get() || !analyzing.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        analyzing.set(false)
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    try {
        scanner.process(image)
            .addOnCompleteListener { task ->
                // Release CameraX's frame before navigating away. Navigating from a
                // success callback while ImageAnalysis still owns this frame races
                // screen disposal, analyzer shutdown, and camera unbinding.
                analyzing.set(false)
                imageProxy.close()

                if (!active.get()) return@addOnCompleteListener
                if (!task.isSuccessful) {
                    Log.d("QrScannerScreen", "QR scan failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val barcodes = task.result.orEmpty()
                val scannedId = barcodes
                    .asSequence()
                    .mapNotNull { it.rawValue }
                    .mapNotNull(DeviceIdValidator::extract)
                    .firstOrNull()
                if (scannedId != null && delivered.compareAndSet(false, true)) {
                    onDeviceIdScanned(scannedId)
                } else if (barcodes.isNotEmpty() && invalidReported.compareAndSet(false, true)) {
                    onInvalidScan()
                }
            }
    } catch (e: Exception) {
        analyzing.set(false)
        imageProxy.close()
        Log.d("QrScannerScreen", "Could not submit camera frame", e)
    }
}
