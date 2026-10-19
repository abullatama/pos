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
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FakeLaundryHomeRepository : LaundryOrderRepository {
    val mockTodayOrdersFlow = kotlinx.coroutines.flow.MutableStateFlow(14)
    val mockReadyOrdersCountFlow = kotlinx.coroutines.flow.MutableStateFlow(6)
    val mockUnpaidOrdersFlow = kotlinx.coroutines.flow.MutableStateFlow(3)
    val mockReadyOrdersFlow = kotlinx.coroutines.flow.MutableStateFlow<List<OrderWithLines>>(emptyList())
    val customers = mutableListOf<CustomerEntity>()

    override fun observeServices(): Flow<List<LaundryServiceEntity>> = flowOf(emptyList())
    override suspend fun seedInitialServicesIfEmpty() {}
    override fun observeOrders(): Flow<List<OrderEntity>> = flowOf(emptyList())
    override fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>> = flowOf(emptyList())
    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? = null
    override suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? = null
    override suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? = null
    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> = Result.success(Unit)
    override suspend fun advanceOrderStatus(orderId: String, targetStatus: String, rework: Boolean, reworkReason: String?, reworkApprovedBy: String?): Result<OutboxEntity> =
        Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = "dev-1", shiftId = null, eventsJson = "[]"))
    override suspend fun saveOrderWithOutbox(order: OrderEntity, lines: List<OrderLineEntity>, eventsJson: String): Result<Unit> = Result.success(Unit)
    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> = flowOf(emptyList())
    override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> = flowOf(emptyList())
    override suspend fun getCustomerById(id: String): CustomerEntity? = customers.find { it.id == id }
    override suspend fun saveCustomerLocally(customer: CustomerEntity): Result<Unit> = Result.success(Unit)
    override suspend fun createLaundryOrder(order: OrderEntity, lines: List<OrderLineEntity>, customer: CustomerEntity?, isNewCustomer: Boolean, deviceId: String, shiftId: String?): Result<OutboxEntity> =
        Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = shiftId, eventsJson = "[]"))
    override fun observeLaundryOrders(statusFilter: String?): Flow<List<OrderWithLines>> = flowOf(emptyList())
    override fun observePickups(orderId: String): Flow<List<LaundryPickupEntity>> = flowOf(emptyList())
    override suspend fun recordPickups(orderId: String, items: List<LaundryPickupItem>, userId: String?, approvedBy: String?, deviceId: String): Result<OutboxEntity> =
        Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    override suspend fun reworkOrder(orderId: String, reason: String, approvedBy: String, deviceId: String): Result<OutboxEntity> =
        Result.success(OutboxEntity(bundleId = UUID.randomUUID().toString(), deviceId = deviceId, shiftId = null, eventsJson = "[]"))
    override suspend fun recordCorrection(orderId: String, type: String, amount: Long, reasonCode: String, approvedBy: String, note: String?): Result<Unit> =
        Result.success(Unit)
    override suspend fun getNextDisplayNumber(prefix: String): String = "${prefix}0001"
    override fun observeTodayOrdersCount(): Flow<Int> = mockTodayOrdersFlow
    override fun observeReadyOrdersCount(): Flow<Int> = mockReadyOrdersCountFlow
    override fun observeUnpaidOrdersCount(): Flow<Int> = mockUnpaidOrdersFlow
    override fun observeReadyOrders(limit: Int): Flow<List<OrderWithLines>> = mockReadyOrdersFlow
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryHomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeLaundryHomeRepository
    private lateinit var viewModel: LaundryHomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLaundryHomeRepository()
        viewModel = LaundryHomeViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits correct summary metrics and ready orders list`() = runTest {
        val customer = CustomerEntity(id = "cust-1", name = "Budi Santoso", phone = "081234567890")
        fakeRepository.customers.add(customer)

        val readyOrder = OrderEntity(
            id = "ord-ready-1",
            displayNumber = "L1-0012",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            customerId = "cust-1",
            status = "ready",
            subtotal = 53000L,
            total = 53000L,
            paidAmount = 20000L // Sisa 33.000 (DP 20k)
        )
        val line = OrderLineEntity(
            id = "line-1",
            orderId = "ord-ready-1",
            itemRefId = "svc-kiloan",
            nameSnapshot = "Kiloan Reguler 4,0 kg",
            quantity = 1,
            unitPriceSnapshot = 28000L
        )
        fakeRepository.mockReadyOrdersFlow.value = listOf(OrderWithLines(readyOrder, listOf(line)))

        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(14, state.todayOrdersCount)
        assertEquals(6, state.readyOrdersCount)
        assertEquals(3, state.unpaidOrdersCount)

        assertEquals(1, state.readyOrders.size)
        val firstItem = state.readyOrders.first()
        assertEquals("L1-0012", firstItem.orderWithLines.order.displayNumber)
        assertEquals("Budi Santoso", firstItem.customer?.name)
        assertEquals(33000L, firstItem.remainingBalance)

        collectJob.cancel()
    }
}
