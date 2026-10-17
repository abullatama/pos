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
import org.junit.Assert.assertFalse
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

    @Test
    fun `recordSoldOutChanged updates room and creates valid menu sold_out_changed outbox event`() = runBlocking {
        // Seed initial menu item
        val item = id.a3.core.data.local.entity.MenuItemEntity(
            id = "menu-croissant",
            name = "Croissant",
            category = "Roti",
            price = 28000L,
            soldOut = false
        )
        database.masterDataDao().upsertMenuItem(item)

        // Mark as sold out
        val outbox = transactionManager.recordSoldOutChanged(
            menuItemId = "menu-croissant",
            soldOut = true,
            deviceId = "dev-cafe-1"
        )

        // Verify Room updated
        val updatedItem = database.masterDataDao().getMenuItem("menu-croissant")
        assertNotNull(updatedItem)
        assertTrue(updatedItem!!.soldOut)

        // Verify Outbox created with correct event payload
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"menu.sold_out_changed\""))
        assertTrue(savedOutbox.eventsJson.contains("\"menu_item_id\":\"menu-croissant\""))
        assertTrue(savedOutbox.eventsJson.contains("\"sold_out\":true"))
    }

    @Test
    fun `recordTableMoved updates room order table and creates valid order table_moved outbox event`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0007",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "5",
            subtotal = 62000L,
            total = 61400L
        )
        database.orderDao().insertOrder(order)

        // Pindahkan meja dari 5 ke 7
        val outbox = transactionManager.recordTableMoved(
            orderId = orderId,
            fromTable = "5",
            toTable = "7",
            deviceId = "dev-cafe-1"
        )

        // Verify Room order table updated
        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder)
        assertEquals("7", updatedOrder!!.tableLabel)

        // Verify Outbox created with correct event payload
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"order.table_moved\""))
        assertTrue(savedOutbox.eventsJson.contains("\"from_table\":\"5\""))
        assertTrue(savedOutbox.eventsJson.contains("\"to_table\":\"7\""))
    }

    @Test
    fun `createOpenOrderWithKitchenTicket saves order, lines, and creates order created and order sent_to_kitchen events`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val line1Id = UUID.randomUUID().toString()
        val line2Id = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0010",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "Meja 03",
            status = "open",
            subtotal = 62000L,
            total = 61400L
        )

        val lines = listOf(
            OrderLineEntity(
                id = line1Id,
                orderId = orderId,
                itemRefId = "menu-latte",
                nameSnapshot = "Caffe Latte",
                quantity = 1,
                unitPriceSnapshot = 34000L
            ),
            OrderLineEntity(
                id = line2Id,
                orderId = orderId,
                itemRefId = "menu-croissant",
                nameSnapshot = "Croissant",
                quantity = 1,
                unitPriceSnapshot = 28000L
            )
        )

        val outbox = transactionManager.createOpenOrderWithKitchenTicket(order, lines)

        // Verify Room saved
        val saved = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(saved)
        assertEquals("open", saved!!.order.status)
        assertEquals("Meja 03", saved.order.tableLabel)
        assertEquals(2, saved.lines.size)

        // Verify Outbox events
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.sent_to_kitchen\""))
        assertTrue(savedOutbox.eventsJson.contains(line1Id))
        assertTrue(savedOutbox.eventsJson.contains(line2Id))
    }

    @Test
    fun `addItemsToOpenOrder inserts new lines, updates totals, and creates order items_added and order sent_to_kitchen events`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val initialLineId = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0011",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "Meja 04",
            status = "open",
            subtotal = 34000L,
            total = 34000L
        )
        val initialLine = OrderLineEntity(
            id = initialLineId,
            orderId = orderId,
            itemRefId = "menu-latte",
            nameSnapshot = "Caffe Latte",
            quantity = 1,
            unitPriceSnapshot = 34000L
        )
        transactionManager.createOpenOrderWithKitchenTicket(order, listOf(initialLine))

        // Tambahkan item baru: Americano
        val newLineId = UUID.randomUUID().toString()
        val newLine = OrderLineEntity(
            id = newLineId,
            orderId = orderId,
            itemRefId = "menu-americano",
            nameSnapshot = "Americano",
            quantity = 1,
            unitPriceSnapshot = 26000L
        )

        val outbox = transactionManager.addItemsToOpenOrder(
            orderId = orderId,
            newLines = listOf(newLine),
            newSubtotal = 60000L,
            newDiscount = 0L,
            newServiceCharge = 0L,
            newTax = 0L,
            newRounding = 0L,
            newTotal = 60000L
        )

        // Verify Room updated
        val updated = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(updated)
        assertEquals(2, updated!!.lines.size)
        assertEquals(60000L, updated.order.total)

        // Verify Outbox events
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"order.items_added\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.sent_to_kitchen\""))
        assertTrue("Sent to kitchen hanya berisi baris baru", savedOutbox.eventsJson.contains(newLineId))
        assertFalse("Sent to kitchen tidak boleh memuat baris lama", savedOutbox.eventsJson.contains("\"line_ids\":[\"$initialLineId\"]"))
    }

    @Test
    fun `recordPayment updates order to paid and writes payment recorded event with cash and change`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0007",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "5",
            status = "open",
            subtotal = 62000L,
            total = 61400L
        )
        database.orderDao().insertOrder(order)

        // Bayar tunai (Test Vector K2: total 61400, tendered 70000)
        val outbox = transactionManager.recordPayment(
            orderId = orderId,
            method = "cash",
            amount = 61400L,
            tendered = 70000L
        )

        // Verifikasi status order di Room berubah menjadi "paid"
        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder)
        assertEquals("paid", updatedOrder!!.status)
        assertEquals(61400L, updatedOrder.paidAmount)

        // Verifikasi event outbox payment.recorded
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"payment.recorded\""))
        assertTrue(savedOutbox.eventsJson.contains("\"method\":\"cash\""))
        assertTrue(savedOutbox.eventsJson.contains("\"amount\":61400"))
        assertTrue(savedOutbox.eventsJson.contains("\"tendered\":70000"))
    }

    @Test
    fun `createAndPayOrder creates paid order and writes order created and payment recorded events`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0008",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "take_away",
            subtotal = 26000L,
            total = 26000L
        )
        val line = OrderLineEntity(
            id = lineId,
            orderId = orderId,
            itemRefId = "menu-americano",
            nameSnapshot = "Americano",
            quantity = 1,
            unitPriceSnapshot = 26000L
        )

        // Bayar QRIS Statis
        val outbox = transactionManager.createAndPayOrder(
            order = order,
            lines = listOf(line),
            method = "qris_static",
            amount = 26000L,
            tendered = null
        )

        // Verifikasi Room
        val saved = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(saved)
        assertEquals("paid", saved!!.order.status)
        assertEquals(26000L, saved.order.paidAmount)
        assertEquals(1, saved.lines.size)

        // Verifikasi outbox memuat kedua event
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"payment.recorded\""))
        assertTrue(savedOutbox.eventsJson.contains("\"method\":\"qris_static\""))
        assertTrue(savedOutbox.eventsJson.contains("\"amount\":26000"))
    }
}
