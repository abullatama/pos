package id.a3.core.sync

import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.sync.model.SyncEventDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

data class ProblemBundle(
    val bundleId: String,
    val deviceId: String,
    val shiftId: String?,
    val eventTypes: List<String>,
    val reasonCode: String?,
    val errorMessage: String?,
    val attempts: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Pengelola Antrean Bermasalah (F29):
 * Memungkinkan pemantauan dan tindakan coba lagi (retry) untuk event yang REJECTED
 * tanpa ada event yang dihapus diam-diam.
 */
class SyncProblemQueueManager(
    private val outboxDao: OutboxDao,
    private val syncSender: SyncSender? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    fun observeFailedCount(): Flow<Int> = outboxDao.observeFailedCount()

    fun observePendingCount(): Flow<Int> = outboxDao.observePendingCount()

    fun observeProblemBundles(): Flow<List<ProblemBundle>> {
        return outboxDao.observeFailedOutbox().map { list ->
            list.map { it.toProblemBundle() }
        }
    }

    suspend fun getProblemBundles(): List<ProblemBundle> {
        return outboxDao.getFailedOutbox().map { it.toProblemBundle() }
    }

    suspend fun retryBundle(bundleId: String, currentDeviceId: String? = null) {
        outboxDao.retryFailedBundle(bundleId)
        if (!currentDeviceId.isNullOrBlank()) {
            outboxDao.updateDeviceId(bundleId, currentDeviceId)
        }
    }

    suspend fun retryAllProblems(currentDeviceId: String? = null, fallbackShiftId: String? = null) {
        if (!currentDeviceId.isNullOrBlank()) {
            outboxDao.retryAllFailedWithDeviceId(currentDeviceId, fallbackShiftId)
        } else {
            outboxDao.retryAllFailed(fallbackShiftId)
        }
    }

    suspend fun retryAndSync(bundleId: String? = null, currentDeviceId: String? = null, fallbackShiftId: String? = null): SyncSendSummary? {
        if (bundleId != null) {
            outboxDao.retryFailedBundle(bundleId)
            if (!currentDeviceId.isNullOrBlank()) {
                outboxDao.updateDeviceId(bundleId, currentDeviceId)
            }
        } else {
            retryAllProblems(currentDeviceId, fallbackShiftId)
        }
        return syncSender?.sendPendingBundles()
    }

    private fun OutboxEntity.toProblemBundle(): ProblemBundle {
        val types = try {
            val events = json.decodeFromString<List<SyncEventDto>>(eventsJson)
            events.map { it.type }
        } catch (_: Exception) {
            emptyList()
        }

        return ProblemBundle(
            bundleId = bundleId,
            deviceId = deviceId,
            shiftId = shiftId,
            eventTypes = types,
            reasonCode = reasonCode,
            errorMessage = lastError,
            attempts = attempts,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
