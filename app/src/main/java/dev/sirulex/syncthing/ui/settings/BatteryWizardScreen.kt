package dev.sirulex.syncthing.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sirulex.syncthing.data.BatteryOptimizationDetector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatteryWizardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    // Re-query whitelist status every time we come back to the screen, so the
    // "you're all set" state updates after the user grants from system dialog.
    var whitelistTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) whitelistTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val whitelisted = remember(whitelistTick) {
        BatteryOptimizationDetector.isIgnoringOptimizations(context)
    }
    val hasAllFilesAccess = remember(whitelistTick) {
        if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager()
        else true
    }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Reliability") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> StepCard(
                        icon = Icons.Default.Battery5Bar,
                        title = "Battery optimization",
                        body = if (whitelisted)
                            "Granted. Android won't kill the sync service for battery reasons."
                        else
                            "Grant Syncthing unrestricted background access so Android doesn't pause sync to save battery.",
                        primaryLabel = if (whitelisted) "Already granted" else "Grant",
                        primaryEnabled = !whitelisted,
                        onPrimary = {
                            try {
                                context.startActivity(BatteryOptimizationDetector.buildRequestIgnoreIntent(context))
                            } catch (_: ActivityNotFoundException) {
                                context.startActivity(BatteryOptimizationDetector.buildAppInfoIntent(context))
                            }
                            whitelistTick++
                        },
                        secondaryLabel = "Re-check",
                        onSecondary = { whitelistTick++ },
                    )

                    1 -> StepCard(
                        icon = Icons.Default.Phone,
                        title = "Manufacturer autostart",
                        body = "On ${BatteryOptimizationDetector.manufacturerFriendlyName()}, Android may still kill the service. Open the vendor autostart settings and allow Syncthing.",
                        primaryLabel = "Open autostart settings",
                        onPrimary = {
                            val intent = BatteryOptimizationDetector.buildOemAutostartIntent()
                                ?: BatteryOptimizationDetector.buildAppInfoIntent(context)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                context.startActivity(BatteryOptimizationDetector.buildAppInfoIntent(context))
                            }
                        },
                        secondaryLabel = "Open app info",
                        onSecondary = {
                            context.startActivity(BatteryOptimizationDetector.buildAppInfoIntent(context))
                        },
                    )

                    2 -> StepCard(
                        icon = Icons.Default.Settings,
                        title = "All files access",
                        body = if (hasAllFilesAccess)
                            "Granted. Conflict detection and share-target copy can access your folders."
                        else
                            "Syncthing needs All Files access (API 30+) so it can scan folder contents for conflicts and receive shared files.",
                        primaryLabel = if (hasAllFilesAccess) "Already granted" else "Grant",
                        primaryEnabled = !hasAllFilesAccess,
                        onPrimary = {
                            if (android.os.Build.VERSION.SDK_INT >= 30) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                        )
                                    )
                                }
                            }
                        },
                        secondaryLabel = "Re-check",
                        onSecondary = { whitelistTick++ },
                    )

                    3 -> StepCard(
                        icon = Icons.Default.Check,
                        title = "All set",
                        body = if (whitelisted && hasAllFilesAccess)
                            "All reliability settings are granted. If sync still pauses on this device, revisit the manufacturer autostart step."
                        else
                            "Some steps are still incomplete. Go back to grant the remaining permissions.",
                        primaryLabel = "Done",
                        onPrimary = onBack,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = pagerState.currentPage > 0,
                ) { Text("Back") }
                Text(
                    "${pagerState.currentPage + 1} / 4",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onBack()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(if (pagerState.currentPage == 3) "Finish" else "Next")
                    Icon(
                        Icons.Default.ChevronRight, null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepCard(
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onPrimary,
                shapes = ButtonDefaults.shapes(),
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryLabel)
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondary,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(secondaryLabel) }
            }
        }
    }
}
