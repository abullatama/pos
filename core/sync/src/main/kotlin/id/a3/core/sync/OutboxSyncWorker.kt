package id.a3.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Worker cadangan WorkManager untuk sinkronisasi antrean outbox saat aplikasi di latar belakang (F43, SYN-03):
 * - Berjalan setiap 15 menit jika ada koneksi jaringan (NetworkType.CONNECTED).
 * - Mendukung pemicuan instan (enqueueImmediate) saat koneksi terdeteksi pulih.
 */
class OutboxSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val manager = syncManager ?: return Result.success()
        val summary = manager.triggerSync()
        return if (summary.failed > 0 && summary.sent == 0 && summary.duplicates == 0) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "outbox_sync_periodic"
        private const val IMMEDIATE_WORK_NAME = "outbox_sync_immediate"

        @Volatile
        var syncManager: OutboxSyncManager? = null

        fun schedulePeriodic(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<OutboxSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
