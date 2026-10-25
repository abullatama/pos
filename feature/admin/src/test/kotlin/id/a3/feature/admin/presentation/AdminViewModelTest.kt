package id.a3.feature.admin.presentation

import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.ShiftEntity
import id.a3.core.domain.security.ManagerPinVerifier
import id.a3.feature.admin.update.UpdatePreconditionValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
class AdminViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeShiftDao : ShiftDao {
        override suspend fun insertShift(shift: ShiftEntity) {}
        override suspend fun updateShift(shift: ShiftEntity) {}
        override suspend fun getActiveShift(): ShiftEntity? = null
        override fun observeActiveShift(): Flow<ShiftEntity?> = emptyFlow()
        override suspend fun getShiftById(shiftId: String): ShiftEntity? = null
        override suspend fun getLatestShift(): ShiftEntity? = null
        override fun observeAllShifts(): Flow<List<ShiftEntity>> = emptyFlow()
    }

    private class FakeOutboxDao : OutboxDao {
        override suspend fun insert(outbox: OutboxEntity) {}
        override suspend fun getOutboxByStatus(status: OutboxStatus, limit: Int): List<OutboxEntity> = emptyList()
        override suspend fun getByBundleId(bundleId: String): OutboxEntity? = null
        override suspend fun markSent(bundleId: String, flagsJson: String?, updatedAt: Long) {}
        override suspend fun markFailed(bundleId: String, error: String, reasonCode: String?, updatedAt: Long) {}
        override suspend fun incrementAttempts(bundleId: String, error: String?, updatedAt: Long) {}
        override suspend fun getFailedOutbox(): List<OutboxEntity> = emptyList()
        override fun observeFailedOutbox(): Flow<List<OutboxEntity>> = emptyFlow()
        override fun observeFailedCount(): Flow<Int> = emptyFlow()
        override suspend fun retryFailedBundle(bundleId: String, updatedAt: Long) {}
        override suspend fun retryAllFailed(updatedAt: Long) {}
        override suspend fun deleteSentOlderThan(olderThan: Long): Int = 0
        override fun observePendingCount(): Flow<Int> = emptyFlow()
        override suspend fun getUnsyncedCount(): Int = 0
        override fun observeUnsyncedCount(): Flow<Int> = emptyFlow()
    }

    private lateinit var shiftDao: FakeShiftDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var updateValidator: UpdatePreconditionValidator
    private lateinit var viewModel: AdminViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        shiftDao = FakeShiftDao()
        outboxDao = FakeOutboxDao()
        updateValidator = UpdatePreconditionValidator(shiftDao, outboxDao)
        viewModel = AdminViewModel(
            shiftDao = shiftDao,
            outboxDao = outboxDao,
            updateValidator = updateValidator,
            printerService = null,
            deviceId = "DEV-TEST-01",
            businessUnitId = "CAFE-01",
            appVersion = "1.0.0",
            ownerPinHash = ManagerPinVerifier.hashPin("9999")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is locked and enteredPin is empty`() {
        val state = viewModel.uiState.value
        assertFalse(state.isOwnerUnlocked)
        assertEquals("", state.enteredPin)
        assertNull(state.pinError)
        assertFalse(state.isPinLocked)
        assertEquals(0, state.lockoutRemainingSeconds)
    }

    @Test
    fun `onPinDigit updates enteredPin incrementally`() {
        viewModel.onPinDigit("1")
        assertEquals("1", viewModel.uiState.value.enteredPin)

        viewModel.onPinDigit("2")
        assertEquals("12", viewModel.uiState.value.enteredPin)

        viewModel.onPinDigit("3")
        assertEquals("123", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun `onPinBackspace removes last digit`() {
        viewModel.onPinDigit("5")
        viewModel.onPinDigit("6")
        assertEquals("56", viewModel.uiState.value.enteredPin)

        viewModel.onPinBackspace()
        assertEquals("5", viewModel.uiState.value.enteredPin)

        viewModel.onPinBackspace()
        assertEquals("", viewModel.uiState.value.enteredPin)

        // Backspace pada kondisi kosong tidak crash
        viewModel.onPinBackspace()
        assertEquals("", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun `onPinClear clears all entered digits and resets error`() {
        viewModel.onPinDigit("8")
        viewModel.onPinDigit("8")
        viewModel.onPinDigit("8")
        assertEquals("888", viewModel.uiState.value.enteredPin)

        viewModel.onPinClear()
        assertEquals("", viewModel.uiState.value.enteredPin)
        assertNull(viewModel.uiState.value.pinError)
    }

    @Test
    fun `entering correct PIN (9999) unlocks admin successfully`() {
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")

        val state = viewModel.uiState.value
        assertTrue(state.isOwnerUnlocked)
        assertEquals("", state.enteredPin)
        assertNull(state.pinError)
    }

    @Test
    fun `entering incorrect PIN fails and reduces remaining attempts`() {
        viewModel.onPinDigit("1")
        viewModel.onPinDigit("2")
        viewModel.onPinDigit("3")
        viewModel.onPinDigit("4")

        val state = viewModel.uiState.value
        assertFalse(state.isOwnerUnlocked)
        assertEquals("", state.enteredPin)
        assertNotNull(state.pinError)
        assertTrue(state.pinError!!.contains("Sisa kesempatan: 2 kali"))
    }

    @Test
    fun `failing 3 consecutive PIN attempts triggers lockout and disables keypad inputs`() = runTest {
        // Percobaan 1
        viewModel.onPinDigit("1")
        viewModel.onPinDigit("1")
        viewModel.onPinDigit("1")
        viewModel.onPinDigit("1")
        assertFalse(viewModel.uiState.value.isPinLocked)

        // Percobaan 2
        viewModel.onPinDigit("2")
        viewModel.onPinDigit("2")
        viewModel.onPinDigit("2")
        viewModel.onPinDigit("2")
        assertFalse(viewModel.uiState.value.isPinLocked)

        // Percobaan 3 -> Lockout
        viewModel.onPinDigit("3")
        viewModel.onPinDigit("3")
        viewModel.onPinDigit("3")
        viewModel.onPinDigit("3")

        val state = viewModel.uiState.value
        assertTrue(state.isPinLocked)
        assertTrue(state.lockoutRemainingSeconds > 0)
        assertNotNull(state.pinError)

        // Saat terkunci, pengetikan digit maupun clear diabaikan
        viewModel.onPinDigit("9")
        assertEquals("", viewModel.uiState.value.enteredPin)

        viewModel.onPinBackspace()
        assertEquals("", viewModel.uiState.value.enteredPin)

        viewModel.onPinClear()
        assertEquals("", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun `lockAdmin resets unlocked state`() {
        // Buka dulu dengan PIN benar
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")
        viewModel.onPinDigit("9")
        assertTrue(viewModel.uiState.value.isOwnerUnlocked)

        // Kunci kembali
        viewModel.lockAdmin()
        assertFalse(viewModel.uiState.value.isOwnerUnlocked)
        assertEquals("", viewModel.uiState.value.enteredPin)
    }
}
