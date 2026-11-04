package id.a3.feature.cafe.presentation

import id.a3.core.domain.cafe.CafeCalculationInput
import id.a3.core.domain.cafe.CafeCalculationResult
import id.a3.core.domain.cafe.CafeCalculator
import id.a3.core.domain.cafe.CafeOrderLine
import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.cafe.TaxMode
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.feature.cafe.model.AppliedDiscount
import id.a3.feature.cafe.model.CafeCartLine
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.model.DiscountPreset
import id.a3.feature.cafe.model.MenuOptionItem
import id.a3.feature.cafe.repository.TaxAndServiceSettings
import id.a3.feature.cafe.model.OptionKind
import id.a3.feature.cafe.presentation.screen.formatOrderDuration
import id.a3.feature.cafe.repository.CafeMenuRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

class FakeCafeMenuRepository : CafeMenuRepository {
    val sampleProducts = listOf(
        CafeProduct(
            id = "menu-latte",
            name = "Caffe Latte",
            category = "Kopi",
            basePrice = 34000L,
            options = listOf(
                MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                MenuOptionItem(kind = OptionKind.ADDON, name = "Shot Tambahan", priceDelta = 6000L)
            )
        ),
        CafeProduct(
            id = "menu-americano",
            name = "Americano",
            category = "Kopi",
            basePrice = 26000L,
            options = emptyList()
        ),
        CafeProduct(
            id = "menu-croissant",
            name = "French Butter Croissant",
            category = "Roti & Makanan",
            basePrice = 28000L,
            options = emptyList()
        ),
        CafeProduct(
            id = "menu-steak",
            name = "Wagyu Sirloin",
            category = "Makanan",
            basePrice = 40000L,
            options = emptyList()
        ),
        CafeProduct(
            id = "menu-mineral",
            name = "Air Mineral",
            category = "Non-kopi",
            basePrice = 10050L,
            options = emptyList()
        )
    )

    private val _productsFlow = MutableStateFlow(sampleProducts)

    override fun observeProducts(): Flow<List<CafeProduct>> = _productsFlow.asStateFlow()

    override fun observeDiscountPresets(): Flow<List<DiscountPreset>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeTaxAndService(): Flow<TaxAndServiceSettings> = kotlinx.coroutines.flow.flowOf(TaxAndServiceSettings())

    override suspend fun getProductById(id: String): CafeProduct? = sampleProducts.find { it.id == id }

    override suspend fun seedInitialMenuIfEmpty() {}

    override suspend fun setProductSoldOut(id: String, soldOut: Boolean) {
        val updated = _productsFlow.value.map {
            if (it.id == id) it.copy(soldOut = soldOut) else it
        }
        _productsFlow.value = updated
    }
}

class FakePrinterService : PrinterService {
    val printedPayloads = mutableListOf<ByteArray>()
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Connected("iWare C58BT", "00:11:22:33:44:55"))
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()
    override fun isConnected(): Boolean = true
    override suspend fun checkConnection(): Boolean = true
    override suspend fun print(bytes: ByteArray): Result<Unit> {
        printedPayloads.add(bytes)
        return Result.success(Unit)
    }
    override suspend fun openCashDrawer(): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CafeMenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeCafeMenuRepository
    private lateinit var fakePrinter: FakePrinterService
    private lateinit var viewModel: CafeMenuViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeCafeMenuRepository()
        fakePrinter = FakePrinterService()
        viewModel = CafeMenuViewModel(fakeRepository, null, fakePrinter, testDispatcher, managerApprover = FakeManagerApprover())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has products and empty cart with 0 total`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.products.size)
        assertTrue(state.cartLines.isEmpty())
        assertEquals(0L, state.calculation.total)
    }

    @Test
    fun `clicking product without options also opens modal sheet for verification and quantity`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val americano = fakeRepository.sampleProducts[1]
        viewModel.onProductClicked(americano)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.sheetProduct)
        assertEquals("Americano", state.sheetProduct?.name)
        assertTrue(state.cartLines.isEmpty())

        // Konfirmasi dari modal verifikasi
        viewModel.addCartLine(CafeCartLine(product = americano, quantity = 1))
        testScheduler.advanceUntilIdle()

        val stateAfterAdd = viewModel.uiState.value
        assertEquals(1, stateAfterAdd.cartLines.size)
        assertEquals("Americano", stateAfterAdd.cartLines.first().product.name)
        assertEquals(26000L, stateAfterAdd.cartLines.first().lineTotal)
        assertEquals(26000L, stateAfterAdd.calculation.total)
        assertNull(stateAfterAdd.sheetProduct)
    }

    @Test
    fun `clicking product with options opens modal sheet`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val latte = fakeRepository.sampleProducts[0]
        viewModel.onProductClicked(latte)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.sheetProduct)
        assertEquals("Caffe Latte", state.sheetProduct?.name)
        assertTrue(state.cartLines.isEmpty()) // belum ditambahkan ke keranjang sebelum varian dikonfirmasi
    }

    @Test
    fun `addCartLine with variant and addon computes unit price and total correctly`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val latte = fakeRepository.sampleProducts[0]
        val coldVariant = latte.variants.first { it.name == "Dingin" }
        val extraShotAddon = latte.addons.first { it.name == "Shot Tambahan" }

        val line = CafeCartLine(
            product = latte,
            selectedVariant = coldVariant,
            selectedAddons = listOf(extraShotAddon),
            quantity = 2
        )

        // Base 34.000 + Shot 6.000 = 40.000 per unit, qty 2 = 80.000
        assertEquals(40000L, line.unitPrice)
        assertEquals(80000L, line.lineTotal)

        viewModel.addCartLine(line)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.cartLines.size)
        assertEquals(80000L, state.calculation.subtotal)
        assertEquals(80000L, state.calculation.total)
        assertNull(state.sheetProduct)
    }

    @Test
    fun `cart calculation matches core domain test vector C1 deterministically`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        // Test vector C1: Latte 34.000 (qty 1) + Croissant 28.000 (qty 1) = 62.000
        // Diskon preset 10% (6.200) -> DPP 55.800 -> Pajak 10% (5.580) -> Pembulatan +20 -> Total 61.400
        val latte = fakeRepository.sampleProducts[0]
        val croissant = fakeRepository.sampleProducts[2]

        viewModel.addCartLine(CafeCartLine(product = latte, quantity = 1))
        viewModel.addCartLine(CafeCartLine(product = croissant, quantity = 1))

        viewModel.applyAppliedDiscount(
            AppliedDiscount(
                discount = OrderDiscount(type = DiscountType.PERCENT, value = 10L),
                label = "Diskon 10%",
                isPreset = true
            )
        )
        viewModel.configureTaxAndService(servicePercent = 0, taxPercent = 10, taxMode = TaxMode.ADDED)

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(62000L, state.calculation.subtotal)
        assertEquals(6200L, state.calculation.discount)
        assertEquals(0L, state.calculation.serviceCharge)
        assertEquals(5580L, state.calculation.tax)
        assertEquals(20L, state.calculation.rounding)
        assertEquals(61400L, state.calculation.total)
        assertEquals("Diskon 10%", state.appliedDiscount?.label)
        assertTrue(state.appliedDiscount?.isPreset == true)

        // Validasi identik 100% dengan CafeCalculator
        val domainInput = CafeCalculationInput(
            lines = listOf(
                CafeOrderLine(unitPrice = 34000L, quantity = 1),
                CafeOrderLine(unitPrice = 28000L, quantity = 1)
            ),
            discount = OrderDiscount(type = DiscountType.PERCENT, value = 10L),
            servicePercent = 0,
            taxPercent = 10,
            taxMode = TaxMode.ADDED
        )
        val domainResult = CafeCalculator.calculate(domainInput)
        assertEquals(domainResult.total, state.calculation.total)
    }

    @Test
    fun `cart calculation matches core domain test vector C2 deterministically`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        // Test vector C2:
        // lines: 40000 x 2 = 80.000
        // discount: amount 5000 -> DPP = 75.000
        // service: 5% atas DPP = 3.750
        // tax: 10% atas (75.000 + 3.750) = 7.875
        // sebelum pembulatan = 75.000 + 3.750 + 7.875 = 86.625
        // pembulatan = -25 -> Total = 86.600
        val steak = fakeRepository.sampleProducts[3]

        viewModel.addCartLine(CafeCartLine(product = steak, quantity = 2))

        viewModel.applyAppliedDiscount(
            AppliedDiscount(
                discount = OrderDiscount(type = DiscountType.AMOUNT, value = 5000L),
                label = "Potongan Rp5.000",
                isPreset = false,
                approval = id.a3.core.domain.security.ManagerApproval(2L, "Manager Uji", "manager"),
                reason = "Promo Khusus / Tamu VIP"
            )
        )
        viewModel.configureTaxAndService(servicePercent = 5, taxPercent = 10, taxMode = TaxMode.ADDED)

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(80000L, state.calculation.subtotal)
        assertEquals(5000L, state.calculation.discount)
        assertEquals(3750L, state.calculation.serviceCharge)
        assertEquals(7875L, state.calculation.tax)
        assertEquals(-25L, state.calculation.rounding)
        assertEquals(86600L, state.calculation.total)

        // Validasi identik 100% dengan CafeCalculator
        val domainInput = CafeCalculationInput(
            lines = listOf(CafeOrderLine(unitPrice = 40000L, quantity = 2)),
            discount = OrderDiscount(type = DiscountType.AMOUNT, value = 5000L),
            servicePercent = 5,
            taxPercent = 10,
            taxMode = TaxMode.ADDED
        )
        val domainResult = CafeCalculator.calculate(domainInput)
        assertEquals(domainResult.total, state.calculation.total)
        assertEquals(domainResult.rounding, state.calculation.rounding)
        assertEquals(domainResult.tax, state.calculation.tax)
        assertEquals(domainResult.serviceCharge, state.calculation.serviceCharge)
    }

    @Test
    fun `cart calculation matches core domain test vector C3 deterministically`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        // Test vector C3:
        // lines: 10050 x 1 = 10.050
        // discount: null, service: 0, tax: 0
        // rounding: +50 -> Total = 10.100 (pembulatan setengah naik ke Rp100)
        val mineral = fakeRepository.sampleProducts[4]

        viewModel.addCartLine(CafeCartLine(product = mineral, quantity = 1))
        viewModel.configureTaxAndService(servicePercent = 0, taxPercent = 0, taxMode = TaxMode.ADDED)

        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(10050L, state.calculation.subtotal)
        assertEquals(0L, state.calculation.discount)
        assertEquals(50L, state.calculation.rounding)
        assertEquals(10100L, state.calculation.total)

        val domainInput = CafeCalculationInput(
            lines = listOf(CafeOrderLine(unitPrice = 10050L, quantity = 1)),
            discount = null,
            servicePercent = 0,
            taxPercent = 0,
            taxMode = TaxMode.ADDED
        )
        val domainResult = CafeCalculator.calculate(domainInput)
        assertEquals(domainResult.total, state.calculation.total)
        assertEquals(domainResult.rounding, state.calculation.rounding)
    }

    @Test
    fun `discount dialog state opens and dismisses accurately`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDiscountDialogOpen)

        viewModel.openDiscountDialog()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDiscountDialogOpen)

        viewModel.dismissDiscountDialog()
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDiscountDialogOpen)
    }

    @Test
    fun `removeDiscount clears discount and updates calculation`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val latte = fakeRepository.sampleProducts[0]
        viewModel.addCartLine(CafeCartLine(product = latte, quantity = 1))
        viewModel.applyAppliedDiscount(
            AppliedDiscount(
                discount = OrderDiscount(type = DiscountType.PERCENT, value = 10L),
                label = "Diskon 10%",
                isPreset = true
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(3400L, viewModel.uiState.value.calculation.discount)

        viewModel.removeDiscount()
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.appliedDiscount)
        assertNull(viewModel.uiState.value.discount)
        assertEquals(0L, viewModel.uiState.value.calculation.discount)
        assertEquals(34000L, viewModel.uiState.value.calculation.total)
    }

    @Test
    fun `verifyManagerPin succeeds with the approver PIN and locks after 3 failures`() = runTest(testDispatcher) {
        // PIN benar menurut penyetuju tiruan
        assertTrue(viewModel.verifyManagerPin("4444"))

        // Percobaan salah 1
        assertFalse(viewModel.verifyManagerPin("1111"))
        // Percobaan salah 2
        assertFalse(viewModel.verifyManagerPin("2222"))
        // Percobaan salah 3 -> Akun terkunci sementara (COR-07)
        assertFalse(viewModel.verifyManagerPin("3333"))

        // Sekarang memasukkan PIN benar pun ditolak karena terkunci sementara
        assertFalse(viewModel.verifyManagerPin("4444"))
    }

    @Test
    fun `updateQuantity updates quantity and removes item when reaching zero`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val americano = fakeRepository.sampleProducts[1]
        viewModel.addCartLine(CafeCartLine(product = americano, quantity = 1))
        testScheduler.advanceUntilIdle()

        val lineId = viewModel.uiState.value.cartLines.first().id

        // Tingkatkan quantity
        viewModel.updateQuantity(lineId, 1)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.cartLines.first().quantity)
        assertEquals(52000L, viewModel.uiState.value.calculation.total)

        // Kurangi sampai 0
        viewModel.updateQuantity(lineId, -2)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.cartLines.isEmpty())
        assertEquals(0L, viewModel.uiState.value.calculation.total)
    }

    @Test
    fun `category filter filters products accurately`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        viewModel.selectCategory("Roti & Makanan")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.products.size)
        assertEquals("French Butter Croissant", state.products.first().name)
    }

    @Test
    fun `search query filters products by name`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("latte")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.products.size)
        assertEquals("Caffe Latte", state.products.first().name)
    }

    @Test
    fun `clearCart removes all lines and resets calculation`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val americano = fakeRepository.sampleProducts[1]
        viewModel.addCartLine(CafeCartLine(product = americano, quantity = 1))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.cartLines.isNotEmpty())

        viewModel.clearCart()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.cartLines.isEmpty())
        assertEquals(0L, viewModel.uiState.value.calculation.total)
    }

    @Test
    fun `moveTable updates table label and closes move table dialog`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        assertEquals("Meja 01", viewModel.uiState.value.orderHeader.tableOrQueueLabel)
        assertFalse(viewModel.uiState.value.isMoveTableDialogOpen)

        // Buka dialog pindah meja
        viewModel.openMoveTableDialog()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMoveTableDialogOpen)

        // Pindahkan ke Meja 05
        viewModel.moveTable("Meja 05")
        testScheduler.advanceUntilIdle()

        assertEquals("Meja 05", viewModel.uiState.value.orderHeader.tableOrQueueLabel)
        assertFalse(viewModel.uiState.value.isMoveTableDialogOpen)
    }

    @Test
    fun `toggleProductSoldOut updates product soldOut status and prevents opening verification sheet`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val latte = viewModel.uiState.value.products.first { it.id == "menu-latte" }
        assertFalse(latte.soldOut)

        // Tandai latte sebagai Habis (C07)
        viewModel.toggleProductSoldOut("menu-latte", true)
        testScheduler.advanceUntilIdle()

        val updatedLatte = viewModel.uiState.value.products.first { it.id == "menu-latte" }
        assertTrue(updatedLatte.soldOut)

        // Mengetuk produk yang habis tidak membuka sheet verifikasi dan tidak masuk keranjang
        viewModel.onProductClicked(updatedLatte)
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.sheetProduct)
        assertTrue(viewModel.uiState.value.cartLines.isEmpty())
    }

    @Test
    fun `sendOrderToKitchen clears cart and generates new order header`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()

        val latte = fakeRepository.sampleProducts[0]
        viewModel.addCartLine(CafeCartLine(product = latte, quantity = 2))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.cartLines.size)
        assertEquals(68000L, viewModel.uiState.value.calculation.total)
        val initialOrderId = viewModel.uiState.value.orderHeader.orderId

        val result = viewModel.sendOrderToKitchen()
        testScheduler.advanceUntilIdle()

        assertTrue(result.isSuccess)
        assertEquals(initialOrderId, result.getOrNull())
        assertTrue(viewModel.uiState.value.cartLines.isEmpty())
        assertEquals(0L, viewModel.uiState.value.calculation.total)

        // Verifikasi printer menerima byte tiket dapur tanpa harga (PRT-03 & F18)
        assertEquals(1, fakePrinter.printedPayloads.size)
        val printedText = String(fakePrinter.printedPayloads.first(), Charsets.ISO_8859_1)
        assertTrue(printedText.contains("TIKET DAPUR"))
        assertTrue(printedText.contains("2x Caffe Latte"))
        assertTrue(printedText.contains("*** TANPA HARGA ***"))
        assertFalse(printedText.contains("Rp"))
    }

    @Test
    fun `formatOrderDuration converts raw minutes to friendly hours and minutes`() {
        assertEquals("Baru saja", formatOrderDuration(0L))
        assertEquals("25 menit", formatOrderDuration(25L))
        assertEquals("1 jam", formatOrderDuration(60L))
        assertEquals("3 jam 50 menit", formatOrderDuration(230L))
        assertEquals("2 jam 5 menit", formatOrderDuration(125L))
    }

    @Test
    fun `refundOrVoidOrder returns failure when transactionManager is null`() = runTest(testDispatcher) {
        val dummyOrder = id.a3.core.data.local.entity.OrderWithLines(
            order = id.a3.core.data.local.entity.OrderEntity(
                id = "ord-1",
                displayNumber = "C1-0001",
                businessUnitId = "bu-1",
                shiftId = "shift-1",
                deviceId = "dev-1",
                cashierId = "u-1",
                orderType = "dine_in",
                subtotal = 34000L,
                total = 34000L,
                paidAmount = 34000L
            ),
            lines = emptyList()
        )
        val res = viewModel.refundOrVoidOrder(
            orderWithLines = dummyOrder,
            type = "refund",
            reasonCode = "wrong_input",
            note = null
        )
        assertTrue(res.isFailure)
    }
}
