package id.a3.feature.shift.presentation

import id.a3.core.data.local.entity.CashOutEntity
import id.a3.feature.shift.repository.ActiveShift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class CashOutViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeShiftRepository
    private lateinit var fakePrinter: FakePrinterForShift
    private lateinit var viewModel: CashOutViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeShiftRepository()
        fakeRepository.currentShiftFlow.value = ActiveShift(
            shiftId = "shift-001",
            cashierId = "u-cashier-1",
            cashierName = "Budi Kasir",
            openedAt = "2026-10-19T08:00:00Z",
            openingCash = 200000L
        )
        fakePrinter = FakePrinterForShift()
        viewModel = CashOutViewModel(fakeRepository, fakePrinter, testDispatcher)
        viewModel.initializeStore("AA COFFEESHOP", "Jl. Kopi Pilihan No. 3", "0812-3456-7890")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default category and zero amount`() {
        val state = viewModel.uiState.value
        assertEquals("Bahan baku", state.category)
        assertEquals(0L, state.amount)
        assertEquals("", state.reason)
        assertFalse(state.isPinRequired)
        assertFalse(state.isPhotoRequired)
        assertFalse(state.isFormValid)
    }

    @Test
    fun `amount and preset methods update amount correctly`() {
        viewModel.setAmount(30000L)
        assertEquals(30000L, viewModel.uiState.value.amount)

        viewModel.addAmountPreset(20000L)
        assertEquals(50000L, viewModel.uiState.value.amount)
        assertFalse(viewModel.uiState.value.isPinRequired)

        viewModel.addAmountPreset(10000L)
        assertEquals(60000L, viewModel.uiState.value.amount)
        assertTrue(viewModel.uiState.value.isPinRequired)
        assertTrue(viewModel.uiState.value.isPhotoRequired)
    }

    @Test
    fun `submitCashOut fails when amount is zero or reason is blank`() {
        viewModel.setAmount(0L)
        viewModel.setReason("Beli es batu")
        viewModel.submitCashOut()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSuccess)

        viewModel.setAmount(30000L)
        viewModel.setReason("   ")
        viewModel.submitCashOut()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `submitCashOut within threshold Rp 50k succeeds without PIN`() = runTest {
        viewModel.setAmount(30000L)
        viewModel.setCategory("Operasional")
        viewModel.setReason("Beli es batu kristal")

        viewModel.submitCashOut()

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertNull(state.errorMessage)
        assertFalse(state.isManagerPinDialogOpen)

        // Verifikasi tercatat di repositori
        assertEquals(30000L, fakeRepository.lastCashOutAmount)
        assertEquals("Operasional", fakeRepository.lastCashOutCategory)
        assertEquals("Beli es batu kristal", fakeRepository.lastCashOutReason)
        assertNull(fakeRepository.lastCashOutApprovedBy)

        // Verifikasi struk bukti kas keluar dicetak
        assertEquals(1, fakePrinter.printedBytes.size)
        val text = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(text.contains("BUKTI KAS KELUAR"))
        assertTrue(text.contains("Beli es batu kristal"))
        assertTrue(text.contains("Rp30.000"))
    }

    @Test
    fun `submitCashOut over Rp 50k requires photo receipt`() {
        viewModel.setAmount(75000L)
        viewModel.setCategory("Bahan baku")
        viewModel.setReason("Beli galon air dan gula pasir")

        viewModel.submitCashOut()

        // Belum ada foto: ditolak
        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isManagerPinDialogOpen)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("foto nota"))
    }

    @Test
    fun `submitCashOut over Rp 50k with photo opens manager PIN dialog`() {
        viewModel.setAmount(75000L)
        viewModel.setCategory("Bahan baku")
        viewModel.setReason("Beli galon air dan gula pasir")
        viewModel.setReceiptPhoto(bitmap = null, filePath = "/storage/emulated/0/receipt_001.jpg")

        viewModel.submitCashOut()

        // Membuka dialog PIN Manager
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `verifyManagerPinAndSubmit with wrong PIN fails and locks after 3 attempts`() {
        viewModel.setAmount(75000L)
        viewModel.setCategory("Bahan baku")
        viewModel.setReason("Beli galon air")
        viewModel.setReceiptPhoto(bitmap = null, filePath = "/path/receipt.jpg")
        viewModel.submitCashOut()

        // Salah 1
        viewModel.verifyManagerPinAndSubmit("1111")
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)
        assertFalse(viewModel.uiState.value.isManagerLocked)

        // Salah 2
        viewModel.verifyManagerPinAndSubmit("2222")
        assertTrue(viewModel.uiState.value.isManagerPinDialogOpen)
        assertFalse(viewModel.uiState.value.isManagerLocked)

        // Salah 3 -> Terkunci (COR-07)
        viewModel.verifyManagerPinAndSubmit("3333")
        assertTrue(viewModel.uiState.value.isManagerLocked)
        assertTrue(viewModel.uiState.value.managerPinErrorMessage!!.contains("terkunci"))

        // PIN benar pun ditolak saat terkunci
        viewModel.verifyManagerPinAndSubmit("4444")
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `verifyManagerPinAndSubmit with correct PIN succeeds and records approvedBy`() = runTest {
        viewModel.setAmount(75000L)
        viewModel.setCategory("Bahan baku")
        viewModel.setReason("Beli galon air")
        viewModel.setReceiptPhoto(bitmap = null, filePath = "/path/receipt.jpg")
        viewModel.submitCashOut()

        viewModel.verifyManagerPinAndSubmit("4444")

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertFalse(state.isManagerPinDialogOpen)

        assertEquals(75000L, fakeRepository.lastCashOutAmount)
        assertEquals("u-manager-1", fakeRepository.lastCashOutApprovedBy)

        // Verifikasi struk dicetak dengan keterangan disetujui
        assertEquals(1, fakePrinter.printedBytes.size)
        val text = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(text.contains("u-manager-1"))
        assertTrue(text.contains("Rp75.000"))
    }

    @Test
    fun `selectTab switches between INPUT and HISTORY tabs correctly`() {
        assertEquals(CashOutTab.INPUT, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(CashOutTab.HISTORY)
        assertEquals(CashOutTab.HISTORY, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(CashOutTab.INPUT)
        assertEquals(CashOutTab.INPUT, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `resetSuccess clears form and resets isSuccess state`() {
        viewModel.setAmount(20000L)
        viewModel.setReason("Beli tisu")
        viewModel.setReceiptPhoto(bitmap = null, filePath = "/path/test.jpg")
        viewModel.submitCashOut()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertEquals(20000L, viewModel.uiState.value.amount)

        viewModel.resetSuccess()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertEquals(0L, viewModel.uiState.value.amount)
        assertEquals("", viewModel.uiState.value.reason)
        assertNull(viewModel.uiState.value.receiptFilePath)
    }

    @Test
    fun `reprintCashOut prints copy receipt with SALINAN marker`() = runTest {
        val cashOut = CashOutEntity(
            id = "csh-999",
            shiftId = "shift-001",
            amount = 35000L,
            category = "Operasional",
            reason = "Beli plastik sampah",
            approvedBy = null,
            createdAt = System.currentTimeMillis()
        )

        viewModel.reprintCashOut(cashOut)

        assertEquals(1, fakePrinter.printedBytes.size)
        val text = String(fakePrinter.printedBytes.first(), Charsets.ISO_8859_1)
        assertTrue(text.contains("*** SALINAN ***"))
        assertTrue(text.contains("Rp35.000"))
        assertTrue(text.contains("Beli plastik sampah"))
    }

    @Test
    fun `cashOutsFlow in repository updates cashOutHistory and totalCashOutAmount in state`() = runTest {
        val item1 = CashOutEntity(
            id = "csh-1",
            shiftId = "shift-001",
            amount = 15000L,
            category = "Bahan baku",
            reason = "Beli jeruk nipis",
            createdAt = System.currentTimeMillis()
        )
        val item2 = CashOutEntity(
            id = "csh-2",
            shiftId = "shift-001",
            amount = 25000L,
            category = "Operasional",
            reason = "Beli baterai remote",
            createdAt = System.currentTimeMillis()
        )

        fakeRepository.cashOutsFlow.value = listOf(item2, item1)

        val state = viewModel.uiState.value
        assertEquals(2, state.cashOutHistory.size)
        assertEquals(40000L, state.totalCashOutAmount)
    }
}
