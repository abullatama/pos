package id.a3.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncProblemQueueManagerTest {

    private lateinit var database: AppDatabase
    private lateinit var manager: SyncProblemQueueManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)
        manager = SyncProblemQueueManager(outboxDao = database.outboxDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeFailedCount and getProblemBundles return failed items accurately`() = runBlocking {
        val bundleId1 = UUID.randomUUID().toString()
        val bundleId2 = UUID.randomUUID().toString()

        val item1 = OutboxEntity(
            bundleId = bundleId1,
            deviceId = "dev-cafe-1",
            shiftId = "shift-1",
            eventsJson = """[{"event_id":"e1","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.FAILED,
            reasonCode = "VALIDATION_FAILED",
            lastError = "Line item mismatch",
            attempts = 2
        )

        val item2 = OutboxEntity(
            bundleId = bundleId2,
            deviceId = "dev-cafe-1",
            shiftId = null,
            eventsJson = """[{"event_id":"e2","type":"laundry.picked_up","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )

        database.outboxDao().insert(item1)
        database.outboxDao().insert(item2)

        val failedCount = manager.observeFailedCount().first()
        val pendingCount = manager.observePendingCount().first()
        assertEquals(1, failedCount)
        assertEquals(1, pendingCount)

        val problems = manager.getProblemBundles()
        assertEquals(1, problems.size)
        val p = problems[0]
        assertEquals(bundleId1, p.bundleId)
        assertEquals("VALIDATION_FAILED", p.reasonCode)
        assertEquals("Line item mismatch", p.errorMessage)
        assertEquals(listOf("order.created"), p.eventTypes)
    }

    @Test
    fun `retryAllProblems resets all failed items back to PENDING`() = runBlocking {
        val bundleId1 = UUID.randomUUID().toString()
        val bundleId2 = UUID.randomUUID().toString()

        database.outboxDao().insert(
            OutboxEntity(
                bundleId = bundleId1,
                deviceId = "dev-1",
                shiftId = null,
                eventsJson = "[]",
                status = OutboxStatus.FAILED,
                reasonCode = "FORBIDDEN",
                lastError = "Forbidden"
            )
        )
        database.outboxDao().insert(
            OutboxEntity(
                bundleId = bundleId2,
                deviceId = "dev-1",
                shiftId = null,
                eventsJson = "[]",
                status = OutboxStatus.FAILED,
                reasonCode = "VALIDATION_FAILED",
                lastError = "Bad request"
            )
        )

        assertEquals(2, manager.observeFailedCount().first())

        manager.retryAllProblems()

        assertEquals(0, manager.observeFailedCount().first())
        assertEquals(2, manager.observePendingCount().first())

        val b1 = database.outboxDao().getByBundleId(bundleId1)
        assertNotNull(b1)
        assertEquals(OutboxStatus.PENDING, b1!!.status)
        assertEquals(0, b1.attempts)
        assertNull(b1.reasonCode)
        assertNull(b1.lastError)
    }
}
