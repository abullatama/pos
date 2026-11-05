package id.a3.feature.laundry.presentation

import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FakeLaundryOrdersRepository : LaundryOrderRepository {
    val orders = mutableMapOf<String, OrderWithLines>()
    val customers = mutableMapOf<String, CustomerEntity>()
    val pickups = mutableListOf<LaundryPickupEntity>()
    var lastRecordedPickups: List<LaundryPickupItem>? = null
    var lastRecordedApprovedBy: String? = null
    var lastReworkedOrderId: String? = null
    var lastReworkedReason: String? = null
    var lastReworkedApprovedBy: String? = null

    private val ordersFlowInternal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)

    override fun observeServices(): Flow<List<LaundryServiceEntity>> = flowOf(emptyList())
    override suspend fun seedInitialServicesIfEmpty() {}
    override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(orders.values.map { it.order })
    override fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>> =
        flowOf(orders.values.map { it.order }.filter { it.status == status })

    override fun observeLaundryOrders(statusFilter: String?): Flow<List<OrderWithLines>> {
        return ordersFlowInternal.onStart { emit(Unit) }.map {
            if (statusFilter == null) orders.values.toList() else orders.values.filter { it.order.status == statusFilter }
        }
    }

    fun notifyOrdersChanged() {
        ordersFlowInternal.tryEmit(Unit)
    }

    override fun observePickups(orderId: String): Flow<List<LaundryPickupEntity>> {
        return flowOf(pickups.filter { it.orderId == orderId })
    }

    override suspend fun recordPickups(
        orderId: String,
        items: List<LaundryPickupItem>,
        userId: String?,
        approvedBy: String?,
        deviceId: String
    ): Result<OutboxEntity> {
        lastRecordedPickups = items
        lastRecordedApprovedBy = approvedBy
        items.forEach { item ->
            pickups.add(
                LaundryPickupEntity(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    orderLineId = item.orderLineId,
                    quantity = item.quantity,
                    isFinal = item.isFinal,
                    approvedBy = approvedBy
                )
            )
        }
        val existing = orders[orderId]
        if (existing != null && items.all { it.isFinal }) {
            orders[orderId] = existing.copy(order = existing.order.copy(status = "picked_up"))
        }
        return Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    }

    override suspend fun reworkOrder(
        orderId: String,
        reason: String,
        approvedBy: String,
        deviceId: String
    ): Result<OutboxEntity> {
        lastReworkedOrderId = orderId
        lastReworkedReason = reason
        lastReworkedApprovedBy = approvedBy
        val existing = orders[orderId]
        if (existing != null) {
            orders[orderId] = existing.copy(order = existing.order.copy(status = "processing"))
        }
        return Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    }

    override suspend fun recordCorrection(
        orderId: String,
        type: String,
        amount: Long,
        reasonCode: String,
        approvedBy: String,
        note: String?
    ): Result<Unit> {
        val existing = orders[orderId]
        if (existing != null) {
            orders[orderId] = existing.copy(order = existing.order.copy(status = "refunded"))
        }
        return Result.success(Unit)
    }

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? = orders[orderId]
    override suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? = orders.values.find { it.order.displayNumber == displayNumber }?.order
    override suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? = orders.values.find { it.order.displayNumber == displayNumber }
    override suspend fun findOrderByNumberFlexible(query: String): OrderWithLines? {
        val clean = query.trim()
        return orders.values.find { it.order.displayNumber == clean || it.order.displayNumber.endsWith(clean) }
    }
    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> = Result.success(Unit)
    override suspend fun advanceOrderStatus(
        orderId: String,
        targetStatus: String,
        rework: Boolean,
        reworkReason: String?,
        reworkApprovedBy: String?,
        userId: String?
    ): Result<OutboxEntity> {
        val existing = orders[orderId]
        if (existing != null) {
            orders[orderId] = existing.copy(order = existing.order.copy(status = targetStatus))
        }
        return Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = "dev-1", shiftId = null, eventsJson = "[]"))
    }

    override suspend fun saveOrderWithOutbox(order: OrderEntity, lines: List<OrderLineEntity>, eventsJson: String): Result<Unit> = Result.success(Unit)
    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> = flowOf(emptyList())
    override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> = flowOf(emptyList())
    override suspend fun getCustomerById(id: String): CustomerEntity? = customers[id]
    override suspend fun saveCustomerLocally(customer: CustomerEntity): Result<Unit> = Result.success(Unit)
    override suspend fun createLaundryOrder(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        customer: CustomerEntity?,
        isNewCustomer: Boolean,
        deviceId: String,
        shiftId: String?
    ): Result<OutboxEntity> = Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = shiftId, eventsJson = "[]"))

    override suspend fun getNextDisplayNumber(prefix: String): String = "${prefix}0001"
    override fun observeTodayOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeReadyOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeUnpaidOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeReadyOrders(limit: Int): Flow<List<OrderWithLines>> = flowOf(emptyList())

    var notificationStatusToReturn = id.a3.core.sync.model.OrderNotificationStatusResponse(status = "sent")

    var notificationFailure: Throwable? = null

    override suspend fun getNotificationStatus(orderId: String): Result<id.a3.core.sync.model.OrderNotificationStatusResponse> {
        notificationFailure?.let { return Result.failure(it) }
        return Result.success(notificationStatusToReturn)
    }

    var repairLinesResult: List<OrderLineEntity> = emptyList()
    override suspend fun repairMissingOrderLines(orderId: String): List<OrderLineEntity> {
        val lines = repairLinesResult
        if (lines.isNotEmpty()) {
            val existing = orders[orderId]
            if (existing != null) {
                orders[orderId] = existing.copy(lines = lines)
                notifyOrdersChanged()
            }
        }
        return lines
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryOrdersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeLaundryOrdersRepository
    private lateinit var fakePrinter: FakePrinterService
    private lateinit var viewModel: LaundryOrdersViewModel

    private val customer1 = CustomerEntity(id = "cust-1", name = "Budi Santoso", phone = "081234567890")
    private val line1 = OrderLineEntity(id = "line-1", orderId = "ord-1", itemRefId = "svc-kiloan", nameSnapshot = "Kiloan Reguler", quantity = 1, unitPriceSnapshot = 53000L)

    private val paidOrder = OrderEntity(
        id = "ord-1",
        displayNumber = "L1-0001",
        businessUnitId = "bu-laundry-01",
        shiftId = null,
        deviceId = "dev-1",
        cashierId = "cashier-1",
        orderType = "laundry",
        customerId = "cust-1",
        status = "ready",
        subtotal = 53000L,
        total = 53000L,
        paidAmount = 53000L // LUNAS
    )

    private val unpaidOrder = OrderEntity(
        id = "ord-2",
        displayNumber = "L1-0002",
        businessUnitId = "bu-laundry-01",
        shiftId = null,
        deviceId = "dev-1",
        cashierId = "cashier-1",
        orderType = "laundry",
        customerId = "cust-1",
        status = "ready",
        subtotal = 40000L,
        total = 40000L,
        paidAmount = 15000L // BELUM LUNAS (DP 15k, sisa 25k)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLaundryOrdersRepository()
        fakePrinter = FakePrinterService()

        fakeRepository.customers["cust-1"] = customer1
        fakeRepository.orders["ord-1"] = OrderWithLines(paidOrder, listOf(line1))
        fakeRepository.orders["ord-2"] = OrderWithLines(unpaidOrder, listOf(line1.copy(id = "line-2", orderId = "ord-2")))

        viewModel = LaundryOrdersViewModel(fakeRepository, fakePrinter, testDispatcher, FakeManagerApprover())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state observes orders and selects first order`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(2, state.orders.size)
        assertNotNull(state.selectedOrder)
        assertEquals("ord-1", state.selectedOrder?.order?.id)
        assertEquals("Budi Santoso", state.customer?.name)
        assertTrue(state.isFullyPaid)
        assertEquals(0L, state.remainingBalance)

        collectJob.cancel()
    }

    @Test
    fun `setFilterStatus filters orders`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setFilterStatus(LaundryFilterStatus.READY)
        assertEquals(LaundryFilterStatus.READY, viewModel.uiState.value.filterStatus)
        assertEquals(2, viewModel.uiState.value.orders.size)

        viewModel.setFilterStatus(LaundryFilterStatus.PICKED_UP)
        assertEquals(0, viewModel.uiState.value.orders.size)

        collectJob.cancel()
    }

    @Test
    fun `submitPickup for fully paid order executes directly without manager pin dialog`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val pickupItem = LaundryPickupItem(orderLineId = "line-1", quantity = 1, isFinal = true)
        viewModel.submitPickup(listOf(pickupItem))

        // Langsung berhasil tanpa buka dialog PIN
        assertFalse(viewModel.uiState.value.isManagerPinDialogOpen)
        assertNull(fakeRepository.lastRecordedApprovedBy)
        assertEquals(1, fakeRepository.lastRecordedPickups?.size)
        assertNotNull(viewModel.uiState.value.successMessage)

        collectJob.cancel()
    }

    @Test
    fun `submitAllItemsFinalPickup on order with lines submits all lines with isFinal true`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.selectOrder(fakeRepository.orders["ord-1"]!!)
        viewModel.submitAllItemsFinalPickup()

        assertEquals(1, fakeRepository.lastRecordedPickups?.size)
        val firstPickup = fakeRepository.lastRecordedPickups?.first()
        assertEquals("line-1", firstPickup?.orderLineId)
        assertTrue(firstPickup?.isFinal == true)
        assertNotNull(viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `submitAllItemsFinalPickup on order without lines sets error message and aborts`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val emptyLinesOrder = OrderWithLines(paidOrder.copy(id = "ord-empty"), emptyList())
        fakeRepository.orders["ord-empty"] = emptyLinesOrder
        fakeRepository.notifyOrdersChanged()
        viewModel.selectOrder(emptyLinesOrder)

        viewModel.submitAllItemsFinalPickup()

        assertNull(fakeRepository.lastRecordedPickups)
        assertEquals("Daftar pakaian tidak ditemukan untuk pesanan ini", viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `submitAllItemsFinalPickup on order without lines triggers auto-repair and completes successfully`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val emptyLinesOrder = OrderWithLines(paidOrder.copy(id = "ord-repair"), emptyList())
        fakeRepository.orders["ord-repair"] = emptyLinesOrder
        fakeRepository.notifyOrdersChanged()
        viewModel.selectOrder(emptyLinesOrder)

        val repairedLine = OrderLineEntity(id = "line-rep", orderId = "ord-repair", itemRefId = "svc-kiloan", nameSnapshot = "Cuci Kiloan", quantity = 1, unitPriceSnapshot = 53000L)
        fakeRepository.repairLinesResult = listOf(repairedLine)

        viewModel.submitAllItemsFinalPickup()

        assertEquals(1, fakeRepository.lastRecordedPickups?.size)
        assertEquals("line-rep", fakeRepository.lastRecordedPickups?.first()?.orderLineId)
        assertNotNull(viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `submitPickup for unpaid order triggers manager PIN dialog (F24)`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Pilih order belum lunas (ord-2)
        viewModel.selectOrder(fakeRepository.orders["ord-2"]!!)
        assertFalse(viewModel.uiState.value.isFullyPaid)
        assertEquals(25000L, viewModel.uiState.value.remainingBalance)

        val pickupItem = LaundryPickupItem(orderLineId = "line-2", quantity = 1, isFinal = true)
        viewModel.submitPickup(listOf(pickupItem))

        // Dialog PIN Manager terbuka dan menahan aksi UnpaidPickup
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)
        assertTrue(viewModel.uiState.value.pendingAction is PendingManagerAction.UnpaidPickup)

        // 1. Masukkan PIN salah
        viewModel.verifyManagerPinAndExecute("0000")
        assertNotNull(viewModel.uiState.value.pinErrorMessage)
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)

        // 2. Masukkan PIN benar ("4444" bawaan seed)
        viewModel.verifyManagerPinAndExecute("4444")
        assertFalse(viewModel.uiState.value.isManagerPinDialogOpen)
        assertEquals("2", fakeRepository.lastRecordedApprovedBy)
        assertNotNull(viewModel.uiState.value.successMessage)

        collectJob.cancel()
    }

    @Test
    fun `requestReworkWithPin opens pin dialog and correct pin executes rework (F23)`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.openReworkDialog()
        assertTrue(viewModel.uiState.value.isReworkDialogOpen)

        viewModel.setReworkReason("Pakaian masih bau apek")
        viewModel.requestReworkWithPin("Pakaian masih bau apek")

        // Dialog Rework tertutup, Dialog PIN terbuka
        assertFalse(viewModel.uiState.value.isReworkDialogOpen)
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)

        // Otorisasi dengan PIN manager
        viewModel.verifyManagerPinAndExecute("4444")
        assertFalse(viewModel.uiState.value.isManagerPinDialogOpen)
        assertEquals("ord-1", fakeRepository.lastReworkedOrderId)
        assertEquals("Pakaian masih bau apek", fakeRepository.lastReworkedReason)
        assertEquals("2", fakeRepository.lastReworkedApprovedBy)
        assertNotNull(viewModel.uiState.value.successMessage)

        collectJob.cancel()
    }

    @Test
    fun `advanceStatusToProcessing and advanceStatusToReady transitions status and updates messages`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Buat order berstatus open (Diterima)
        val openOrder = OrderEntity(
            id = "ord-open",
            displayNumber = "L1-0003",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open",
            subtotal = 30000L,
            total = 30000L
        )
        fakeRepository.orders["ord-open"] = OrderWithLines(openOrder, emptyList())
        fakeRepository.notifyOrdersChanged()
        viewModel.selectOrder(fakeRepository.orders["ord-open"]!!)

        // 1. Advance to processing
        viewModel.advanceStatusToProcessing()
        fakeRepository.notifyOrdersChanged()
        assertNotNull(viewModel.uiState.value.successMessage)
        assertTrue(viewModel.uiState.value.successMessage!!.contains("Diproses"))

        // Simulasikan status sudah processing
        fakeRepository.orders["ord-open"] = OrderWithLines(openOrder.copy(status = "processing"), emptyList())
        fakeRepository.notifyOrdersChanged()
        viewModel.selectOrder(fakeRepository.orders["ord-open"]!!)

        // 2. Advance to ready
        viewModel.advanceStatusToReady()
        fakeRepository.notifyOrdersChanged()
        assertNotNull(viewModel.uiState.value.successMessage)
        assertTrue(viewModel.uiState.value.successMessage!!.contains("siap"))

        collectJob.cancel()
    }

    @Test
    fun `onSearchQueryChange filters and selects order with pure numeric input`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Ketik angka "0002" atau "2"
        viewModel.onSearchQueryChange("0002")
        assertEquals("0002", viewModel.uiState.value.searchQuery)
        assertEquals("ord-2", viewModel.uiState.value.selectedOrder?.order?.id)

        collectJob.cancel()
    }

    @Test
    fun `fetchNotificationStatus updates state with server response`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        fakeRepository.notificationStatusToReturn = id.a3.core.sync.model.OrderNotificationStatusResponse(
            status = "sent",
            sentAt = "2026-10-26T10:00:00Z"
        )

        viewModel.fetchNotificationStatus("ord-1")
        assertEquals("sent", viewModel.uiState.value.notificationStatus)
        assertFalse(viewModel.uiState.value.isCheckingNotification)

        // Ubah ke queued
        fakeRepository.notificationStatusToReturn = id.a3.core.sync.model.OrderNotificationStatusResponse(
            status = "queued"
        )
        viewModel.fetchNotificationStatus("ord-1")
        assertEquals("queued", viewModel.uiState.value.notificationStatus)

        // Ubah ke failed
        fakeRepository.notificationStatusToReturn = id.a3.core.sync.model.OrderNotificationStatusResponse(
            status = "failed",
            lastError = "Fonnte API timeout"
        )
        viewModel.fetchNotificationStatus("ord-1")
        assertEquals("failed", viewModel.uiState.value.notificationStatus)
        assertEquals("Fonnte API timeout", viewModel.uiState.value.notificationError)

        collectJob.cancel()
    }

    @Test
    fun `gagal bertanya ke server tidak ditampilkan sebagai belum ada pesan`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        fakeRepository.notificationFailure = Exception("HTTP 401: Unauthorized")
        viewModel.fetchNotificationStatus("ord-1")

        assertEquals(NOTIFICATION_STATUS_UNKNOWN, viewModel.uiState.value.notificationStatus)
        assertEquals("Gagal memeriksa status ke server (kode 401)", viewModel.uiState.value.notificationError)

        collectJob.cancel()
    }

    @Test
    fun `antre tanpa percobaan dijelaskan belum pernah dicoba`() {
        val r = id.a3.core.sync.model.OrderNotificationStatusResponse(
            notifications = listOf(id.a3.core.sync.model.OrderNotificationItemDto("siap", "queued", 0))
        )
        assertEquals("belum pernah dicoba kirim", notificationDetailText(r))
    }

    @Test
    fun `antre dengan percobaan menyebut jumlah dan waktu terakhir`() {
        val r = id.a3.core.sync.model.OrderNotificationStatusResponse(
            notifications = listOf(
                id.a3.core.sync.model.OrderNotificationItemDto("siap", "queued", 3, "2026-11-05T03:15:00Z")
            )
        )
        val text = notificationDetailText(r, java.time.ZoneId.of("Asia/Jakarta"))
        assertEquals("3 percobaan, terakhir 5 Nov 10.15", text)
    }

    @Test
    fun `terkirim tidak menampilkan keterangan percobaan`() {
        val r = id.a3.core.sync.model.OrderNotificationStatusResponse(
            notifications = listOf(id.a3.core.sync.model.OrderNotificationItemDto("siap", "sent", 1))
        )
        assertEquals(null, notificationDetailText(r))
    }

    @Test
    fun `server tanpa catatan pesan tetap none`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        fakeRepository.notificationStatusToReturn = id.a3.core.sync.model.OrderNotificationStatusResponse()
        viewModel.fetchNotificationStatus("ord-1")

        assertEquals("none", viewModel.uiState.value.notificationStatus)

        collectJob.cancel()
    }

    @Test
    fun `buildWhatsAppMessage produces valid formatted text without emoji`() {
        val order = OrderWithLines(
            order = paidOrder.copy(status = "ready"),
            lines = listOf(line1)
        )
        val msg = viewModel.buildWhatsAppMessage(order, customer1)

        assertTrue(msg.contains("Budi Santoso"))
        assertTrue(msg.contains("L1-0001"))
        assertTrue(msg.contains("SIAP DIAMBIL"))
        assertTrue(msg.contains("Lunas"))
        assertFalse(msg.contains("🚀"))
        assertFalse(msg.contains("✅"))
    }

    @Test
    fun `buildWhatsAppUrl normalizes Indonesian phone number and encodes url`() {
        val order = OrderWithLines(
            order = paidOrder,
            lines = listOf(line1)
        )
        val url = viewModel.buildWhatsAppUrl(order, customer1)

        assertNotNull(url)
        assertTrue(url!!.startsWith("https://api.whatsapp.com/send?phone=6281234567890"))
        assertTrue(url.contains("&text="))
    }
}
