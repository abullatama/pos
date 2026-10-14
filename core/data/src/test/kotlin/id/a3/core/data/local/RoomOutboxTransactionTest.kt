package id.a3.core.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.repository.OrderTransactionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomOutboxTransactionTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionManager: OrderTransactionManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)
        transactionManager = OrderTransactionManager(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `createOrderWithOutbox saves order, lines, and outbox atomically`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val bundleId = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0001",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            subtotal = 62000L,
            discount = 6200L,
            serviceCharge = 0L,
            tax = 5580L,
            rounding = 20L,
            total = 61400L
        )

        val lines = listOf(
            OrderLineEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                itemRefId = "menu-1",
                nameSnapshot = "Espresso",
                quantity = 1,
                unitPriceSnapshot = 34000L
            ),
            OrderLineEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                itemRefId = "menu-2",
                nameSnapshot = "Latte",
                quantity = 1,
                unitPriceSnapshot = 28000L
            )
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-1","type":"order.created"}]"""
        )

        transactionManager.createOrderWithOutbox(order, lines, outbox)

        // Verify order saved
        val savedOrder = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(savedOrder)
        assertEquals("C1-0001", savedOrder!!.order.displayNumber)
        assertEquals(2, savedOrder.lines.size)

        // Verify outbox saved with PENDING status
        val pendingOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.PENDING)
        assertEquals(1, pendingOutbox.size)
        assertEquals(bundleId, pendingOutbox.first().bundleId)

        // Verify status transition to SENT
        database.outboxDao().markSent(bundleId)
        val sentOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(sentOutbox)
        assertEquals(OutboxStatus.SENT, sentOutbox!!.status)
    }

    @Test
    fun `failed transaction rolls back both order and outbox`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val bundleId = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0002",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "take_away",
            subtotal = 20000L,
            total = 20000L
        )

        // First save successfully
        val outbox1 = OutboxEntity(bundleId = bundleId, deviceId = "dev-1", shiftId = null, eventsJson = "[]")
        transactionManager.createOrderWithOutbox(order, emptyList(), outbox1)

        // Attempt to save identical orderId again should fail (PK conflict)
        var failed = false
        try {
            val duplicateOrder = order.copy(displayNumber = "C1-9999")
            val outbox2 = OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = "dev-1", shiftId = null, eventsJson = "[]")
            transactionManager.createOrderWithOutbox(duplicateOrder, emptyList(), outbox2)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue("Expected conflict exception on duplicate order ID", failed)

        // Verify outbox2 was NOT committed (atomic rollback)
        val allOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.PENDING)
        assertEquals(1, allOutbox.size)
        assertEquals(bundleId, allOutbox.first().bundleId)
    }
}
