package id.a3.feature.laundry.presentation

import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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

class FakePrinterService : PrinterService {
    val printedBytes = mutableListOf<ByteArray>()
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Connected("RPP02N", "00:11:22:33:44:55"))
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    override fun isConnected(): Boolean = true
    override suspend fun checkConnection(): Boolean = true
    override suspend fun openCashDrawer(): Result<Unit> = Result.success(Unit)
    override suspend fun print(bytes: ByteArray): Result<Unit> {
        printedBytes.add(bytes)
        return Result.success(Unit)
    }
}

class FakeLaundryScanRepository : LaundryOrderRepository {
    val orders = mutableMapOf<String, OrderWithLines>()
    val customers = mutableMapOf<String, CustomerEntity>()
    var lastAdvancedTargetStatus: String? = null

    override fun observeServices(): Flow<List<LaundryServiceEntity>> = flowOf(emptyList())
    override suspend fun seedInitialServicesIfEmpty() {}
    override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(emptyList())
    override fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>> = flowOf(emptyList())

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        return orders[orderId]
    }

    override suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? {
        return orders.values.find { it.order.displayNumber == displayNumber }?.order
    }

    override suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? {
        return orders.values.find { it.order.displayNumber == displayNumber }
    }

    override suspend fun findOrderByNumberFlexible(query: String): OrderWithLines? {
        val clean = query.trim()
        return orders.values.find { it.order.displayNumber == clean || it.order.displayNumber.endsWith(clean) }
    }

    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> {
        val existing = orders[orderId] ?: return Result.failure(Exception("Not found"))
        orders[orderId] = existing.copy(order = existing.order.copy(status = newStatus))
        return Result.success(Unit)
    }

    override suspend fun advanceOrderStatus(
        orderId: String,
        targetStatus: String,
        rework: Boolean,
        reworkReason: String?,
        reworkApprovedBy: String?,
        userId: String?
    ): Result<OutboxEntity> {
        val existing = orders[orderId] ?: return Result.failure(Exception("Not found"))
        orders[orderId] = existing.copy(order = existing.order.copy(status = targetStatus))
        lastAdvancedTargetStatus = targetStatus
        return Result.success(
            OutboxEntity(
                bundleId = UUID.randomUUID().toString(),
                deviceId = "dev-1",
                shiftId = null,
                eventsJson = "[]"
            )
        )
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
    ): Result<OutboxEntity> = Result.success(
        OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = shiftId, eventsJson = "[]")
    )
    override fun observeLaundryOrders(statusFilter: String?): Flow<List<OrderWithLines>> = flowOf(orders.values.toList())
    override fun observePickups(orderId: String): Flow<List<LaundryPickupEntity>> = flowOf(emptyList())
    override suspend fun recordPickups(
        orderId: String,
        items: List<LaundryPickupItem>,
        userId: String?,
        approvedBy: String?,
        deviceId: String
    ): Result<OutboxEntity> = Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    override suspend fun reworkOrder(
        orderId: String,
        reason: String,
        approvedBy: String,
        deviceId: String
    ): Result<OutboxEntity> = Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    override suspend fun recordCorrection(
        orderId: String,
        type: String,
        amount: Long,
        reasonCode: String,
        approvedBy: String,
        note: String?
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getNextDisplayNumber(prefix: String): String = "${prefix}0001"
    override fun observeTodayOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeReadyOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeUnpaidOrdersCount(): Flow<Int> = flowOf(0)
    override fun observeReadyOrders(limit: Int): Flow<List<OrderWithLines>> = flowOf(emptyList())

    override suspend fun getNotificationStatus(orderId: String): Result<id.a3.core.sync.model.OrderNotificationStatusResponse> {
        return Result.success(id.a3.core.sync.model.OrderNotificationStatusResponse(status = "none"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryScanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeLaundryScanRepository
    private lateinit var fakePrinter: FakePrinterService
    private lateinit var viewModel: LaundryScanViewModel

    private val sampleCustomer = CustomerEntity(
        id = "cust-1",
        name = "Budi Santoso",
        phone = "081234567890"
    )

    private val sampleOrder = OrderEntity(
        id = "ord-uuid-1",
        displayNumber = "L1-0012",
        businessUnitId = "bu-laundry-01",
        shiftId = null,
        deviceId = "dev-1",
        cashierId = "cashier-1",
        orderType = "laundry",
        customerId = "cust-1",
        status = "received",
        subtotal = 53000L,
        total = 53000L
    )

    private val sampleLine = OrderLineEntity(
        id = "line-1",
        orderId = "ord-uuid-1",
        itemRefId = "svc-kiloan",
        nameSnapshot = "Kiloan Reguler",
        quantity = 1,
        unitPriceSnapshot = 28000L,
        note = "Kancing longgar"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLaundryScanRepository()
        fakePrinter = FakePrinterService()

        fakeRepository.customers["cust-1"] = sampleCustomer
        fakeRepository.orders["ord-uuid-1"] = OrderWithLines(sampleOrder, listOf(sampleLine))

        viewModel = LaundryScanViewModel(fakeRepository, fakePrinter, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan QR with UUID retrieves order and customer details`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.onScanResult("ord-uuid-1")

        val state = viewModel.uiState.value
        assertNotNull(state.scannedOrder)
        assertEquals("L1-0012", state.scannedOrder?.order?.displayNumber)
        assertEquals("Budi Santoso", state.customer?.name)
        assertEquals("081234567890", state.customer?.phone)
        assertEquals("received", state.currentStatus)
        assertTrue(state.canAdvance)
        assertNull(state.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `scan or manual search with display number retrieves order and customer details`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.onSearchQueryChange("L1-0012")
        viewModel.searchByManualQuery()

        val state = viewModel.uiState.value
        assertNotNull(state.scannedOrder)
        assertEquals("L1-0012", state.scannedOrder?.order?.displayNumber)
        assertEquals("Budi Santoso", state.customer?.name)
        assertNull(state.errorMessage)

        collectJob.cancel()
    }

    @Test
    fun `search unknown code shows friendly error message`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.onScanResult("L1-9999")

        val state = viewModel.uiState.value
        assertNull(state.scannedOrder)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("tidak ditemukan"))

        collectJob.cancel()
    }

    @Test
    fun `advanceStatus transitions received to processing and processing to ready`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // 1. Scan order
        viewModel.onScanResult("ord-uuid-1")
        assertEquals("received", viewModel.uiState.value.currentStatus)

        // 2. Advance to processing
        viewModel.advanceStatus()
        assertEquals("processing", viewModel.uiState.value.currentStatus)
        assertEquals("processing", fakeRepository.lastAdvancedTargetStatus)
        assertNotNull(viewModel.uiState.value.statusMessage)
        assertTrue(viewModel.uiState.value.statusMessage!!.contains("DIPROSES"))

        // 3. Advance to ready
        viewModel.advanceStatus()
        assertEquals("ready", viewModel.uiState.value.currentStatus)
        assertEquals("ready", fakeRepository.lastAdvancedTargetStatus)
        assertTrue(viewModel.uiState.value.statusMessage!!.contains("SIAP DIAMBIL"))

        collectJob.cancel()
    }

    @Test
    fun `printLaundryTag formats and sends bytes to printer service`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.onScanResult("ord-uuid-1")
        viewModel.printLaundryTag()

        // Verifikasi cetakan dikirim ke printer
        assertEquals(1, fakePrinter.printedBytes.size)
        val printed = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(printed.contains("ATHIA LAUNDRY HOUSE"))
        assertTrue(printed.contains("L1-0012"))
        assertTrue(printed.contains("Budi Santoso"))
        assertTrue(printed.contains("Kiloan Reguler"))
        assertTrue(printed.contains("Kancing longgar"))

        collectJob.cancel()
    }

    @Test
    fun `clearScannedOrder resets state`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.onScanResult("ord-uuid-1")
        assertNotNull(viewModel.uiState.value.scannedOrder)

        viewModel.clearScannedOrder()
        assertNull(viewModel.uiState.value.scannedOrder)
        assertNull(viewModel.uiState.value.customer)
        assertEquals("", viewModel.uiState.value.searchQuery)

        collectJob.cancel()
    }
}
