package id.a3.feature.admin.update

import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian unit kepatuhan aturan OPS-03 untuk pembaruan aplikasi (F39):
 * "Update tidak berjalan saat shift aktif atau outbox tidak kosong"
 */
class UpdatePreconditionValidatorTest {

    private class FakeShiftDao(var activeShift: ShiftEntity? = null) : ShiftDao {
        override suspend fun insertShift(shift: ShiftEntity) { activeShift = shift }
        override suspend fun updateShift(shift: ShiftEntity) { activeShift = shift }
        override suspend fun getActiveShift(): ShiftEntity? = activeShift
        override fun observeActiveShift(): Flow<ShiftEntity?> = emptyFlow()
        override suspend fun getShiftById(shiftId: String): ShiftEntity? = activeShift
        override suspend fun getLatestShift(): ShiftEntity? = activeShift
        override fun observeAllShifts(): Flow<List<ShiftEntity>> = emptyFlow()
    }

    private class FakeOutboxDao(var unsyncedCount: Int = 0) : OutboxDao {
        override suspend fun insert(outbox: OutboxEntity) {}
        override suspend fun getOutboxByStatus(status: OutboxStatus, limit: Int): List<OutboxEntity> = emptyList()
        override suspend fun getByBundleId(bundleId: String): OutboxEntity? = null
        override suspend fun markSent(bundleId: String, flagsJson: String?, updatedAt: Long) {}
        override suspend fun markFailed(bundleId: String, error: String, reasonCode: String?, updatedAt: Long) {}
        override suspend fun incrementAttempts(bundleId: String, error: String?, updatedAt: Long) {}
        override suspend fun getFailedOutbox(): List<OutboxEntity> = emptyList()
        override fun observeFailedOutbox(): Flow<List<OutboxEntity>> = emptyFlow()
        override fun observeFailedCount(): Flow<Int> = emptyFlow()
        override suspend fun retryFailedBundle(bundleId: String, updatedAt: Long) {}
        override suspend fun retryAllFailed(updatedAt: Long) {}
        override suspend fun deleteSentOlderThan(olderThan: Long): Int = 0
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun getPendingCount(): Int = unsyncedCount
        override suspend fun getFailedCount(): Int = 0
        override suspend fun getUnsyncedCount(): Int = unsyncedCount
        override fun observeUnsyncedCount(): Flow<Int> = emptyFlow()
    }

    private val sampleShift = ShiftEntity(
        id = "shift-001",
        businessUnitId = "bu-cafe-01",
        deviceId = "dev-01",
        cashierId = "cashier-1",
        cashierName = "Budi Santoso",
        status = "open",
        openingCash = 200000L,
        openingCashBreakdownJson = "{}",
        openedAt = System.currentTimeMillis()
    )

    @Test
    fun `update diizinkan bila tidak ada shift aktif dan outbox kosong`() = runBlocking {
        val shiftDao = FakeShiftDao(activeShift = null)
        val outboxDao = FakeOutboxDao(unsyncedCount = 0)
        val validator = UpdatePreconditionValidator(shiftDao, outboxDao)

        val result = validator.checkUpdateEligibility()
        assertEquals(UpdateEligibility.Allowed, result)
    }

    @Test
    fun `update diblokir bila ada shift aktif meskipun outbox kosong`() = runBlocking {
        val shiftDao = FakeShiftDao(activeShift = sampleShift)
        val outboxDao = FakeOutboxDao(unsyncedCount = 0)
        val validator = UpdatePreconditionValidator(shiftDao, outboxDao)

        val result = validator.checkUpdateEligibility()
        assertTrue(result is UpdateEligibility.Blocked)
        val blocked = result as UpdateEligibility.Blocked
        assertEquals(1, blocked.reasons.size)
        assertTrue(blocked.reasons[0] is UpdateBlockReason.ActiveShiftRunning)
        val reason = blocked.reasons[0] as UpdateBlockReason.ActiveShiftRunning
        assertEquals("Budi Santoso", reason.cashierName)
        assertTrue(blocked.userFriendlyMessage.contains("Budi Santoso"))
    }

    @Test
    fun `update diblokir bila outbox tidak kosong meskipun shift tidak aktif`() = runBlocking {
        val shiftDao = FakeShiftDao(activeShift = null)
        val outboxDao = FakeOutboxDao(unsyncedCount = 3)
        val validator = UpdatePreconditionValidator(shiftDao, outboxDao)

        val result = validator.checkUpdateEligibility()
        assertTrue(result is UpdateEligibility.Blocked)
        val blocked = result as UpdateEligibility.Blocked
        assertEquals(1, blocked.reasons.size)
        assertTrue(blocked.reasons[0] is UpdateBlockReason.UnsyncedOutboxPending)
        val reason = blocked.reasons[0] as UpdateBlockReason.UnsyncedOutboxPending
        assertEquals(3, reason.pendingCount)
        assertTrue(blocked.userFriendlyMessage.contains("3 transaksi/event"))
    }

    @Test
    fun `update diblokir ganda bila shift aktif dan outbox tidak kosong`() = runBlocking {
        val shiftDao = FakeShiftDao(activeShift = sampleShift)
        val outboxDao = FakeOutboxDao(unsyncedCount = 5)
        val validator = UpdatePreconditionValidator(shiftDao, outboxDao)

        val result = validator.checkUpdateEligibility()
        assertTrue(result is UpdateEligibility.Blocked)
        val blocked = result as UpdateEligibility.Blocked
        assertEquals(2, blocked.reasons.size)
        assertTrue(blocked.reasons.any { it is UpdateBlockReason.ActiveShiftRunning })
        assertTrue(blocked.reasons.any { it is UpdateBlockReason.UnsyncedOutboxPending })
    }
}
