package dev.lostf1sh.syncthing.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.ui.devices.DeviceDetailScreen
import dev.lostf1sh.syncthing.ui.folders.FolderDetailScreen
import dev.lostf1sh.syncthing.ui.home.HomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Placeholder data until wired to real repositories (needs running service)
    val folders = remember { emptyList<Folder>() }
    val folderStates = remember { emptyMap<String, String>() }
    val devices = remember { emptyList<Device>() }
    val deviceConnections = remember { emptyMap<String, Boolean>() }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                folders = folders,
                folderStates = folderStates,
                devices = devices,
                deviceConnections = deviceConnections,
                onFolderClick = { navController.navigate(FolderRoute(it)) },
                onDeviceClick = { navController.navigate(DeviceRoute(it)) },
                onSettingsClick = { /* Phase 8 */ },
            )
        }
        composable<FolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderRoute>()
            FolderDetailScreen(
                folder = folders.find { it.id == route.id },
                status = null,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DeviceRoute>()
            DeviceDetailScreen(
                device = devices.find { it.deviceID == route.id },
                connection = null,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
