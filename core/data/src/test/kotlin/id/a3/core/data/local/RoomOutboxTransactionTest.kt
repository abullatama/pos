package id.a3.core.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.ShiftEntity
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.data.util.PhoneNumberUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("shift-001", savedOutbox!!.shiftId)
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.sent_to_kitchen\""))
        assertTrue(savedOutbox.eventsJson.contains(line1Id))
        assertTrue(savedOutbox.eventsJson.contains(line2Id))
    }

    @Test
    fun `createOpenOrderWithKitchenTicket with discount includes discount_rule preset in payload`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0015",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "take_away",
            status = "open",
            subtotal = 50000L,
            discount = 5000L,
            total = 45000L
        )

        val outbox = transactionManager.createOpenOrderWithKitchenTicket(
            order = order,
            lines = emptyList(),
            discountPresetName = "Diskon 10%"
        )

        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"discount_rule\""))
        assertTrue(savedOutbox.eventsJson.contains("\"preset\":\"Diskon 10%\""))
        assertTrue(savedOutbox.eventsJson.contains("\"order_type\":\"takeaway\""))
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
    fun `recordPayment without explicit shiftId links to active shift and counts toward cashSales`() = runBlocking {
        val activeShiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = activeShiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}",
            openedAt = System.currentTimeMillis()
        )
        database.shiftDao().insertShift(shift)

        // Buat pesanan cafe (misal Rp 32.000) yang awalnya ber-shiftId lama / null
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0008",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-old",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "3",
            status = "open",
            subtotal = 32000L,
            total = 32000L
        )
        database.orderDao().insertOrder(order)

        // Pembayaran tunai Rp 32.000 dicatat saat shift aktif
        transactionManager.recordPayment(
            orderId = orderId,
            method = "cash",
            amount = 32000L,
            tendered = 50000L
        )

        // Verifikasi pembayaran dikaitkan ke shift yang aktif (bukan shift-old)
        val cashSales = database.paymentDao().getCashSalesForShift(activeShiftId)
        assertEquals(32000L, cashSales)
    }

    @Test
    fun `recordPayment on laundry order updates paidAmount and preserves physical status`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0012",
            businessUnitId = "bu-laundry-01",
            shiftId = "shift-001",
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open",
            subtotal = 53000L,
            total = 53000L,
            paidAmount = 0L
        )
        database.orderDao().insertOrder(order)

        // Bayar lunas di muka
        val outbox = transactionManager.recordPayment(
            orderId = orderId,
            method = "cash",
            amount = 53000L,
            tendered = 53000L,
            isDp = false
        )

        val updated = database.orderDao().getOrderById(orderId)
        assertNotNull(updated)
        // Status fisik cucian HARUS tetap "open" (Diterima), tidak boleh ditimpa menjadi "paid"
        assertEquals("open", updated!!.status)
        assertEquals(53000L, updated.paidAmount)
        assertEquals(53000L, updated.total)
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
        assertEquals("shift-001", savedOutbox!!.shiftId)
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"payment.recorded\""))
        assertTrue(savedOutbox.eventsJson.contains("\"method\":\"qris_static\""))
        assertTrue(savedOutbox.eventsJson.contains("\"amount\":26000"))
    }

    @Test
    fun `phoneNumberUtil normalizes and validates indonesian phone numbers correctly`() {
        // Normalisasi berbagai variasi awalan dan separator
        assertEquals("081234567890", PhoneNumberUtil.normalize("+62 812-3456-7890"))
        assertEquals("081234567890", PhoneNumberUtil.normalize("6281234567890"))
        assertEquals("081234567890", PhoneNumberUtil.normalize("0812 3456 7890"))
        assertEquals("081234567890", PhoneNumberUtil.normalize("0812-3456-7890"))

        // Validasi format
        assertTrue(PhoneNumberUtil.isValid("081234567890"))
        assertTrue(PhoneNumberUtil.isValid("+6281234567890"))
        assertFalse(PhoneNumberUtil.isValid("021123456")) // Telepon rumah/bukan 08
        assertFalse(PhoneNumberUtil.isValid("0812")) // Kurang dari 10 digit
        assertFalse(PhoneNumberUtil.isValid("08123456789012345")) // Lebih dari 14 digit

        // Format tampilan
        assertEquals("0812 3456 7890", PhoneNumberUtil.formatDisplay("081234567890"))
    }

    @Test
    fun `customerDao can insert, find by phone, and search customers`() = runBlocking {
        val customer1 = CustomerEntity(
            id = UUID.randomUUID().toString(),
            name = "Budi Santoso",
            phone = "081234567890"
        )
        val customer2 = CustomerEntity(
            id = UUID.randomUUID().toString(),
            name = "Sari Indah",
            phone = "081211112222"
        )
        database.customerDao().insertCustomer(customer1)
        database.customerDao().insertCustomer(customer2)

        // Find by phone
        val found = database.customerDao().findCustomerByPhone("081234567890")
        assertNotNull(found)
        assertEquals("Budi Santoso", found!!.name)

        // Search by name query "sari"
        val searchByName = database.customerDao().searchCustomers("sari").first()
        assertEquals(1, searchByName.size)
        assertEquals("Sari Indah", searchByName.first().name)

        // Search by phone query "0812"
        val searchByPhone = database.customerDao().searchCustomers("0812").first()
        assertEquals(2, searchByPhone.size)
    }

    @Test
    fun `createLaundryOrderWithCustomer for new customer saves customer and creates customer created in bundle`() = runBlocking {
        val customerId = UUID.randomUUID().toString()
        val customer = CustomerEntity(
            id = customerId,
            name = "Budi Santoso",
            phone = "081234567890"
        )

        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0001",
            businessUnitId = "bu-laundry-01",
            shiftId = null, // Event customer.created dan laundry.* tidak mensyaratkan shift
            deviceId = "dev-laundry-1",
            cashierId = "cashier-laundry-1",
            orderType = "laundry",
            customerId = null,
            subtotal = 53000L,
            total = 53000L
        )

        val line = OrderLineEntity(
            id = lineId,
            orderId = orderId,
            itemRefId = "svc-kiloan",
            nameSnapshot = "Cuci Komplit Reguler",
            quantity = 1,
            unitPriceSnapshot = 28000L
        )

        // Buat order dengan pelanggan baru (isNewCustomer = true)
        val outbox = transactionManager.createLaundryOrderWithCustomer(
            order = order,
            lines = listOf(line),
            customer = customer,
            isNewCustomer = true,
            deviceId = "dev-laundry-1",
            shiftId = null
        )

        // 1. Verifikasi Customer tersimpan di Room
        val savedCustomer = database.customerDao().getCustomerById(customerId)
        assertNotNull(savedCustomer)
        assertEquals("Budi Santoso", savedCustomer!!.name)
        assertEquals("081234567890", savedCustomer.phone)

        // 2. Verifikasi Order tersimpan dengan customerId
        val savedOrder = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(savedOrder)
        assertEquals(customerId, savedOrder!!.order.customerId)
        assertEquals("L1-0001", savedOrder.order.displayNumber)

        // 3. Verifikasi Outbox memuat event customer.created dan order.created
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"customer.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"customer_id\":\"$customerId\""))
        assertTrue(savedOutbox.eventsJson.contains("\"name\":\"Budi Santoso\""))
        assertTrue(savedOutbox.eventsJson.contains("\"phone\":\"081234567890\""))

        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"order_id\":\"$orderId\""))

        // Best Practice Server: payload.customer dan payload.customer_id tidak boleh bersamaan
        val eventsArray = Json.parseToJsonElement(savedOutbox.eventsJson).jsonArray
        val orderCreatedPayload = eventsArray.first { it.jsonObject["type"]?.jsonPrimitive?.content == "order.created" }
            .jsonObject["payload"]!!.jsonObject
        assertTrue("Order payload harus memiliki objek customer untuk pelanggan baru", orderCreatedPayload.containsKey("customer"))
        assertFalse("Order payload tidak boleh memiliki customer_id jika customer inline ada", orderCreatedPayload.containsKey("customer_id"))
    }

    @Test
    fun `createLaundryOrderWithCustomer for existing customer does not duplicate customer created event`() = runBlocking {
        val existingCustomerId = UUID.randomUUID().toString()
        val customer = CustomerEntity(
            id = existingCustomerId,
            name = "Sari Indah",
            phone = "081211112222"
        )
        database.customerDao().insertCustomer(customer)

        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0002",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-laundry-1",
            orderType = "laundry",
            customerId = existingCustomerId,
            subtotal = 25000L,
            total = 25000L
        )

        // Buat order dengan pelanggan yang sudah ada (isNewCustomer = false)
        val outbox = transactionManager.createLaundryOrderWithCustomer(
            order = order,
            lines = emptyList(),
            customer = customer,
            isNewCustomer = false,
            deviceId = "dev-laundry-1",
            shiftId = null
        )

        // Verifikasi outbox HANYA berisi order.created tanpa customer.created
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertFalse("Tidak boleh ada customer.created untuk pelanggan lama", savedOutbox!!.eventsJson.contains("\"type\":\"customer.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"type\":\"order.created\""))
        assertTrue(savedOutbox.eventsJson.contains("\"customer_id\":\"$existingCustomerId\""))

        // Best Practice Server: Pelanggan lama menggunakan customer_id dan tidak membawa objek customer inline
        val eventsArray = Json.parseToJsonElement(savedOutbox.eventsJson).jsonArray
        val orderCreatedPayload = eventsArray.first { it.jsonObject["type"]?.jsonPrimitive?.content == "order.created" }
            .jsonObject["payload"]!!.jsonObject
        assertTrue("Order payload harus memiliki customer_id untuk pelanggan lama", orderCreatedPayload.containsKey("customer_id"))
        assertFalse("Order payload tidak boleh memiliki objek customer untuk pelanggan lama", orderCreatedPayload.containsKey("customer"))
    }

    @Test
    fun `advanceLaundryOrderStatus advances status one-way and creates laundry status_changed outbox event without shift`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0003",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open", // atau received
            subtotal = 53000L,
            total = 53000L
        )
        database.orderDao().insertOrder(order)

        // 1. Transisi dari open -> processing
        val outbox1 = transactionManager.advanceLaundryOrderStatus(orderId, "processing")
        val updatedOrder1 = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder1)
        assertEquals("processing", updatedOrder1!!.status)

        // Verifikasi event outbox laundry.status_changed
        val savedOutbox1 = database.outboxDao().getByBundleId(outbox1.bundleId)
        assertNotNull(savedOutbox1)
        assertTrue(savedOutbox1!!.shiftId == null) // Tidak mensyaratkan shift
        assertTrue(savedOutbox1.eventsJson.contains("\"type\":\"laundry.status_changed\""))
        assertTrue(savedOutbox1.eventsJson.contains("\"status\":\"processing\""))

        // 2. Transisi dari processing -> ready
        val outbox2 = transactionManager.advanceLaundryOrderStatus(orderId, "ready")
        val updatedOrder2 = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder2)
        assertEquals("ready", updatedOrder2!!.status)

        val savedOutbox2 = database.outboxDao().getByBundleId(outbox2.bundleId)
        assertNotNull(savedOutbox2)
        assertTrue(savedOutbox2!!.eventsJson.contains("\"status\":\"ready\""))

        // 3. Verifikasi query lookup berdasarkan nomor tampilan (L1-0003)
        val lookupByNumber = transactionManager.getOrderByDisplayNumber("L1-0003")
        assertNotNull(lookupByNumber)
        assertEquals(orderId, lookupByNumber!!.id)
    }

    @Test
    fun `advanceLaundryOrderStatus preserves order lines without cascade deleting them`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0099",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open",
            subtotal = 53000L,
            total = 53000L
        )
        val line = OrderLineEntity(
            id = lineId,
            orderId = orderId,
            itemRefId = "svc-kiloan",
            nameSnapshot = "Cuci Kiloan",
            quantity = 2,
            unitPriceSnapshot = 26500L
        )
        database.orderDao().insertOrder(order)
        database.orderDao().insertLines(listOf(line))

        // 1. Transisi ke processing
        transactionManager.advanceLaundryOrderStatus(orderId, "processing")
        val orderWithLines1 = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(orderWithLines1)
        assertEquals("processing", orderWithLines1!!.order.status)
        assertEquals(1, orderWithLines1.lines.size)
        assertEquals(lineId, orderWithLines1.lines.first().id)

        // 2. Transisi ke ready
        transactionManager.advanceLaundryOrderStatus(orderId, "ready")
        val orderWithLines2 = database.orderDao().getOrderWithLines(orderId)
        assertNotNull(orderWithLines2)
        assertEquals("ready", orderWithLines2!!.order.status)
        assertEquals(1, orderWithLines2.lines.size)
        assertEquals(lineId, orderWithLines2.lines.first().id)
    }

    @Test
    fun `repairMissingOrderLines restores lines from outbox order_created event`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0088",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "ready",
            subtotal = 35000L,
            total = 35000L,
            paidAmount = 35000L
        )
        val line = OrderLineEntity(
            id = lineId,
            orderId = orderId,
            itemRefId = "svc-kiloan",
            nameSnapshot = "Cuci Kiloan Reguler",
            quantity = 5,
            unitPriceSnapshot = 7000L
        )
        // Buat order dan outbox menggunakan createLaundryOrderWithCustomer
        transactionManager.createLaundryOrderWithCustomer(
            order = order,
            lines = listOf(line),
            customer = null,
            isNewCustomer = false
        )

        // Simulasikan baris pakaian terhapus secara anomali
        database.openHelper.writableDatabase.execSQL("DELETE FROM order_lines WHERE orderId = '$orderId'")
        assertEquals(0, database.orderDao().getLinesForOrder(orderId).size)

        // getOrderWithLines harus memulihkan baris pakaian dari outbox secara otomatis
        val recovered = transactionManager.getOrderWithLines(orderId)
        assertNotNull(recovered)
        assertEquals(1, recovered!!.lines.size)
        assertEquals(lineId, recovered.lines.first().id)
        assertEquals("Cuci Kiloan Reguler", recovered.lines.first().nameSnapshot)
        assertEquals(5, recovered.lines.first().quantity)
        assertEquals(7000L, recovered.lines.first().unitPriceSnapshot)
    }

    @Test
    fun `repairMissingOrderLines without outbox creates synthetic fallback line`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0089",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "ready",
            subtotal = 25000L,
            total = 25000L,
            paidAmount = 25000L
        )
        database.orderDao().insertOrder(order)
        assertEquals(0, database.orderDao().getLinesForOrder(orderId).size)

        // getOrderWithLines harus membuat fallback line sintetis
        val recovered = transactionManager.getOrderWithLines(orderId)
        assertNotNull(recovered)
        assertEquals(1, recovered!!.lines.size)
        val fallback = recovered.lines.first()
        assertEquals(orderId, fallback.orderId)
        assertTrue(fallback.nameSnapshot.contains("L1-0089"))
        assertEquals(25000L, fallback.unitPriceSnapshot)
    }

    @Test
    fun `advanceLaundryOrderStatus rejects illegal transition`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0004",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open",
            subtotal = 53000L,
            total = 53000L
        )
        database.orderDao().insertOrder(order)

        // Mencoba melompati langsung dari open ke ready (ilegal)
        var failed = false
        try {
            transactionManager.advanceLaundryOrderStatus(orderId, "ready")
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Harus menolak lompatan status ilegal", failed)
    }

    @Test
    fun `advanceLaundryOrderStatus with rework reverts ready to processing with approval and reason`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0005",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "ready", // Status siap
            subtotal = 25000L,
            total = 25000L
        )
        database.orderDao().insertOrder(order)

        // Cuci ulang (ready -> processing)
        val outbox = transactionManager.advanceLaundryOrderStatus(
            orderId = orderId,
            targetStatus = "processing",
            rework = true,
            reworkReason = "Noda minyak belum hilang",
            reworkApprovedBy = "u-manager-1"
        )

        val updated = database.orderDao().getOrderById(orderId)
        assertNotNull(updated)
        assertEquals("processing", updated!!.status)

        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"rework\":true"))
        assertTrue(savedOutbox.eventsJson.contains("\"reason\":\"Noda minyak belum hilang\""))
        assertTrue(savedOutbox.eventsJson.contains("\"approved_by\":\"u-manager-1\""))
    }

    @Test
    fun `recordLaundryPickups partial pickup keeps order status ready until all lines completed (F22)`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val line1Id = UUID.randomUUID().toString()
        val line2Id = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = "L1-0006",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "ready", // Siap diambil
            subtotal = 53000L,
            total = 53000L,
            paidAmount = 53000L // Lunas
        )
        val line1 = OrderLineEntity(id = line1Id, orderId = orderId, itemRefId = "svc-kiloan", nameSnapshot = "Kiloan", quantity = 1, unitPriceSnapshot = 28000L)
        val line2 = OrderLineEntity(id = line2Id, orderId = orderId, itemRefId = "svc-selimut", nameSnapshot = "Selimut", quantity = 1, unitPriceSnapshot = 25000L)

        database.orderDao().insertOrder(order)
        database.orderDao().insertLines(listOf(line1, line2))

        // 1. Ambil baris pertama saja (Kiloan) secara tuntas (isFinal = true)
        val pickupLine1 = LaundryPickupItem(orderLineId = line1Id, quantity = 1, isFinal = true)
        val outbox1 = transactionManager.recordLaundryPickups(orderId, listOf(pickupLine1))

        // Verifikasi Room: baris tersimpan di laundry_pickups
        val pickupsAfterFirst = database.laundryPickupDao().getPickupsByOrderId(orderId)
        assertEquals(1, pickupsAfterFirst.size)
        assertEquals(line1Id, pickupsAfterFirst.first().orderLineId)

        // Verifikasi Status Order: Masih 'ready' karena baris 2 belum diambil
        val orderAfterFirst = database.orderDao().getOrderById(orderId)
        assertNotNull(orderAfterFirst)
        assertEquals("ready", orderAfterFirst!!.status)

        // Verifikasi event outbox laundry.picked_up
        val savedOutbox1 = database.outboxDao().getByBundleId(outbox1.bundleId)
        assertNotNull(savedOutbox1)
        assertTrue(savedOutbox1!!.eventsJson.contains("\"type\":\"laundry.picked_up\""))
        assertTrue(savedOutbox1.eventsJson.contains("\"order_line_id\":\"$line1Id\""))

        // 2. Sekarang ambil baris kedua (Selimut) secara tuntas (isFinal = true)
        val pickupLine2 = LaundryPickupItem(orderLineId = line2Id, quantity = 1, isFinal = true)
        transactionManager.recordLaundryPickups(orderId, listOf(pickupLine2))

        // Verifikasi Status Order: Sekarang menjadi 'picked_up' (Diambil) karena SEMUA baris tuntas
        val orderAfterSecond = database.orderDao().getOrderById(orderId)
        assertNotNull(orderAfterSecond)
        assertEquals("picked_up", orderAfterSecond!!.status)
    }

    @Test
    fun `recordLaundryPickups for unpaid order requires manager approval and throws exception without it (F24)`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val unpaidOrder = OrderEntity(
            id = orderId,
            displayNumber = "L1-0007",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "ready",
            subtotal = 53000L,
            total = 53000L,
            paidAmount = 20000L // Belum lunas (baru bayar DP 20k, sisa 33k)
        )
        val line = OrderLineEntity(id = lineId, orderId = orderId, itemRefId = "svc-kiloan", nameSnapshot = "Kiloan", quantity = 1, unitPriceSnapshot = 53000L)
        database.orderDao().insertOrder(unpaidOrder)
        database.orderDao().insertLines(listOf(line))

        val pickupItem = LaundryPickupItem(orderLineId = lineId, quantity = 1, isFinal = true)

        // 1. Coba ambil tanpa persetujuan Manager -> harus DITOLAK
        var failedWithoutPin = false
        try {
            transactionManager.recordLaundryPickups(orderId, listOf(pickupItem), approvedBy = null)
        } catch (_: IllegalStateException) {
            failedWithoutPin = true
        }
        assertTrue("Pengambilan cucian belum lunas tanpa PIN Manager harus ditolak", failedWithoutPin)

        // 2. Ambil DENGAN otorisasi Manager -> DITERIMA & dicatat di outbox
        val outbox = transactionManager.recordLaundryPickups(
            orderId = orderId,
            items = listOf(pickupItem),
            approvedBy = "u-manager-1"
        )
        val savedOutbox = database.outboxDao().getByBundleId(outbox.bundleId)
        assertNotNull(savedOutbox)
        assertTrue(savedOutbox!!.eventsJson.contains("\"type\":\"laundry.picked_up\""))
        assertTrue(savedOutbox.eventsJson.contains("\"approved_by\":\"u-manager-1\""))

        // Status berubah menjadi picked_up karena baris tunggal ini selesai
        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertEquals("picked_up", updatedOrder!!.status)
    }

    @Test
    fun `OutboxDao supports flags on SENT, reasonCode on FAILED, and retry without data loss`() = runBlocking {
        val bundle1 = UUID.randomUUID().toString()
        val bundle2 = UUID.randomUUID().toString()

        database.outboxDao().insert(
            OutboxEntity(
                bundleId = bundle1,
                deviceId = "dev-1",
                shiftId = null,
                eventsJson = """[{"event_id":"e1","type":"order.created"}]""",
                status = OutboxStatus.PENDING
            )
        )
        database.outboxDao().insert(
            OutboxEntity(
                bundleId = bundle2,
                deviceId = "dev-1",
                shiftId = null,
                eventsJson = """[{"event_id":"e2","type":"payment.recorded"}]""",
                status = OutboxStatus.PENDING
            )
        )

        // 1. Mark bundle1 as SENT with flags
        val flags = """["TOTAL_MISMATCH","CLOCK_SKEW"]"""
        database.outboxDao().markSent(bundle1, flagsJson = flags)

        val saved1 = database.outboxDao().getByBundleId(bundle1)
        assertNotNull(saved1)
        assertEquals(OutboxStatus.SENT, saved1!!.status)
        assertEquals(flags, saved1.flagsJson)

        // 2. Mark bundle2 as FAILED with reasonCode (Antrean Bermasalah)
        database.outboxDao().markFailed(bundle2, error = "Item snapshot missing", reasonCode = "VALIDATION_FAILED")

        val saved2 = database.outboxDao().getByBundleId(bundle2)
        assertNotNull(saved2)
        assertEquals(OutboxStatus.FAILED, saved2!!.status)
        assertEquals("VALIDATION_FAILED", saved2.reasonCode)
        assertEquals("Item snapshot missing", saved2.lastError)

        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(1, failedList.size)
        assertEquals(bundle2, failedList[0].bundleId)

        // 3. Retry bundle2 -> resets status to PENDING
        database.outboxDao().retryFailedBundle(bundle2)
        val retried2 = database.outboxDao().getByBundleId(bundle2)
        assertNotNull(retried2)
        assertEquals(OutboxStatus.PENDING, retried2!!.status)
        assertEquals(0, retried2.attempts)
        org.junit.Assert.assertNull(retried2.lastError)
        org.junit.Assert.assertNull(retried2.reasonCode)

        // 4. Verify retention cleaner preserves PENDING and FAILED
        database.outboxDao().markFailed(bundle2, error = "Failed again", reasonCode = "FORBIDDEN")
        val deleted = database.outboxDao().deleteSentOlderThan(System.currentTimeMillis() + 10000)
        assertEquals(1, deleted) // only bundle1 (SENT) deleted

        org.junit.Assert.assertNull(database.outboxDao().getByBundleId(bundle1))
        assertNotNull(database.outboxDao().getByBundleId(bundle2)) // bundle2 PRESERVED

        // 5. Verify retryAllFailedWithDeviceId regenerates bundleId and aligns device/shift
        database.outboxDao().retryAllFailedWithDeviceId(currentDeviceId = "new-dev-555", fallbackShiftId = "shift-auto-999")
        org.junit.Assert.assertNull("Old bundle2 ID must be deleted and replaced", database.outboxDao().getByBundleId(bundle2))
        val pendingList = database.outboxDao().getOutboxByStatus(OutboxStatus.PENDING)
        assertEquals(1, pendingList.size)
        org.junit.Assert.assertNotEquals(bundle2, pendingList[0].bundleId)
        assertEquals("new-dev-555", pendingList[0].deviceId)
        assertEquals("shift-auto-999", pendingList[0].shiftId)
        assertEquals(OutboxStatus.PENDING, pendingList[0].status)
    }

    @Test
    fun `openShift saves shift entity and creates shift_opened outbox event atomically`(): Unit = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = """{"50000":2,"20000":4,"10000":2}""",
            differenceReason = null,
            openedAt = System.currentTimeMillis()
        )

        val breakdown = mapOf("50000" to 2, "20000" to 4, "10000" to 2)

        val outbox = transactionManager.openShift(
            shift = shift,
            breakdownMap = breakdown,
            differenceReason = null
        )

        // 1. Verifikasi entitas shift tersimpan di Room
        val activeShift = database.shiftDao().getActiveShift()
        assertNotNull(activeShift)
        assertEquals(shiftId, activeShift!!.id)
        assertEquals("open", activeShift.status)
        assertEquals(200000L, activeShift.openingCash)
        assertEquals("Budi Santoso", activeShift.cashierName)

        // 2. Verifikasi outbox terbuat dengan event shift.opened
        assertNotNull(outbox)
        assertEquals(shiftId, outbox.shiftId)
        assertEquals("dev-cafe-1", outbox.deviceId)
        assertTrue(outbox.eventsJson.contains("shift.opened"))
        assertTrue(outbox.eventsJson.contains(""""opening_cash":200000"""))
        assertTrue(outbox.eventsJson.contains(""""50000":2"""))
    }

    @Test
    fun `openShift throws IllegalStateException when another shift is already open`(): Unit = runBlocking {
        val shift1 = ShiftEntity(
            id = UUID.randomUUID().toString(),
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        transactionManager.openShift(shift1, emptyMap())

        // Coba buka shift kedua saat shift1 masih open -> harus lempar IllegalStateException (SHF-03)
        val shift2 = ShiftEntity(
            id = UUID.randomUUID().toString(),
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-2",
            cashierName = "Dewi Lestari",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        try {
            transactionManager.openShift(shift2, emptyMap())
            org.junit.Assert.fail("Harus melempar IllegalStateException saat shift aktif sudah ada")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SHF-03"))
        }
    }

    @Test
    fun `closeShift updates shift entity and writes shift_closed outbox event atomically`(): Unit = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        // Buat pesanan terlebih dahulu
        val order = OrderEntity(
            id = "order-test-1",
            displayNumber = "C1-0001",
            businessUnitId = "bu-cafe-01",
            shiftId = shiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            orderType = "dine_in",
            subtotal = 100000L,
            total = 100000L
        )
        database.orderDao().insertOrder(order)

        // Catat pembayaran QRIS dan Tunai untuk shift ini
        val payment1 = UUID.randomUUID().toString()
        val outboxPayment = transactionManager.recordPayment(
            orderId = "order-test-1",
            method = "qris_static",
            amount = 100000L,
            shiftId = shiftId
        )
        assertNotNull(outboxPayment)

        val paymentEntity = database.paymentDao().getPaymentsForShift(shiftId)
        assertEquals(1, paymentEntity.size)
        assertEquals("qris_static", paymentEntity[0].method)

        // Eksekusi closeShift (K1: Counted 725k, Laci 200k, Setor 525k)
        val outboxClose = transactionManager.closeShift(
            shiftId = shiftId,
            countedCash = 725000L,
            differenceReason = null,
            cashLeft = 200000L,
            cashDeposited = 525000L,
            cashTakenBy = "Pak Owner",
            qrisCheckedPaymentIds = listOf(payment1),
            handoverOrderIds = listOf("order-open-1")
        )

        // 1. Verifikasi status shift di Room berubah ke closed
        val closedShift = database.shiftDao().getShiftById(shiftId)
        assertNotNull(closedShift)
        assertEquals("closed", closedShift!!.status)
        assertEquals(725000L, closedShift.countedCash)
        assertEquals(200000L, closedShift.cashLeft)
        assertEquals(525000L, closedShift.cashDeposited)

        // 2. Verifikasi outbox shift.closed
        assertNotNull(outboxClose)
        assertEquals(shiftId, outboxClose.shiftId)
        assertTrue(outboxClose.eventsJson.contains("shift.closed"))
        assertTrue(outboxClose.eventsJson.contains(""""counted_cash":725000"""))
        assertTrue(outboxClose.eventsJson.contains(""""cash_left":200000"""))
        assertTrue(outboxClose.eventsJson.contains(""""cash_deposited":525000"""))
        assertTrue(outboxClose.eventsJson.contains(""""cash_taken_by":"Pak Owner""""))
        assertTrue(outboxClose.eventsJson.contains(payment1))
    }

    @Test
    fun `closeShift throws IllegalStateException when allocation does not equal counted cash without reason`(): Unit = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        // Kas dihitung 725k, tapi alokasi 200k + 500k = 700k (selisih 25k) tanpa alasan
        try {
            transactionManager.closeShift(
                shiftId = shiftId,
                countedCash = 725000L,
                differenceReason = null,
                cashLeft = 200000L,
                cashDeposited = 500000L
            )
            org.junit.Assert.fail("Harus melempar IllegalStateException bila alokasi tidak sama dengan kas dihitung (TUP-06)")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("TUP-06"))
        }
    }

    // =========================================================================
    // F35: Void, Refund Tunai, dan PIN Manager Lokal
    // =========================================================================

    @Test
    fun `recordCorrection for refund in current shift creates correction entity and outbox event`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0010",
            businessUnitId = "bu-cafe-01",
            shiftId = shiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            orderType = "dine_in",
            subtotal = 50000L,
            total = 50000L,
            paidAmount = 50000L,
            status = "paid"
        )
        database.orderDao().insertOrder(order)

        val outbox = transactionManager.recordCorrection(
            orderId = orderId,
            type = "refund",
            amount = 50000L,
            reasonCode = "quality_complaint",
            approvedBy = "u-manager-1",
            note = "Rasa kopi asam",
            currentShiftId = shiftId
        )

        // 1. Verifikasi status order berubah menjadi 'refunded'
        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder)
        assertEquals("refunded", updatedOrder!!.status)

        // 2. Verifikasi entitas CorrectionEntity tersimpan di Room
        val corrections = database.correctionDao().getCorrectionsForOrder(orderId)
        assertEquals(1, corrections.size)
        val correction = corrections.first()
        assertEquals(orderId, correction.orderId)
        assertEquals("refund", correction.type)
        assertEquals(50000L, correction.amount)
        assertEquals("cash", correction.refundMethod)
        assertEquals("quality_complaint", correction.reasonCode)
        assertEquals("Rasa kopi asam", correction.note)
        assertEquals("u-manager-1", correction.approvedBy)
        assertFalse(correction.isOldTransactionRefund)

        // 3. Verifikasi kas laci berkurang (getCashRefundTotalForShift)
        val refundTotal = database.correctionDao().getCashRefundTotalForShift(shiftId)
        assertEquals(50000L, refundTotal)

        // 4. Verifikasi event outbox 'correction.recorded' dengan skema Laravel 12
        assertNotNull(outbox)
        assertTrue(outbox.eventsJson.contains("correction.recorded"))
        assertTrue(outbox.eventsJson.contains("komplain_kualitas"))
        assertTrue(outbox.eventsJson.contains("reason_note"))
        assertTrue(outbox.eventsJson.contains("payment_id"))
        assertTrue(outbox.eventsJson.contains("order_line_id"))
        assertTrue(outbox.eventsJson.contains(""""approved_by":4"""))
        assertTrue(outbox.eventsJson.contains(""""amount":50000"""))
        assertTrue(outbox.eventsJson.contains(""""refund_method":"cash""""))
    }

    @Test
    fun `recordCorrection for void marks order void and does not affect cash refund`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        val orderId = UUID.randomUUID().toString()
        val order = OrderEntity(
            id = orderId,
            displayNumber = "C1-0011",
            businessUnitId = "bu-cafe-01",
            shiftId = shiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            orderType = "dine_in",
            subtotal = 30000L,
            total = 30000L,
            paidAmount = 0L,
            status = "open"
        )
        database.orderDao().insertOrder(order)

        transactionManager.recordCorrection(
            orderId = orderId,
            type = "void",
            amount = 30000L,
            reasonCode = "customer_cancelled",
            approvedBy = "u-manager-1"
        )

        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder)
        assertEquals("void", updatedOrder!!.status)

        // Void tidak mengurangi kas refund laci
        val refundTotal = database.correctionDao().getCashRefundTotalForShift(shiftId)
        assertEquals(0L, refundTotal)
    }

    // =========================================================================
    // F36: Refund Transaksi Shift Sebelumnya dengan Batas Nilai & Umur
    // =========================================================================

    @Test
    fun `recordCorrection for old transaction within limit succeeds and marks isOldTransaction true`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldShiftId = "shift-old-001"
        val activeShiftId = "shift-today-002"

        // Shift aktif hari ini dibuka 2 jam lalu
        val activeShift = ShiftEntity(
            id = activeShiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-today",
            cashierName = "Kasir Hari Ini",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}",
            openedAt = now - 2 * 3600 * 1000L
        )
        database.shiftDao().insertShift(activeShift)

        // Order dari 3 hari lalu (shift lama) senilai Rp 80.000 (<= Rp 100.000, <= 14 hari)
        val orderId = UUID.randomUUID().toString()
        val oldOrder = OrderEntity(
            id = orderId,
            displayNumber = "C1-0005",
            businessUnitId = "bu-cafe-01",
            shiftId = oldShiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-old",
            orderType = "dine_in",
            subtotal = 80000L,
            total = 80000L,
            paidAmount = 80000L,
            status = "paid",
            createdAt = now - 3 * 24 * 3600 * 1000L
        )
        database.orderDao().insertOrder(oldOrder)

        val outbox = transactionManager.recordCorrection(
            orderId = orderId,
            type = "refund",
            amount = 80000L,
            reasonCode = "quality_complaint",
            approvedBy = "u-manager-1",
            note = "Komplain susu basi",
            currentShiftId = activeShiftId,
            occurredAtMs = now
        )

        // 1. Verifikasi catatan tersimpan dengan tanda isOldTransactionRefund = true
        val corrections = database.correctionDao().getCorrectionsForOrder(orderId)
        assertEquals(1, corrections.size)
        val c = corrections.first()
        assertTrue(c.isOldTransactionRefund)
        assertEquals(activeShiftId, c.shiftId) // Dibebankan ke kas shift aktif hari ini (COR-05)

        // 2. Verifikasi memotong kas laci shift aktif hari ini
        val todayRefundTotal = database.correctionDao().getCashRefundTotalForShift(activeShiftId)
        assertEquals(80000L, todayRefundTotal)

        assertNotNull(outbox)
        assertEquals(activeShiftId, outbox.shiftId)
    }

    @Test
    fun `recordCorrection for old transaction exceeding Rp 100k is rejected with IllegalArgumentException`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldShiftId = "shift-old-001"
        val activeShiftId = "shift-today-002"

        val activeShift = ShiftEntity(
            id = activeShiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-today",
            cashierName = "Kasir Hari Ini",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}",
            openedAt = now - 2 * 3600 * 1000L
        )
        database.shiftDao().insertShift(activeShift)

        // Order dari shift lama senilai Rp 120.000 (> batas Rp 100.000 OQ-22 / F36)
        val orderId = UUID.randomUUID().toString()
        val oldOrder = OrderEntity(
            id = orderId,
            displayNumber = "C1-0006",
            businessUnitId = "bu-cafe-01",
            shiftId = oldShiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-old",
            orderType = "dine_in",
            subtotal = 120000L,
            total = 120000L,
            paidAmount = 120000L,
            status = "paid",
            createdAt = now - 2 * 24 * 3600 * 1000L
        )
        database.orderDao().insertOrder(oldOrder)

        try {
            transactionManager.recordCorrection(
                orderId = orderId,
                type = "refund",
                amount = 120000L,
                reasonCode = "quality_complaint",
                approvedBy = "u-manager-1",
                currentShiftId = activeShiftId,
                occurredAtMs = now
            )
            org.junit.Assert.fail("Harus ditolak bila refund transaksi shift lalu > Rp 100.000 (F36, COR-05)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("100.000"))
            assertTrue(e.message!!.contains("Web Dashboard"))
        }
    }

    @Test
    fun `recordCorrection for old transaction older than 14 days is rejected with IllegalArgumentException`() = runBlocking {
        val now = System.currentTimeMillis()
        val oldShiftId = "shift-old-001"
        val activeShiftId = "shift-today-002"

        val activeShift = ShiftEntity(
            id = activeShiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-today",
            cashierName = "Kasir Hari Ini",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}",
            openedAt = now - 2 * 3600 * 1000L
        )
        database.shiftDao().insertShift(activeShift)

        // Order dari 16 hari lalu (> batas 14 hari OQ-22 / F36)
        val orderId = UUID.randomUUID().toString()
        val oldOrder = OrderEntity(
            id = orderId,
            displayNumber = "C1-0007",
            businessUnitId = "bu-cafe-01",
            shiftId = oldShiftId,
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-old",
            orderType = "dine_in",
            subtotal = 40000L,
            total = 40000L,
            paidAmount = 40000L,
            status = "paid",
            createdAt = now - 16 * 24 * 3600 * 1000L
        )
        database.orderDao().insertOrder(oldOrder)

        try {
            transactionManager.recordCorrection(
                orderId = orderId,
                type = "refund",
                amount = 40000L,
                reasonCode = "wrong_input",
                approvedBy = "u-manager-1",
                currentShiftId = activeShiftId,
                occurredAtMs = now
            )
            org.junit.Assert.fail("Harus ditolak bila umur transaksi > 14 hari (F36, COR-05)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("14 hari"))
            assertTrue(e.message!!.contains("Web Dashboard"))
        }
    }

    // =========================================================================
    // F37: Kas Keluar dengan Foto Nota & Batas Tanpa PIN
    // =========================================================================

    @Test
    fun `recordCashOut within Rp 50k succeeds without PIN or photo and saves outbox`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        val outbox = transactionManager.recordCashOut(
            amount = 30000L,
            category = "Operasional",
            reason = "Beli es batu kristal",
            shiftId = shiftId
        )

        // 1. Verifikasi tersimpan di Room
        val list = database.cashOutDao().getCashOutsForShift(shiftId)
        assertEquals(1, list.size)
        val item = list.first()
        assertEquals(30000L, item.amount)
        assertEquals("Operasional", item.category)
        assertEquals("Beli es batu kristal", item.reason)
        assertNull(item.approvedBy)
        assertNull(item.receiptFileId)

        // 2. Verifikasi total kas keluar shift
        val total = database.cashOutDao().getCashOutTotalForShift(shiftId)
        assertEquals(30000L, total)

        // 3. Verifikasi event outbox 'cashout.recorded'
        assertNotNull(outbox)
        assertTrue(outbox.eventsJson.contains("cashout.recorded"))
        assertTrue(outbox.eventsJson.contains(""""amount":30000"""))
        assertTrue(outbox.eventsJson.contains("Operasional"))
        assertTrue(outbox.eventsJson.contains("Beli es batu kristal"))
    }

    @Test
    fun `recordCashOut over Rp 50k without PIN throws IllegalArgumentException`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        try {
            transactionManager.recordCashOut(
                amount = 75000L,
                category = "Bahan baku",
                reason = "Beli kopi mentah",
                receiptFilePath = "/path/photo.jpg",
                approvedBy = null, // Tanpa PIN Manager
                shiftId = shiftId
            )
            org.junit.Assert.fail("Harus ditolak bila kas keluar > Rp 50.000 tanpa PIN Manager (KAS-02)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("50.000"))
            assertTrue(e.message!!.contains("Manager"))
        }
    }

    @Test
    fun `recordCashOut over Rp 50k without photo receipt throws IllegalArgumentException`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        try {
            transactionManager.recordCashOut(
                amount = 80000L,
                category = "Bahan baku",
                reason = "Beli gula dan susu",
                receiptFileId = null,
                receiptFilePath = null, // Tanpa foto nota
                approvedBy = "u-manager-1",
                shiftId = shiftId
            )
            org.junit.Assert.fail("Harus ditolak bila kas keluar > Rp 50.000 tanpa foto nota (KAS-02)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("foto nota"))
        }
    }

    @Test
    fun `recordCashOut over Rp 50k with PIN and photo succeeds`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        val fileId = UUID.randomUUID().toString()
        val outbox = transactionManager.recordCashOut(
            amount = 90000L,
            category = "Perawatan",
            reason = "Servis grinder kopi",
            receiptFileId = fileId,
            receiptFilePath = "/data/user/0/id.a3/files/nota_grinder.jpg",
            approvedBy = "u-manager-1",
            shiftId = shiftId
        )

        val list = database.cashOutDao().getCashOutsForShift(shiftId)
        assertEquals(1, list.size)
        val item = list.first()
        assertEquals(90000L, item.amount)
        assertEquals("u-manager-1", item.approvedBy)
        assertEquals(fileId, item.receiptFileId)

        assertNotNull(outbox)
        assertTrue(outbox.eventsJson.contains("cashout.recorded"))
        assertTrue(outbox.eventsJson.contains(""""amount":90000"""))
        assertTrue(outbox.eventsJson.contains(""""receipt_file_id":"$fileId""""))
        assertTrue(outbox.eventsJson.contains(""""approved_by":"u-manager-1""""))
    }

    // =========================================================================
    // F38: Tutup Shift Offline & Tutup Paksa oleh Manager (shift.force_closed)
    // =========================================================================

    @Test
    fun `forceCloseShift updates status to force_closed, records outbox, and frees tablet for new shift`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        // Verifikasi shift berstatus open
        assertNotNull(database.shiftDao().getActiveShift())

        val note = "Kasir sakit mendadak, shift ditutup paksa manager"
        val approvedBy = "u-manager-1"
        val outbox = transactionManager.forceCloseShift(
            shiftId = shiftId,
            note = note,
            approvedBy = approvedBy
        )

        // 1. Verifikasi entitas shift berstatus force_closed
        val updatedShift = database.shiftDao().getShiftById(shiftId)
        assertNotNull(updatedShift)
        assertEquals("force_closed", updatedShift!!.status)
        assertEquals(approvedBy, updatedShift.approvedBy)
        assertEquals(note, updatedShift.differenceReason)
        assertNotNull(updatedShift.closedAt)

        // 2. Verifikasi getActiveShift() kini bernilai null (tablet tidak lagi terkunci)
        assertNull(database.shiftDao().getActiveShift())

        // 3. Verifikasi event outbox 'shift.force_closed'
        assertNotNull(outbox)
        assertTrue(outbox.eventsJson.contains("shift.force_closed"))
        assertTrue(outbox.eventsJson.contains(shiftId))
        assertTrue(outbox.eventsJson.contains("Kasir sakit mendadak"))
        assertTrue(outbox.eventsJson.contains("u-manager-1"))

        // 4. Verifikasi kasir berikutnya langsung dapat membuka shift baru tanpa terhalang
        val nextShiftId = UUID.randomUUID().toString()
        val nextShift = ShiftEntity(
            id = nextShiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-2",
            cashierName = "Siti Kasir Baru",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        transactionManager.openShift(
            shift = nextShift,
            breakdownMap = mapOf("50000" to 4)
        )

        val currentActive = database.shiftDao().getActiveShift()
        assertNotNull(currentActive)
        assertEquals(nextShiftId, currentActive!!.id)
        assertEquals("u-cashier-2", currentActive.cashierId)
    }

    @Test
    fun `forceCloseShift without note or approvedBy throws IllegalArgumentException`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val shift = ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{}"
        )
        database.shiftDao().insertShift(shift)

        try {
            transactionManager.forceCloseShift(
                shiftId = shiftId,
                note = "   ",
                approvedBy = "u-manager-1"
            )
            org.junit.Assert.fail("Harus ditolak bila catatan alasan kosong (ShiftForceClosed)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Catatan"))
        }

        try {
            transactionManager.forceCloseShift(
                shiftId = shiftId,
                note = "Kasir sakit",
                approvedBy = "  "
            )
            org.junit.Assert.fail("Harus ditolak bila approved_by kosong (ShiftForceClosed)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Manager"))
        }
    }
}
