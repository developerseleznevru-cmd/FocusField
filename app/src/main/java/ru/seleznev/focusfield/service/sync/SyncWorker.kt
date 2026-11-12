package ru.seleznev.focusfield.service.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.seleznev.focusfield.FocusFieldApp
import ru.seleznev.focusfield.data.repo.SyncOutcome
import java.util.concurrent.TimeUnit

/** Фоновая периодическая синхронизация с REST API (WorkManager). */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FocusFieldApp).container
        return when (val outcome = container.remoteSyncRepository.syncNow()) {
            SyncOutcome.Disabled -> Result.success()
            SyncOutcome.Success -> Result.success()
            is SyncOutcome.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "focusfield_sync"

        /** Планирует или отменяет периодическую синхронизацию в зависимости от настроек. */
        fun ensureScheduled(context: Context, syncEnabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!syncEnabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
