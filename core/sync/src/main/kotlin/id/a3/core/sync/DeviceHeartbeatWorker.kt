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
import id.a3.core.auth.DeviceHeartbeatResult
import java.util.concurrent.TimeUnit

/**
 * Worker latar belakang untuk pengiriman detak jantung periodik tablet (F41 & OPS-01):
 * - Dijalankan setiap 15 menit menggunakan WorkManager.
 * - Memerlukan konektivitas jaringan (NetworkType.CONNECTED).
 * - Mendukung pemicuan langsung (OneTime) saat dibutuhkan layar Admin atau sinkron.
 */
class DeviceHeartbeatWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val manager = heartbeatManager ?: return Result.success()

        val result = manager.executeHeartbeat(applicationContext)
        return when (result) {
            is DeviceHeartbeatResult.Success,
            is DeviceHeartbeatResult.SkippedNotActive,
            is DeviceHeartbeatResult.DeviceRevoked -> Result.success()
            is DeviceHeartbeatResult.Error -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "device_heartbeat_periodic"
        private const val IMMEDIATE_WORK_NAME = "device_heartbeat_immediate"

        @Volatile
        var heartbeatManager: DeviceHeartbeatManager? = null

        fun schedulePeriodic(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DeviceHeartbeatWorker>(
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

            val workRequest = OneTimeWorkRequestBuilder<DeviceHeartbeatWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
