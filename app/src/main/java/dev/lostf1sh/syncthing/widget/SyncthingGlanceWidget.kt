package dev.lostf1sh.syncthing.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.lostf1sh.syncthing.MainActivity
import dev.lostf1sh.syncthing.SyncthingApp
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService

class SyncthingGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { WidgetContent() }
        }
    }
}

@Composable
private fun WidgetContent() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as SyncthingApp
    val health by app.container.appState.health.collectAsState(initial = null)
    val serviceState by SyncthingService.state.collectAsState(initial = RunState.Stopped)

    val folderCount = health?.folderCount ?: 0
    val connectedDevices = health?.connectedDevices ?: 0
    val issueCount = health?.issues?.size ?: 0

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.primaryContainer)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Text(
                text = "Syncthing",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = stateLabel(serviceState),
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            "$folderCount folders · $connectedDevices connected",
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
        )
        if (issueCount > 0) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                "$issueCount issue(s)",
                style = TextStyle(color = GlanceTheme.colors.error),
            )
        }
        Spacer(GlanceModifier.height(12.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            val toggleAction = when (serviceState) {
                is RunState.Running, is RunState.Starting -> SyncthingService.ACTION_PAUSE
                else -> SyncthingService.ACTION_START
            }
            val toggleLabel = when (serviceState) {
                is RunState.Running, is RunState.Starting -> "Pause"
                else -> "Start"
            }
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(6.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(
                        actionStartService(
                            Intent(ctx, SyncthingService::class.java).apply { action = toggleAction },
                            isForegroundService = true,
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    toggleLabel,
                    style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(6.dp)
                    .background(GlanceTheme.colors.tertiaryContainer)
                    .clickable(
                        actionStartService(
                            Intent(ctx, SyncthingService::class.java).apply {
                                action = SyncthingService.ACTION_RESCAN_ALL
                            },
                            isForegroundService = true,
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Rescan",
                    style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer),
                )
            }
        }
    }
}

private fun stateLabel(state: RunState): String = when (state) {
    is RunState.Stopped -> "Stopped"
    is RunState.Starting -> "Starting…"
    is RunState.Running -> "Running"
    is RunState.Paused -> "Paused"
    is RunState.Crashed -> "Crashed"
}

class SyncthingGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SyncthingGlanceWidget()
}
