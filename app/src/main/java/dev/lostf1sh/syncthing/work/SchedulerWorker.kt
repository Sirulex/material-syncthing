package dev.lostf1sh.syncthing.work

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.service.SyncthingService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SchedulerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val constraints = SyncConstraints(applicationContext)
        val enabled = settings.schedulerEnabled.first()
        if (!enabled) return Result.success()
        val decision = constraints.observe(settings).first()
        val intent = Intent(applicationContext, SyncthingService::class.java)
        when (decision) {
            is SyncConstraints.ConstraintState.ShouldRun -> {
                intent.action = SyncthingService.ACTION_START
                ContextCompat.startForegroundService(applicationContext, intent)
            }
            is SyncConstraints.ConstraintState.ShouldPause -> {
                intent.action = SyncthingService.ACTION_PAUSE
                applicationContext.startService(intent)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "syncthing_scheduler"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SchedulerWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
