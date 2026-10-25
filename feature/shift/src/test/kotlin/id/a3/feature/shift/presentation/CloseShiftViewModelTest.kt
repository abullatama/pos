package id.a3.feature.shift.presentation

import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.feature.shift.repository.ActiveShift
import id.a3.feature.shift.repository.ShiftSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class FakePrinterForShift : PrinterService {
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
class CloseShiftViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeShiftRepository
    private lateinit var fakePrinter: FakePrinterForShift
    private lateinit var viewModel: CloseShiftViewModel

    private val shiftId = "shift-001"
    private val qrisPayment = PaymentEntity(
        id = "pay-qris-1",
        orderId = "ord-1",
        method = "qris_static",
        amount = 100000L,
        idempotencyKey = "key-1",
        shiftId = shiftId
    )

    private val k1Summary = ShiftSummary(
        shift = ActiveShift(
            shiftId = shiftId,
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            openedAt = "2026-10-19T08:00:00Z",
            openingCash = 200000L
        ),
        grossSales = 600000L,
        totalDiscount = 0L,
        totalRefund = 25000L,
        netSales = 575000L,
        transactionCount = 10,
        cashSales = 500000L,
        cashDpAndSettlement = 100000L,
        qrisSales = 100000L,
        cashOuts = 50000L,
        // Sesuai Test Vector K1: 200k + 500k + 100k - 25k - 50k = 725k
        expectedCash = 725000L,
        qrisPayments = listOf(qrisPayment),
        openOrderIds = listOf("ord-open-1")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeShiftRepository()
        fakeRepository.mockSummary = k1Summary
        fakePrinter = FakePrinterForShift()
        viewModel = CloseShiftViewModel(fakeRepository, fakePrinter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializeWithShift loads summary and initializes expected cash K1`() = runTest {
        viewModel.initializeWithShift(shiftId)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.summary)
        assertEquals(725000L, state.expectedCash)
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, state.currentStep)
    }

    @Test
    fun `blind cash count calculates total accurately`() = runTest {
        viewModel.initializeWithShift(shiftId)

        // Hitung kas: 7 lembar 100k (700.000) + 1 lembar 20k (20.000) + 1 lembar 5k (5.000) = 725.000
        viewModel.adjustDenominationCount(100000L, 7)
        viewModel.adjustDenominationCount(20000L, 1)
        viewModel.adjustDenominationCount(5000L, 1)

        val state = viewModel.uiState.value
        assertEquals(725000L, state.countedCash)
        assertEquals(725000L, state.expectedCash)
        assertEquals(0L, state.cashDifference)
        assertFalse(state.isDifferenceOverTolerance)
    }

    @Test
    fun `step 2 evaluates difference over tolerance requiring reason`() = runTest {
        viewModel.initializeWithShift(shiftId)

        // Kas dihitung: 720.000 (selisih -5.000, melebihi toleransi Rp 2.000 OQ-07)
        viewModel.adjustDenominationCount(100000L, 7)
        viewModel.adjustDenominationCount(20000L, 1)

        viewModel.proceedToStep2()

        val state = viewModel.uiState.value
        assertEquals(CloseShiftStep.STEP_2_DIFF_AND_QRIS, state.currentStep)
        assertEquals(720000L, state.countedCash)
        assertEquals(-5000L, state.cashDifference)
        assertTrue(state.isDifferenceOverTolerance)
        assertFalse(state.isStep2Valid)

        // Mengisi alasan selisih kas -> valid
        viewModel.setDifferenceReason("Uang kembalian kurang Rp5.000")
        assertTrue(viewModel.uiState.value.isStep2Valid)
    }

    @Test
    fun `qris checking toggles payment check correctly`() = runTest {
        viewModel.initializeWithShift(shiftId)
        viewModel.proceedToStep2()

        assertFalse(viewModel.uiState.value.checkedQrisPaymentIds.contains("pay-qris-1"))

        viewModel.toggleQrisChecked("pay-qris-1")
        assertTrue(viewModel.uiState.value.checkedQrisPaymentIds.contains("pay-qris-1"))

        viewModel.checkAllQris()
        assertTrue(viewModel.uiState.value.checkedQrisPaymentIds.contains("pay-qris-1"))
    }

    @Test
    fun `step 3 allocation validation enforces totalAllocated equals countedCash`() = runTest {
        viewModel.initializeWithShift(shiftId)

        // Kas dihitung: 725.000
        viewModel.adjustDenominationCount(100000L, 7)
        viewModel.adjustDenominationCount(20000L, 1)
        viewModel.adjustDenominationCount(5000L, 1)

        viewModel.proceedToStep2()
        viewModel.proceedToStep3()

        val state = viewModel.uiState.value
        assertEquals(CloseShiftStep.STEP_3_ALLOCATION_AND_Z, state.currentStep)
        assertEquals(725000L, state.countedCash)

        // Default alokasi: Laci 200.000, Setor 525.000 (total = 725.000)
        assertTrue(state.isAllocationBalanced)

        // Jika disetor > 0 tapi penerima kosong -> canProceedToClose false (TUP-06)
        assertFalse(state.canProceedToClose)

        // Isi nama penerima
        viewModel.setCashTakenBy("Pak Owner")
        assertTrue(viewModel.uiState.value.canProceedToClose)

        // Jika alokasi sengaja diubah tidak seimbang: Laci 200.000 + Setor 500.000 = 700.000 != 725.000
        viewModel.setCashDeposited(500000L)
        assertFalse(viewModel.uiState.value.isAllocationBalanced)
        assertFalse(viewModel.uiState.value.canProceedToClose)
    }

    @Test
    fun `submitCloseShift closes shift and prints Z report`() = runTest {
        viewModel.initializeWithShift(shiftId)

        // Hitung kas K1: 725.000
        viewModel.adjustDenominationCount(100000L, 7)
        viewModel.adjustDenominationCount(20000L, 1)
        viewModel.adjustDenominationCount(5000L, 1)

        viewModel.proceedToStep2()
        viewModel.toggleQrisChecked("pay-qris-1")
        viewModel.proceedToStep3()

        viewModel.setCashLeft(200000L)
        viewModel.setCashDeposited(525000L)
        viewModel.setCashTakenBy("Pak Owner")

        var isCompletedCalled = false
        viewModel.submitCloseShift(onSuccess = { isCompletedCalled = true })

        assertTrue(isCompletedCalled)
        assertTrue(viewModel.uiState.value.isSuccess)

        // Verifikasi delegasi ke repository
        assertEquals(725000L, fakeRepository.lastClosedCountedCash)
        assertEquals(200000L, fakeRepository.lastClosedCashLeft)
        assertEquals(525000L, fakeRepository.lastClosedCashDeposited)
        assertEquals("Pak Owner", fakeRepository.lastClosedCashTakenBy)

        // Verifikasi Laporan Z otomatis dicetak ke printer
        assertEquals(1, fakePrinter.printedBytes.size)
        val text = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(text.contains("LAPORAN Z"))
        assertTrue(text.contains("725.000"))
    }

    @Test
    fun `recount requires manager pin authorization to return to step 1`() = runTest {
        viewModel.initializeWithShift(shiftId)
        viewModel.adjustDenominationCount(100000L, 5)
        viewModel.proceedToStep2()

        assertEquals(CloseShiftStep.STEP_2_DIFF_AND_QRIS, viewModel.uiState.value.currentStep)

        // Buka dialog recount
        viewModel.openRecountPinDialog()
        assertTrue(viewModel.uiState.value.isRecountPinDialogOpen)

        // PIN salah
        viewModel.verifyManagerPinForRecount("1111")
        assertTrue(viewModel.uiState.value.isRecountPinDialogOpen)
        assertNotNull(viewModel.uiState.value.recountPinError)
        assertEquals(CloseShiftStep.STEP_2_DIFF_AND_QRIS, viewModel.uiState.value.currentStep)

        // PIN benar
        viewModel.verifyManagerPinForRecount("4444")
        assertFalse(viewModel.uiState.value.isRecountPinDialogOpen)
        assertNull(viewModel.uiState.value.recountPinError)
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `initializeWithShift resets state cleanly when closing subsequent shift`() = runTest {
        viewModel.initializeWithShift(shiftId)
        viewModel.adjustDenominationCount(100000L, 7)
        viewModel.proceedToStep2()
        viewModel.setDifferenceReason("Catatan selisih shift 1")
        viewModel.proceedToStep3()
        viewModel.setCashTakenBy("Owner Shift 1")

        // Pastikan berada di Step 3 dengan state terisi
        assertEquals(CloseShiftStep.STEP_3_ALLOCATION_AND_Z, viewModel.uiState.value.currentStep)
        assertEquals(700000L, viewModel.uiState.value.countedCash)
        assertEquals("Catatan selisih shift 1", viewModel.uiState.value.differenceReason)

        // Buka proses tutup shift berikutnya (Shift B)
        viewModel.initializeWithShift("shift-002")

        val state = viewModel.uiState.value
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, state.currentStep)
        assertEquals(0L, state.countedCash)
        assertTrue(state.denominations.isEmpty())
        assertTrue(state.differenceReason.isEmpty())
        assertEquals(0L, state.cashDeposited)
        assertTrue(state.cashTakenBy.isEmpty())
        assertFalse(state.isSuccess)
        assertFalse(state.isClosing)
        assertFalse(state.isRecountPinDialogOpen)
        assertTrue(state.checkedQrisPaymentIds.isEmpty())
    }

    @Test
    fun `resetState restores fresh clean state`() = runTest {
        viewModel.initializeWithShift(shiftId)
        viewModel.adjustDenominationCount(100000L, 5)
        viewModel.proceedToStep2()

        assertEquals(CloseShiftStep.STEP_2_DIFF_AND_QRIS, viewModel.uiState.value.currentStep)
        assertEquals(500000L, viewModel.uiState.value.countedCash)

        viewModel.resetState()

        val state = viewModel.uiState.value
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, state.currentStep)
        assertEquals(0L, state.countedCash)
        assertTrue(state.denominations.isEmpty())
        assertNull(state.summary)
        assertFalse(state.isLoading)
        assertFalse(state.isStep1ConfirmationDialogOpen)
    }

    @Test
    fun `openStep1ConfirmationDialog opens dialog when countedCash is positive`() = runTest {
        viewModel.initializeWithShift(shiftId)
        assertFalse(viewModel.uiState.value.isStep1ConfirmationDialogOpen)

        // Tidak boleh terbuka jika countedCash masih 0
        viewModel.openStep1ConfirmationDialog()
        assertFalse(viewModel.uiState.value.isStep1ConfirmationDialogOpen)

        // Setelah kas dihitung
        viewModel.adjustDenominationCount(50000L, 2)
        assertEquals(100000L, viewModel.uiState.value.countedCash)

        viewModel.openStep1ConfirmationDialog()
        assertTrue(viewModel.uiState.value.isStep1ConfirmationDialogOpen)
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, viewModel.uiState.value.currentStep)

        // Tutup dialog konfirmasi
        viewModel.closeStep1ConfirmationDialog()
        assertFalse(viewModel.uiState.value.isStep1ConfirmationDialogOpen)
        assertEquals(CloseShiftStep.STEP_1_BLIND_COUNT, viewModel.uiState.value.currentStep)

        // Buka lagi lalu konfirmasi lanjut ke Step 2
        viewModel.openStep1ConfirmationDialog()
        assertTrue(viewModel.uiState.value.isStep1ConfirmationDialogOpen)

        viewModel.proceedToStep2()
        assertFalse(viewModel.uiState.value.isStep1ConfirmationDialogOpen)
        assertEquals(CloseShiftStep.STEP_2_DIFF_AND_QRIS, viewModel.uiState.value.currentStep)
    }
}
