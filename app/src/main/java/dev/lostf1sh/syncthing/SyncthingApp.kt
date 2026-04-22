package dev.lostf1sh.syncthing

import android.app.Application
import dev.lostf1sh.syncthing.di.AppContainer

class SyncthingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
