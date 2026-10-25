package id.a3.feature.shift.presentation

import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.feature.shift.model.StandardDenominations
import id.a3.feature.shift.repository.ActiveShift
import id.a3.feature.shift.repository.ShiftRepository
import id.a3.feature.shift.repository.ShiftSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

class FakeShiftRepository : ShiftRepository {
    val currentShiftFlow = MutableStateFlow<ActiveShift?>(null)
    val cashOutsFlow = MutableStateFlow<List<CashOutEntity>>(emptyList())
    var lastOpenedShift: ActiveShift? = null
    var shouldFailOpen: Boolean = false

    override fun observeCurrentShift(): Flow<ActiveShift?> = currentShiftFlow.asStateFlow()
    override fun observeCashOutsForShift(shiftId: String): Flow<List<CashOutEntity>> = cashOutsFlow.asStateFlow()

    override suspend fun getActiveShift(): ActiveShift? = currentShiftFlow.value

    override suspend fun openShift(
        cashierId: String,
        cashierName: String,
        openingCash: Long,
        breakdown: Map<Long, Int>,
        reason: String?,
        businessUnitId: String,
        deviceId: String
    ): Result<ActiveShift> {
        if (shouldFailOpen) {
            return Result.failure(IllegalStateException("Simulated database failure"))
        }

        val shift = ActiveShift(
            shiftId = UUID.randomUUID().toString(),
            businessUnitId = businessUnitId,
            deviceId = deviceId,
            cashierId = cashierId,
            cashierName = cashierName,
            openedAt = java.time.Instant.now().toString(),
            openingCash = openingCash,
            openingReason = reason
        )
        lastOpenedShift = shift
        currentShiftFlow.value = shift
        return Result.success(shift)
    }

    var mockSummary: ShiftSummary? = null
    var lastClosedCountedCash: Long? = null
    var lastClosedCashLeft: Long? = null
    var lastClosedCashDeposited: Long? = null
    var lastClosedCashTakenBy: String? = null
    var lastClosedReason: String? = null
    var lastCashOutAmount: Long? = null
    var lastCashOutCategory: String? = null
    var lastCashOutReason: String? = null
    var lastCashOutApprovedBy: String? = null

    override suspend fun recordCashOut(
        amount: Long,
        category: String,
        reason: String,
        receiptFileId: String?,
        receiptFilePath: String?,
        approvedBy: String?,
        deviceId: String
    ): Result<Unit> {
        lastCashOutAmount = amount
        lastCashOutCategory = category
        lastCashOutReason = reason
        lastCashOutApprovedBy = approvedBy
        val entity = CashOutEntity(
            id = java.util.UUID.randomUUID().toString(),
            shiftId = currentShiftFlow.value?.shiftId ?: "shift-1",
            amount = amount,
            category = category,
            reason = reason,
            receiptFileId = receiptFileId,
            receiptFilePath = receiptFilePath,
            approvedBy = approvedBy
        )
        cashOutsFlow.value = listOf(entity) + cashOutsFlow.value
        return Result.success(Unit)
    }

    override suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary> {
        val summary = mockSummary ?: ShiftSummary(
            shift = currentShiftFlow.value ?: ActiveShift(shiftId = shiftId, cashierId = "c1", cashierName = "Kasir", openedAt = "2026-10-19T08:00:00Z", openingCash = 200000L),
            grossSales = 600000L,
            totalDiscount = 0L,
            totalRefund = 0L,
            netSales = 600000L,
            transactionCount = 10,
            cashSales = 500000L,
            cashDpAndSettlement = 100000L,
            qrisSales = 100000L,
            cashOuts = 50000L,
            expectedCash = 725000L,
            qrisPayments = emptyList(),
            openOrderIds = emptyList()
        )
        return Result.success(summary)
    }

    override suspend fun closeShift(
        shiftId: String,
        countedCash: Long,
        differenceReason: String?,
        cashLeft: Long,
        cashDeposited: Long,
        cashTakenBy: String?,
        qrisCheckedPaymentIds: List<String>,
        handoverOrderIds: List<String>
    ): Result<Unit> {
        lastClosedCountedCash = countedCash
        lastClosedCashLeft = cashLeft
        lastClosedCashDeposited = cashDeposited
        lastClosedCashTakenBy = cashTakenBy
        lastClosedReason = differenceReason
        currentShiftFlow.value = null
        return Result.success(Unit)
    }

    var lastForceClosedShiftId: String? = null
    var lastForceClosedNote: String? = null
    var lastForceClosedApprovedBy: String? = null

    override suspend fun forceCloseShift(
        shiftId: String,
        note: String,
        approvedBy: String
    ): Result<Unit> {
        lastForceClosedShiftId = shiftId
        lastForceClosedNote = note
        lastForceClosedApprovedBy = approvedBy
        currentShiftFlow.value = null
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeShiftRepository
    private lateinit var viewModel: ShiftViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeShiftRepository()
        viewModel = ShiftViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state contains standard 200k preset and isStandardAmount true`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(200000L, state.openingCashTotal)
        assertEquals(200000L, state.standardOpeningCash)
        assertFalse(state.isDifferenceFromStandard)
        assertEquals(0L, state.differenceAmount)
        assertTrue(state.canSubmitOpening)
    }

    @Test
    fun `adjustDenominationCount updates breakdown and total opening cash accurately`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Tambah 1 lembar 100.000 (total dari 200.000 menjadi 300.000)
        viewModel.adjustDenominationCount(100000L, 1)

        val state = viewModel.uiState.value
        assertEquals(300000L, state.openingCashTotal)
        assertTrue(state.isDifferenceFromStandard)
        assertEquals(100000L, state.differenceAmount)
        // Belum isi alasan -> canSubmitOpening false (SHF-04)
        assertFalse(state.canSubmitOpening)

        collectJob.cancel()
    }

    @Test
    fun `openShift with standard 200k succeeds without reason`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.openShift(
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso",
            businessUnitId = "bu-cafe-01",
            deviceId = "dev-cafe-1"
        )

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertNotNull(state.currentShift)
        assertEquals(200000L, state.currentShift?.openingCash)
        assertNull(state.currentShift?.openingReason)

        assertEquals(200000L, fakeRepository.lastOpenedShift?.openingCash)
        assertEquals("Budi Santoso", fakeRepository.lastOpenedShift?.cashierName)

        collectJob.cancel()
    }

    @Test
    fun `openShift with non-standard amount without reason fails validation`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        // Ubah total jadi 150.000 tanpa mengisi alasan
        viewModel.clearAllDenominations()
        viewModel.setDenominationCount(50000L, 3) // 150.000

        assertEquals(150000L, viewModel.uiState.value.openingCashTotal)
        assertTrue(viewModel.uiState.value.isDifferenceFromStandard)

        viewModel.openShift(
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso"
        )

        // Verifikasi ditolak karena tidak ada alasan
        assertFalse(viewModel.uiState.value.isSuccess)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.error!!.contains("SHF-04"))
        assertNull(fakeRepository.lastOpenedShift)

        collectJob.cancel()
    }

    @Test
    fun `openShift with non-standard amount and valid reason succeeds`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.clearAllDenominations()
        viewModel.setDenominationCount(50000L, 3) // 150.000
        viewModel.setDifferenceReason("Uang kembalian kurang dari shift kemarin")

        assertTrue(viewModel.uiState.value.canSubmitOpening)

        viewModel.openShift(
            cashierId = "u-cashier-1",
            cashierName = "Budi Santoso"
        )

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertNotNull(state.currentShift)
        assertEquals(150000L, state.currentShift?.openingCash)
        assertEquals("Uang kembalian kurang dari shift kemarin", state.currentShift?.openingReason)

        collectJob.cancel()
    }

    @Test
    fun `applyStandardPreset restores 200k and resets reason`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.clearAllDenominations()
        assertEquals(0L, viewModel.uiState.value.openingCashTotal)

        viewModel.applyStandardPreset()

        val state = viewModel.uiState.value
        assertEquals(200000L, state.openingCashTotal)
        assertFalse(state.isDifferenceFromStandard)
        assertEquals("", state.differenceReason)
        assertNull(state.error)

        collectJob.cancel()
    }

    @Test
    fun `force close dialog opens and closes properly`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.isForceCloseDialogOpen)
        viewModel.openForceCloseDialog()
        assertTrue(viewModel.uiState.value.isForceCloseDialogOpen)
        viewModel.closeForceCloseDialog()
        assertFalse(viewModel.uiState.value.isForceCloseDialogOpen)

        collectJob.cancel()
    }

    @Test
    fun `verifyManagerPinAndForceClose with wrong PIN fails and locks after 3 attempts`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        fakeRepository.currentShiftFlow.value = ActiveShift(
            shiftId = "shift-001",
            cashierId = "c1",
            cashierName = "Budi Kasir",
            openedAt = "2026-10-19T08:00:00Z",
            openingCash = 200000L
        )

        viewModel.openForceCloseDialog()

        // Salah 1
        viewModel.verifyManagerPinAndForceClose("1111", "Kasir tidak hadir")
        assertTrue(viewModel.uiState.value.isForceCloseDialogOpen)
        assertFalse(viewModel.uiState.value.isManagerLocked)

        // Salah 2
        viewModel.verifyManagerPinAndForceClose("2222", "Kasir tidak hadir")
        assertTrue(viewModel.uiState.value.isForceCloseDialogOpen)
        assertFalse(viewModel.uiState.value.isManagerLocked)

        // Salah 3 -> Terkunci
        viewModel.verifyManagerPinAndForceClose("3333", "Kasir tidak hadir")
        assertTrue(viewModel.uiState.value.isManagerLocked)
        assertTrue(viewModel.uiState.value.forceCloseErrorMessage!!.contains("terkunci"))

        // PIN benar pun ditolak saat terkunci
        viewModel.verifyManagerPinAndForceClose("4444", "Kasir tidak hadir")
        assertNotNull(viewModel.uiState.value.currentShift)

        collectJob.cancel()
    }

    @Test
    fun `verifyManagerPinAndForceClose with correct PIN succeeds and resets shift`() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }
        fakeRepository.currentShiftFlow.value = ActiveShift(
            shiftId = "shift-001",
            cashierId = "c1",
            cashierName = "Budi Kasir",
            openedAt = "2026-10-19T08:00:00Z",
            openingCash = 200000L
        )

        var callbackCalled = false
        viewModel.openForceCloseDialog()
        viewModel.verifyManagerPinAndForceClose(
            pin = "4444",
            note = "Kasir sakit mendadak, shift ditutup paksa",
            onSuccess = { callbackCalled = true }
        )

        assertTrue(callbackCalled)
        assertFalse(viewModel.uiState.value.isForceCloseDialogOpen)
        assertNull(viewModel.uiState.value.currentShift)

        // Verifikasi repo menerima data force close
        assertEquals("shift-001", fakeRepository.lastForceClosedShiftId)
        assertEquals("Kasir sakit mendadak, shift ditutup paksa", fakeRepository.lastForceClosedNote)
        assertEquals("u-manager-1", fakeRepository.lastForceClosedApprovedBy)

        collectJob.cancel()
    }
}
