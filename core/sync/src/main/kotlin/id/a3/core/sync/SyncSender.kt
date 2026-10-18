package id.a3.core.sync

import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.sync.api.SyncApiService
import id.a3.core.sync.model.SyncBundleDto
import id.a3.core.sync.model.SyncEventDto
import id.a3.core.sync.model.SyncRequest
import kotlinx.serialization.json.Json

/**
 * Pengirim bundel sinkronisasi idempoten:
 * 1. Mengambil outbox berstatus PENDING dari Room.
 * 2. Menyusun payload bundel dan mengirimkannya ke endpoint POST /v1/sync/bundles.
 * 3. Menangani hasil per-bundle:
 *    - ACCEPTED: ditandai SENT di outbox.
 *    - DUPLICATE: diperlakukan sebagai sukses (idempoten), ditandai SENT di outbox.
 *    - REJECTED:
 *      * jika retryable == true (DEPENDENCY_MISSING): dicoba lagi pada sync berikutnya dengan backoff.
 *      * jika retryable == false (VALIDATION_FAILED, dll): ditandai FAILED (masuk antrean bermasalah).
 */
class SyncSender(
    private val outboxDao: OutboxDao,
    private val apiService: SyncApiService,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val appId: String = "cafe",
    private val appVersion: String = "1.0.0"
) {

    suspend fun sendPendingBundles(limit: Int = 50): SyncSendSummary {
        val pendingItems = outboxDao.getOutboxByStatus(OutboxStatus.PENDING, limit)
        if (pendingItems.isEmpty()) {
            return SyncSendSummary(sent = 0, duplicates = 0, failed = 0)
        }

        val bundles = pendingItems.map { outbox ->
            val events = json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson)
            SyncBundleDto(
                bundleId = outbox.bundleId,
                deviceId = outbox.deviceId,
                shiftId = outbox.shiftId,
                events = events
            )
        }

        val request = SyncRequest(bundles = bundles)
        val response = try {
            apiService.sendBundles(request, appId, appVersion)
        } catch (e: Exception) {
            val errorMsg = "Network error: ${e.message}"
            pendingItems.forEach { outbox ->
                outboxDao.incrementAttempts(outbox.bundleId, errorMsg)
            }
            return SyncSendSummary(sent = 0, duplicates = 0, failed = pendingItems.size)
        }

        if (!response.isSuccessful || response.body() == null) {
            val errorMsg = "HTTP error ${response.code()}: ${response.message()}"
            pendingItems.forEach { outbox ->
                outboxDao.incrementAttempts(outbox.bundleId, errorMsg)
            }
            return SyncSendSummary(sent = 0, duplicates = 0, failed = pendingItems.size)
        }

        var sentCount = 0
        var duplicateCount = 0
        var failedCount = 0

        val results = response.body()!!.results.associateBy { it.bundleId }

        for (outbox in pendingItems) {
            val result = results[outbox.bundleId]
            if (result == null) {
                outboxDao.incrementAttempts(outbox.bundleId, "No result returned for bundle")
                failedCount++
                continue
            }

            when (result.status) {
                "ACCEPTED" -> {
                    val flagsJson = result.flags?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
                    outboxDao.markSent(outbox.bundleId, flagsJson)
                    sentCount++
                }
                "DUPLICATE" -> {
                    // Sesuai D-04 & Bagian 12: DUPLICATE dianggap sukses, tandai outbox SENT
                    outboxDao.markSent(outbox.bundleId)
                    duplicateCount++
                }
                "REJECTED" -> {
                    if (result.retryable == true) {
                        outboxDao.incrementAttempts(outbox.bundleId, result.message ?: result.reasonCode)
                    } else {
                        outboxDao.markFailed(
                            bundleId = outbox.bundleId,
                            error = result.message ?: (result.reasonCode ?: "REJECTED"),
                            reasonCode = result.reasonCode
                        )
                    }
                    failedCount++
                }
                else -> {
                    outboxDao.incrementAttempts(outbox.bundleId, "Unknown status: ${result.status}")
                    failedCount++
                }
            }
        }

        return SyncSendSummary(sent = sentCount, duplicates = duplicateCount, failed = failedCount)
    }
}

data class SyncSendSummary(
    val sent: Int,
    val duplicates: Int,
    val failed: Int
)
