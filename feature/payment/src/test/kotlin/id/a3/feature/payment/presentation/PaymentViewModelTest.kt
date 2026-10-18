package id.a3.feature.payment.presentation

import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.feature.payment.repository.PaymentMethod
import id.a3.feature.payment.repository.PaymentRepository
import id.a3.feature.payment.repository.PaymentTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

class FakePaymentRepository : PaymentRepository {
    val processedTransactions = mutableListOf<PaymentTransaction>()
    var mockOrder: OrderWithLines? = null

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? = mockOrder

    override suspend fun processPayment(payment: PaymentTransaction): Result<Unit> {
        processedTransactions.add(payment)
        return Result.success(Unit)
    }

    override suspend fun processTakeAwayDirectPayment(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        payment: PaymentTransaction
    ): Result<Unit> {
        processedTransactions.add(payment)
        return Result.success(Unit)
    }
}

class FakePrinterService : PrinterService {
    val printedBytes = mutableListOf<ByteArray>()
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Connected("iWare C58BT", "00:11:22:33:44:55"))
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()
    override fun isConnected(): Boolean = true
    override suspend fun checkConnection(): Boolean = true
    override suspend fun print(bytes: ByteArray): Result<Unit> {
        printedBytes.add(bytes)
        return Result.success(Unit)
    }
    override suspend fun openCashDrawer(): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePaymentRepository
    private lateinit var fakePrinter: FakePrinterService
    private lateinit var viewModel: PaymentViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePaymentRepository()
        fakePrinter = FakePrinterService()
        viewModel = PaymentViewModel(fakeRepository, fakePrinter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializeWithOrder sets total and initial exact payment state`() = runTest(testDispatcher) {
        viewModel.initializeWithOrder(
            orderId = "order-001",
            totalAmount = 61400L,
            displayNumber = "C1-0007",
            tableOrQueueLabel = "Meja 05"
        )
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("order-001", state.orderId)
        assertEquals(61400L, state.totalAmount)
        assertEquals(61400L, state.cashReceived)
        assertEquals(0L, state.change)
        assertTrue(state.isExactPayment)
        assertEquals(PaymentStep.METHOD_SELECTION, state.currentStep)
        assertEquals(PaymentMethod.CASH, state.selectedMethod)
        assertTrue(state.quickCashOptions.contains(61400L))
    }

    @Test
    fun `setCashReceived calculates change correctly matching test vector K2`() = runTest(testDispatcher) {
        viewModel.initializeWithOrder(
            orderId = "order-001",
            totalAmount = 61400L
        )
        testScheduler.advanceUntilIdle()

        // Test vector K2: Total 61.400, Uang Diterima 70.000 -> Kembalian 8.600
        viewModel.setCashReceived(70000L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(70000L, state.cashReceived)
        assertEquals(8600L, state.change)
        assertFalse(state.isExactPayment)
        assertNull(state.errorMessage)
    }

    @Test
    fun `setCashReceived with amount less than total produces error message`() = runTest(testDispatcher) {
        viewModel.initializeWithOrder(
            orderId = "order-001",
            totalAmount = 61400L
        )
        testScheduler.advanceUntilIdle()

        viewModel.setCashReceived(50000L)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(50000L, state.cashReceived)
        assertEquals(0L, state.change)
        assertFalse(state.isExactPayment)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `selectMethod switches method and transitions correctly`() = runTest(testDispatcher) {
        viewModel.initializeWithOrder("order-001", 34000L)
        testScheduler.advanceUntilIdle()

        // Default Tunai
        viewModel.proceedToMethodInput()
        assertEquals(PaymentStep.CASH_INPUT, viewModel.uiState.value.currentStep)

        // Kembali dan pilih QRIS Statis
        viewModel.backToMethodSelection()
        assertEquals(PaymentStep.METHOD_SELECTION, viewModel.uiState.value.currentStep)

        viewModel.selectMethod(PaymentMethod.STATIC_QRIS)
        assertEquals(PaymentMethod.STATIC_QRIS, viewModel.uiState.value.selectedMethod)

        viewModel.proceedToMethodInput()
        assertEquals(PaymentStep.QRIS_CONFIRM, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `processPayment cash records transaction and prints customer receipt`() = runTest(testDispatcher) {
        val order = OrderEntity(
            id = "order-001",
            displayNumber = "C1-0007",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            tableLabel = "Meja 05",
            status = "open",
            subtotal = 62000L,
            total = 61400L
        )
        val line = OrderLineEntity(
            id = "line-1",
            orderId = "order-001",
            itemRefId = "menu-latte",
            nameSnapshot = "Caffe Latte",
            quantity = 1,
            unitPriceSnapshot = 34000L
        )
        fakeRepository.mockOrder = OrderWithLines(order, listOf(line))

        viewModel.initializeWithOrder("order-001", 61400L, "C1-0007", "Meja 05")
        testScheduler.advanceUntilIdle()

        viewModel.setCashReceived(70000L)
        testScheduler.advanceUntilIdle()

        viewModel.processPayment()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PaymentStep.SUCCESS, state.currentStep)
        assertNull(state.errorMessage)

        // Verifikasi transaksi tercatat di repositori
        assertEquals(1, fakeRepository.processedTransactions.size)
        val tx = fakeRepository.processedTransactions.first()
        assertEquals("order-001", tx.orderId)
        assertEquals(PaymentMethod.CASH, tx.method)
        assertEquals(61400L, tx.totalAmount)
        assertEquals(70000L, tx.cashReceived)
        assertEquals(8600L, tx.change)

        // Verifikasi struk otomatis dicetak ke printer
        assertEquals(1, fakePrinter.printedBytes.size)
        val receiptText = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("A3 COFFEESHOP"))
        assertTrue(receiptText.contains("C1-0007"))
        assertTrue(receiptText.contains("Bayar (Tunai)"))
        assertTrue(receiptText.contains("Kembalian"))
    }

    @Test
    fun `printCustomerReceipt with isCopy true adds SALINAN header`() = runTest(testDispatcher) {
        viewModel.initializeWithOrder("order-001", 26000L, "C1-0008")
        testScheduler.advanceUntilIdle()

        viewModel.printCustomerReceipt(isCopy = true)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakePrinter.printedBytes.size)
        val receiptText = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("*** SALINAN ***"))
    }

    @Test
    fun `generateQuickCashOptions generates accurate denominations`() {
        // Kasus 61.400: Uang pas 61.400, pembulatan ke 10k (70.000), pembulatan ke 50k (100.000)
        val options = PaymentViewModel.generateQuickCashOptions(61400L)
        assertTrue(options.contains(61400L))
        assertTrue(options.contains(70000L))
        assertTrue(options.contains(100000L))
    }
}
