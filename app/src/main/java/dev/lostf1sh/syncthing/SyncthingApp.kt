package dev.lostf1sh.syncthing

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dev.lostf1sh.syncthing.di.AppContainer
import dev.lostf1sh.syncthing.widget.SyncthingGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncthingApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        startWidgetUpdater()
    }

    /**
     * Keep the Glance widget in sync with global health. Collector lives in the
     * Application scope — not the Service scope — so widgets still refresh when
     * Syncthing is paused or the FGS is recycled.
     */
    private fun startWidgetUpdater() {
        appScope.launch {
            container.appState.health.collect {
                try {
                    val manager = GlanceAppWidgetManager(this@SyncthingApp)
                    if (manager.getGlanceIds(SyncthingGlanceWidget::class.java).isNotEmpty()) {
                        SyncthingGlanceWidget().updateAll(this@SyncthingApp)
                    }
                } catch (_: Exception) { }
            }
        }
    }
}
