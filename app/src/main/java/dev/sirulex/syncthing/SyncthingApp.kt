package dev.sirulex.syncthing

import android.app.Application
import dev.sirulex.syncthing.di.AppContainer

class SyncthingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
