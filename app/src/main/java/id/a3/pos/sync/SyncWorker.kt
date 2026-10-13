package id.a3.pos.sync

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val logEntry = "[$nowStr] WorkManager trigger sync: Battery=$batteryPct%, attempt=$runAttemptCount"
        Log.i("SyncWorker", logEntry)

        val prefs = applicationContext.getSharedPreferences("spike_sync_logs", Context.MODE_PRIVATE)
        val existing = prefs.getString("logs", "") ?: ""
        val updated = "$logEntry\n$existing".take(5000)
        prefs.edit().putString("logs", updated).apply()

        return Result.success()
    }
}
