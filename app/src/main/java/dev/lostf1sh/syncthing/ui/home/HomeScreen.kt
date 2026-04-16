package dev.lostf1sh.syncthing.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val state by SyncthingService.state.collectAsState()
    val isRunning = state is RunState.Running || state is RunState.Starting

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Syncthing Service",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stateLabel(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isRunning,
                    onCheckedChange = { enabled ->
                        val intent = Intent(context, SyncthingService::class.java).apply {
                            action = if (enabled) SyncthingService.ACTION_START
                                     else SyncthingService.ACTION_STOP
                        }
                        ContextCompat.startForegroundService(context, intent)
                    },
                )
            }
        }
    }
}

private fun stateLabel(state: RunState): String = when (state) {
    is RunState.Stopped -> "Stopped"
    is RunState.Starting -> "Starting..."
    is RunState.Running -> "Running on port ${state.port}"
    is RunState.Crashed -> "Crashed: ${state.reason}"
    is RunState.Paused -> "Paused: ${state.reason}"
}
