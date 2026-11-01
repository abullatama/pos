package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outbox: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOutboxByStatus(status: OutboxStatus = OutboxStatus.PENDING, limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE bundleId = :bundleId")
    suspend fun getByBundleId(bundleId: String): OutboxEntity?

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

    @Query("UPDATE outbox SET status = 'PENDING', attempts = 0, lastError = NULL, reasonCode = NULL, updatedAt = :updatedAt WHERE status = 'FAILED'")
    suspend fun retryAllFailed(updatedAt: Long = System.currentTimeMillis())

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
}
