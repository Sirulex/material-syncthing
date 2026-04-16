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
