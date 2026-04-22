package dev.lostf1sh.syncthing.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class FolderRoute(val id: String)

@Serializable
object DevicesRoute

@Serializable
data class DeviceRoute(val id: String)

@Serializable
object SettingsRoute

@Serializable
data class AddDeviceRoute(val prefillId: String = "")

@Serializable
object AddFolderRoute

@Serializable
object QrScannerRoute

@Serializable
object OnboardingRoute

@Serializable
object ErrorCenterRoute

@Serializable
object ConflictRoute

@Serializable
object ProfilesRoute

@Serializable
object DiagnosticsRoute

@Serializable
object InsightsRoute

@Serializable
object RecentChangesRoute

@Serializable
object BatteryWizardRoute

/** One-shot navigation intent from a shortcut / widget / share. Consumed on delivery. */
sealed interface PendingShortcut {
    data object ErrorCenter : PendingShortcut
    data object Insights : PendingShortcut
    data class Share(val uris: List<android.net.Uri>) : PendingShortcut
}

@Serializable
object ShareTargetRoute

@Serializable
data class FolderBrowserRoute(val folderId: String, val prefix: String = "")

@Serializable
data class IgnoreEditorRoute(val folderId: String)
