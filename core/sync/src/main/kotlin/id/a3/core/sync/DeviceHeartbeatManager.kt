package id.a3.core.sync

import android.content.Context
import android.os.BatteryManager
import id.a3.core.auth.DeviceHeartbeatResult
import id.a3.core.auth.DeviceManager
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.domain.monitoring.AppCrashManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manajer pengiriman detak jantung (Heartbeat) tablet ke server backend (F41):
 * - Mengumpulkan telemetri lokal: pending outbox, failed outbox, persentase baterai, sync timestamp.
 * - Mengirim ke POST /v1/devices/heartbeat lewat DeviceManager.
 * - Mencatat hasil atau error ke AppCrashManager (sanitized).
 */
class DeviceHeartbeatManager(
    private val deviceManager: DeviceManager,
    private val outboxDao: OutboxDao,
    private val printerStatusProvider: (() -> String)? = null
) {

    suspend fun executeHeartbeat(context: Context? = null): DeviceHeartbeatResult {
        val outboxPending = try {
            outboxDao.getPendingCount()
        } catch (_: Exception) {
            0
        }

        val outboxProblem = try {
            outboxDao.getFailedCount()
        } catch (_: Exception) {
            0
        }

        val batteryPercent = context?.let { getBatteryLevel(it) }
        val printerStatus = printerStatusProvider?.invoke() ?: "unknown"

        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val lastSyncAt = isoDateFormat.format(Date())

        val result = deviceManager.sendHeartbeat(
            outboxPending = outboxPending,
            outboxProblem = outboxProblem,
            batteryPercent = batteryPercent,
            lastSyncAt = lastSyncAt,
            printerStatus = printerStatus
        )

        when (result) {
            is DeviceHeartbeatResult.Success -> {
                AppCrashManager.reporter.log("Heartbeat sent successfully: outboxPending=$outboxPending, battery=$batteryPercent%, printer=$printerStatus")
            }
            is DeviceHeartbeatResult.DeviceRevoked -> {
                AppCrashManager.reporter.log("Device REVOKED received during heartbeat. Tablet locked.")
            }
            is DeviceHeartbeatResult.SkippedNotActive -> {
                // Device not yet active, normal when setting up
            }
            is DeviceHeartbeatResult.Error -> {
                AppCrashManager.reporter.log("Heartbeat failed: [${result.code}] ${result.message}")
            }
        }

        return result
    }

    companion object {
        fun getBatteryLevel(context: Context): Int? {
            return try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (capacity in 0..100) capacity else null
            } catch (_: Exception) {
                null
            }
        }

        fun create(
            context: Context,
            deviceManager: DeviceManager,
            printerStatusProvider: (() -> String)? = null
        ): DeviceHeartbeatManager {
            val db = id.a3.core.data.local.AppDatabase.getInstance(context)
            return DeviceHeartbeatManager(
                deviceManager = deviceManager,
                outboxDao = db.outboxDao(),
                printerStatusProvider = printerStatusProvider
            )
        }
    }
}
