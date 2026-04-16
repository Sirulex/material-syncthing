package dev.lostf1sh.syncthing.di

import android.content.Context

class AppContainer(private val appContext: Context) {
    // Singletons added as modules are built:
    // - SyncthingClient (Phase 4)
    // - FolderRepository, DeviceRepository, etc. (Phase 5+)
    // - SettingsStore (Phase 8)
}
