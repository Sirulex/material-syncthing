package dev.lostf1sh.syncthing.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
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
import dev.lostf1sh.syncthing.api.dto.ConnectivityOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectivitySettingsScreen(
    initialOptions: ConnectivityOptions?,
    onSave: suspend (ConnectivityOptions) -> Result<Unit>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var globalDiscovery by remember(initialOptions) {
        mutableStateOf(initialOptions?.globalAnnounceEnabled ?: true)
    }
    var localDiscovery by remember(initialOptions) {
        mutableStateOf(initialOptions?.localAnnounceEnabled ?: true)
    }
    var relaysEnabled by remember(initialOptions) {
        mutableStateOf(initialOptions?.relaysEnabled ?: true)
    }
    var discoveryServers by remember(initialOptions) {
        mutableStateOf(initialOptions?.globalAnnounceServers?.joinToString("\n") ?: "default")
    }
    var listenAddresses by remember(initialOptions) {
        mutableStateOf(initialOptions?.listenAddresses?.joinToString("\n") ?: "default")
    }
    var saving by remember { mutableStateOf(false) }

    val servers = parseConnectivityLines(discoveryServers)
    val addresses = parseConnectivityLines(listenAddresses)
    val serversValid = servers.isNotEmpty() && servers.all(::isValidDiscoveryServer)
    val addressesValid = addresses.isNotEmpty() && addresses.all(::isValidListenAddress)
    val formValid = initialOptions != null && serversValid && addressesValid

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Relay & Discovery") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!saving) {
                MediumExtendedFloatingActionButton(
                    onClick = {
                        if (!formValid) return@MediumExtendedFloatingActionButton
                        saving = true
                        scope.launch {
                            val result = onSave(
                                ConnectivityOptions(
                                    listenAddresses = addresses,
                                    globalAnnounceServers = servers,
                                    globalAnnounceEnabled = globalDiscovery,
                                    localAnnounceEnabled = localDiscovery,
                                    relaysEnabled = relaysEnabled,
                                )
                            )
                            saving = false
                            result.fold(
                                onSuccess = { onBack() },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(
                                        error.message ?: "Could not update connectivity settings"
                                    )
                                },
                            )
                        }
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(if (formValid) "Save" else "Check values") },
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
            ListItem(
                headlineContent = { Text("Global discovery") },
                supportingContent = { Text("Find devices through configured discovery servers") },
                trailingContent = {
                    Switch(
                        checked = globalDiscovery,
                        onCheckedChange = { globalDiscovery = it },
                        enabled = initialOptions != null && !saving,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Local discovery") },
                supportingContent = { Text("Find devices on the local network") },
                trailingContent = {
                    Switch(
                        checked = localDiscovery,
                        onCheckedChange = { localDiscovery = it },
                        enabled = initialOptions != null && !saving,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Relays") },
                supportingContent = { Text("Use relay servers when direct connections fail") },
                trailingContent = {
                    Switch(
                        checked = relaysEnabled,
                        onCheckedChange = { relaysEnabled = it },
                        enabled = initialOptions != null && !saving,
                    )
                },
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = discoveryServers,
                onValueChange = { discoveryServers = it },
                label = { Text("Discovery servers") },
                supportingText = {
                    Text(if (serversValid) "One HTTPS URL per line, or default" else "Use default or valid HTTPS URLs")
                },
                isError = !serversValid,
                enabled = initialOptions != null && !saving,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = listenAddresses,
                onValueChange = { listenAddresses = it },
                label = { Text("Listen & relay addresses") },
                supportingText = {
                    Text("One address per line. Use default, tcp://, quic://, relay:// or dynamic+https://.")
                },
                isError = !addressesValid,
                enabled = initialOptions != null && !saving,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}

internal fun parseConnectivityLines(value: String): List<String> = value
    .split('\n', ',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

internal fun isValidDiscoveryServer(value: String): Boolean =
    value == "default" || runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

internal fun isValidListenAddress(value: String): Boolean {
    if (value == "default") return true
    val separator = value.indexOf("://")
    if (separator < 0 || separator == value.lastIndex - 2) return false
    return value.substring(0, separator) in setOf(
        "tcp", "tcp4", "tcp6", "quic", "quic4", "quic6", "relay", "dynamic+https"
    )
}
