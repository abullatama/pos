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

class FakeLaundryOrderRepository : LaundryOrderRepository {
    val sampleServices = listOf(
        LaundryServiceEntity(
            id = "svc-kiloan-reguler",
            name = "Cuci Komplit Kiloan Reguler",
            unit = "kg",
            rate = 7000L,
            minWeight = 1.0,
            weightStep = 0.5,
            expressRate = 10000L,
            active = true
        ),
        LaundryServiceEntity(
            id = "svc-selimut",
            name = "Selimut Tebal",
            unit = "pcs",
            rate = 25000L,
            minWeight = 1.0,
            weightStep = 1.0,
            expressRate = 0L,
            active = true
        )
    )

    val customers = mutableListOf(
        CustomerEntity(
            id = "cust-1",
            name = "Budi Santoso",
            phone = "081234567890"
        ),
        CustomerEntity(
            id = "cust-2",
            name = "Sari Indah",
            phone = "081211112222"
        )
    )

    var lastCreatedOrder: OrderEntity? = null
    var lastCreatedCustomer: CustomerEntity? = null
    var lastIsNewCustomer: Boolean? = null

    override fun observeServices(): Flow<List<LaundryServiceEntity>> = flowOf(sampleServices)
    override suspend fun seedInitialServicesIfEmpty() {}
    override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(emptyList())
    override fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>> = flowOf(emptyList())
    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? = null
    override suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? = null
    override suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? = null
    override suspend fun findOrderByNumberFlexible(query: String): OrderWithLines? = null
    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> = Result.success(Unit)
    override suspend fun advanceOrderStatus(
        orderId: String,
        targetStatus: String,
        rework: Boolean,
        reworkReason: String?,
        reworkApprovedBy: String?
    ): Result<OutboxEntity> = Result.success(
        OutboxEntity(
            bundleId = UUID.randomUUID().toString(),
            deviceId = "dev-1",
            shiftId = null,
            eventsJson = "[]"
        )
    )
    override suspend fun saveOrderWithOutbox(order: OrderEntity, lines: List<OrderLineEntity>, eventsJson: String): Result<Unit> = Result.success(Unit)

    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        val filtered = customers.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
        return flowOf(filtered)
    }

    override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> {
        return flowOf(customers.take(limit))
    }

    override suspend fun getCustomerById(id: String): CustomerEntity? {
        return customers.find { it.id == id }
    }

    override suspend fun saveCustomerLocally(customer: CustomerEntity): Result<Unit> {
        customers.add(customer)
        return Result.success(Unit)
    }

    override suspend fun createLaundryOrder(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        customer: CustomerEntity?,
        isNewCustomer: Boolean,
        deviceId: String,
        shiftId: String?
    ): Result<OutboxEntity> {
        lastCreatedOrder = order
        lastCreatedCustomer = customer
        lastIsNewCustomer = isNewCustomer
        return Result.success(
            OutboxEntity(
                bundleId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = "[]"
            )
        )
    }

    override fun observeLaundryOrders(statusFilter: String?): Flow<List<OrderWithLines>> = flowOf(emptyList())
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryOrderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeLaundryOrderRepository
    private lateinit var viewModel: LaundryOrderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLaundryOrderRepository()
        viewModel = LaundryOrderViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty customer and lines`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertNull(state.selectedCustomer)
        assertEquals("", state.customerName)
        assertEquals("", state.customerPhone)
        assertFalse(state.isNewCustomer)
        assertFalse(state.isCustomerDialogOpen)
        assertEquals(0L, state.calculation.total)
        assertEquals(LaundryPaymentOption.PAY_ON_PICKUP, state.paymentOption)

        collectJob.cancel()
    }

    @Test
    fun `Test Vector L1 - kiloan 3,7 kg rounded to 4,0 kg plus 1 pcs selimut equals 53000`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Line 1: Kiloan 3.7 kg
        viewModel.selectServiceForInput(fakeRepository.sampleServices[0])
        viewModel.setInputWeight(3.7)
        viewModel.addConfiguredServiceToLines()

        // Line 2: Selimut 1 pcs
        viewModel.selectServiceForInput(fakeRepository.sampleServices[1])
        viewModel.setInputPieces(1)
        viewModel.addConfiguredServiceToLines()

        val state = viewModel.uiState.value
        assertEquals(2, state.lines.size)

        // Verifikasi Test Vector L1
        assertEquals(listOf(4.0), state.calculation.billedWeights)
        assertEquals(listOf(28000L, 25000L), state.calculation.lineTotals)
        assertEquals(53000L, state.calculation.total)

        collectJob.cancel()
    }

    @Test
    fun `Test Vector L2 - minimum weight 0,3 kg rounded to 1,0 kg equals 7000`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Input 0.3 kg kiloan
        viewModel.selectServiceForInput(fakeRepository.sampleServices[0])
        viewModel.updateWeight(0, 0.3) // Atau add line dengan 0.3 kg
        viewModel.addLine(fakeRepository.sampleServices[0].copy())
        viewModel.updateWeight(0, 0.3)

        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)

        // Verifikasi Test Vector L2: Billed weight = 1.0 kg (minWeight), total = 7000
        assertEquals(listOf(1.0), state.calculation.billedWeights)
        assertEquals(listOf(7000L), state.calculation.lineTotals)
        assertEquals(7000L, state.calculation.total)

        collectJob.cancel()
    }

    @Test
    fun `Test Vector L3 - express 2,1 kg rounded to 2,5 kg at rate 10000 equals 25000`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Input 2.1 kg kiloan dengan express
        viewModel.selectServiceForInput(fakeRepository.sampleServices[0])
        viewModel.setInputWeight(2.1)
        viewModel.toggleInputExpress()
        viewModel.addConfiguredServiceToLines()

        val state = viewModel.uiState.value
        assertEquals(1, state.lines.size)
        assertTrue(state.lines.first().isExpress)

        // Verifikasi Test Vector L3: Billed weight = 2.5 kg, total = 25000
        assertEquals(listOf(2.5), state.calculation.billedWeights)
        assertEquals(listOf(25000L), state.calculation.lineTotals)
        assertEquals(25000L, state.calculation.total)

        collectJob.cancel()
    }

    @Test
    fun `category filter filters services between kiloan and satuan`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // ALL
        viewModel.setCategoryFilter(LaundryCategoryFilter.ALL)
        assertEquals(2, viewModel.uiState.value.filteredServices.size)

        // KILOAN
        viewModel.setCategoryFilter(LaundryCategoryFilter.KILOAN)
        assertEquals(1, viewModel.uiState.value.filteredServices.size)
        assertEquals("kg", viewModel.uiState.value.filteredServices.first().unit)

        // SATUAN
        viewModel.setCategoryFilter(LaundryCategoryFilter.SATUAN)
        assertEquals(1, viewModel.uiState.value.filteredServices.size)
        assertEquals("pcs", viewModel.uiState.value.filteredServices.first().unit)

        collectJob.cancel()
    }

    @Test
    fun `select existing customer updates uiState with isNewCustomer false`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val existing = CustomerEntity(id = "cust-1", name = "Budi Santoso", phone = "081234567890")
        viewModel.selectCustomer(existing, isNew = false)

        val state = viewModel.uiState.value
        assertEquals("cust-1", state.selectedCustomer?.id)
        assertEquals("Budi Santoso", state.customerName)
        assertEquals("081234567890", state.customerPhone)
        assertFalse(state.isNewCustomer)
        assertFalse(state.isCustomerDialogOpen)

        collectJob.cancel()
    }

    @Test
    fun `createAndSelectNewCustomer normalizes phone and sets isNewCustomer true`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.createAndSelectNewCustomer(
            name = "Ahmad Dani",
            phone = "+62 813-9876-5432"
        )

        val state = viewModel.uiState.value
        assertNotNull(state.selectedCustomer)
        assertEquals("Ahmad Dani", state.customerName)
        assertEquals("081398765432", state.customerPhone)
        assertTrue(state.isNewCustomer)
        assertFalse(state.isCustomerDialogOpen)

        collectJob.cancel()
    }

    @Test
    fun `payment option change updates state`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setPaymentOption(LaundryPaymentOption.PAID_UPFRONT)
        assertEquals(LaundryPaymentOption.PAID_UPFRONT, viewModel.uiState.value.paymentOption)

        viewModel.setPaymentOption(LaundryPaymentOption.DP)
        assertEquals(LaundryPaymentOption.DP, viewModel.uiState.value.paymentOption)

        collectJob.cancel()
    }

    @Test
    fun `submitOrder passes customer and isNewCustomer to repository and clears order`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Tambah layanan
        viewModel.addLine(fakeRepository.sampleServices.first())
        viewModel.createAndSelectNewCustomer("Dewi Lestari", "081299998888")

        val result = viewModel.submitOrder(displayNumber = "L1-0005")
        assertTrue(result.isSuccess)

        // Verifikasi delegasi ke repository
        assertEquals("Dewi Lestari", fakeRepository.lastCreatedCustomer?.name)
        assertEquals("081299998888", fakeRepository.lastCreatedCustomer?.phone)
        assertEquals(true, fakeRepository.lastIsNewCustomer)
        assertEquals("L1-0005", fakeRepository.lastCreatedOrder?.displayNumber)

        // Verifikasi reset order setelah submit
        val stateAfter = viewModel.uiState.value
        assertTrue(stateAfter.lines.isEmpty())
        assertNull(stateAfter.selectedCustomer)
        assertNotNull(stateAfter.orderSuccessNotification)

        collectJob.cancel()
    }

    @Test
    fun `submitOrder without displayNumber uses auto-generated sequence number`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.addLine(fakeRepository.sampleServices.first())
        viewModel.createAndSelectNewCustomer("Budi", "081234567890")

        val result = viewModel.submitOrder()
        assertTrue(result.isSuccess)

        assertEquals("L1-0001", fakeRepository.lastCreatedOrder?.displayNumber)

        collectJob.cancel()
    }

    @Test
    fun `setDpAmount updates dpAmount in uiState`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setDpAmount(25000L)
        assertEquals(25000L, viewModel.uiState.value.dpAmount)

        collectJob.cancel()
    }
}
