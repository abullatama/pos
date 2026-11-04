package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outbox: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOutboxByStatus(status: OutboxStatus = OutboxStatus.PENDING, limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE bundleId = :bundleId")
    suspend fun getByBundleId(bundleId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE eventsJson LIKE '%' || :orderId || '%' ORDER BY createdAt ASC")
    suspend fun findBundlesByOrderId(orderId: String): List<OutboxEntity>

    @Query("UPDATE outbox SET status = 'SENT', flagsJson = :flagsJson, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun markSent(bundleId: String, flagsJson: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET status = 'FAILED', lastError = :error, reasonCode = :reasonCode, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun markFailed(bundleId: String, error: String, reasonCode: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun incrementAttempts(bundleId: String, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY createdAt DESC")
    suspend fun getFailedOutbox(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY createdAt DESC")
    fun observeFailedOutbox(): Flow<List<OutboxEntity>>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("UPDATE outbox SET status = 'PENDING', attempts = 0, lastError = NULL, reasonCode = NULL, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun retryFailedBundle(bundleId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET status = 'PENDING', attempts = 0, lastError = NULL, reasonCode = NULL, shiftId = COALESCE(shiftId, :fallbackShiftId), updatedAt = :updatedAt WHERE status = 'FAILED'")
    suspend fun retryAllFailed(fallbackShiftId: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun retryAllFailedWithDeviceId(
        currentDeviceId: String,
        fallbackShiftId: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val failedList = getFailedOutbox()
        for (item in failedList) {
            // Hanya buat bundle_id baru jika deviceId benar-benar berganti ke perangkat fisik lain.
            // Pada perangkat yang sama, pertahankan bundle_id asli agar idempotensi retry server (DUPLICATE) bekerja.
            if (item.deviceId != currentDeviceId) {
                val freshId = UUID.randomUUID().toString()
                updateBundleId(item.bundleId, freshId, updatedAt)
            }
        }
        resetFailedStatusWithDeviceId(currentDeviceId, fallbackShiftId, updatedAt)
    }

    @Query("UPDATE outbox SET status = 'PENDING', deviceId = :currentDeviceId, shiftId = COALESCE(shiftId, :fallbackShiftId), attempts = 0, lastError = NULL, reasonCode = NULL, updatedAt = :updatedAt WHERE status = 'FAILED'")
    suspend fun resetFailedStatusWithDeviceId(currentDeviceId: String, fallbackShiftId: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET bundleId = :newBundleId, updatedAt = :updatedAt WHERE bundleId = :oldBundleId")
    suspend fun updateBundleId(oldBundleId: String, newBundleId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET eventsJson = :eventsJson, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun updateEventsJson(bundleId: String, eventsJson: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET shiftId = :shiftId, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun updateShiftId(bundleId: String, shiftId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET shiftId = :shiftId, updatedAt = :updatedAt WHERE shiftId IS NULL AND status != 'SENT'")
    suspend fun updateMissingShiftId(shiftId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET deviceId = :newDeviceId, updatedAt = :updatedAt WHERE bundleId = :bundleId")
    suspend fun updateDeviceId(bundleId: String, newDeviceId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE outbox SET deviceId = :newDeviceId, updatedAt = :updatedAt WHERE status != 'SENT'")
    suspend fun updateAllUnsyncedDeviceId(newDeviceId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT lastError FROM outbox WHERE status = 'FAILED' ORDER BY updatedAt DESC LIMIT 1")
    fun observeLastFailedError(): Flow<String?>

    @Query("SELECT lastError FROM outbox WHERE status = 'FAILED' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastFailedError(): String?

    @Query("DELETE FROM outbox WHERE status = 'SENT' AND createdAt < :olderThan")
    suspend fun deleteSentOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status != 'SENT'")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status != 'SENT'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("DELETE FROM outbox WHERE bundleId = :bundleId")
    suspend fun deleteBundle(bundleId: String)

    @Query("DELETE FROM outbox WHERE status = 'FAILED' AND (lastError LIKE '%tidak berizin%' OR lastError LIKE '%shift%')")
    suspend fun deleteFailedUnauthorizedShiftBundles()
}
