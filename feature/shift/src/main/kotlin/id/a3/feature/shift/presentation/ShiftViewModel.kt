package id.a3.feature.shift.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.domain.cash.CashCalculator
import id.a3.core.domain.cash.ShiftCashInput
import id.a3.core.domain.security.ManagerPinVerifier
import id.a3.feature.shift.model.StandardDenominations
import id.a3.feature.shift.repository.ActiveShift
import id.a3.feature.shift.repository.ShiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShiftUiState(
    val currentShift: ActiveShift? = null,
    val denominations: Map<Long, Int> = StandardDenominations.PRESET_200K,
    val openingCashTotal: Long = StandardDenominations.STANDARD_OPENING_CASH,
    val standardOpeningCash: Long = StandardDenominations.STANDARD_OPENING_CASH,
    val differenceReason: String = "",
    val countedCashInput: Long = 0L,
    val expectedCash: Long = 0L,
    val cashDifference: Long = 0L,
    val isOpeningShift: Boolean = false,
    val isClosingShift: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isForceCloseDialogOpen: Boolean = false,
    val forceCloseErrorMessage: String? = null,
    val isManagerLocked: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val isForceClosing: Boolean = false
) {
    val isDifferenceFromStandard: Boolean
        get() = openingCashTotal != standardOpeningCash

    val differenceAmount: Long
        get() = openingCashTotal - standardOpeningCash

    val canSubmitOpening: Boolean
        get() = openingCashTotal > 0L && (!isDifferenceFromStandard || differenceReason.trim().isNotBlank()) && !isOpeningShift
}

class ShiftViewModel(
    private val repository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCurrentShift().collect { shift ->
                _uiState.update { it.copy(currentShift = shift) }
            }
        }
    }

    fun adjustDenominationCount(denomination: Long, delta: Int) {
        _uiState.update { current ->
            val updatedMap = current.denominations.toMutableMap()
            val currentCount = updatedMap[denomination] ?: 0
            val newCount = (currentCount + delta).coerceAtLeast(0)
            if (newCount == 0) {
                updatedMap.remove(denomination)
            } else {
                updatedMap[denomination] = newCount
            }
            val newTotal = StandardDenominations.calculateTotal(updatedMap)
            current.copy(
                denominations = updatedMap,
                openingCashTotal = newTotal,
                error = null
            )
        }
    }

    fun setDenominationCount(denomination: Long, count: Int) {
        _uiState.update { current ->
            val updatedMap = current.denominations.toMutableMap()
            val sanitized = count.coerceAtLeast(0)
            if (sanitized == 0) {
                updatedMap.remove(denomination)
            } else {
                updatedMap[denomination] = sanitized
            }
            val newTotal = StandardDenominations.calculateTotal(updatedMap)
            current.copy(
                denominations = updatedMap,
                openingCashTotal = newTotal,
                error = null
            )
        }
    }

    fun applyStandardPreset() {
        _uiState.update { current ->
            current.copy(
                denominations = StandardDenominations.PRESET_200K,
                openingCashTotal = StandardDenominations.STANDARD_OPENING_CASH,
                differenceReason = "",
                error = null
            )
        }
    }

    fun clearAllDenominations() {
        _uiState.update { current ->
            current.copy(
                denominations = emptyMap(),
                openingCashTotal = 0L,
                error = null
            )
        }
    }

    fun setDifferenceReason(reason: String) {
        _uiState.update { it.copy(differenceReason = reason, error = null) }
    }

    fun openShift(
        cashierId: String,
        cashierName: String,
        businessUnitId: String = "bu-01",
        deviceId: String = "dev-1",
        onSuccess: (ActiveShift) -> Unit = {}
    ) {
        val state = _uiState.value
        if (!state.canSubmitOpening) {
            if (state.isDifferenceFromStandard && state.differenceReason.trim().isBlank()) {
                _uiState.update { it.copy(error = "Kas awal berbeda dari standar. Alasan selisih wajib diisi (SHF-04)") }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOpeningShift = true, error = null) }
            val result = repository.openShift(
                cashierId = cashierId,
                cashierName = cashierName,
                openingCash = state.openingCashTotal,
                breakdown = state.denominations,
                reason = if (state.isDifferenceFromStandard) state.differenceReason.trim() else null,
                businessUnitId = businessUnitId,
                deviceId = deviceId
            )

            if (result.isSuccess) {
                val shift = result.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isOpeningShift = false,
                        isSuccess = true,
                        currentShift = shift
                    )
                }
                onSuccess(shift)
            } else {
                _uiState.update {
                    it.copy(
                        isOpeningShift = false,
                        error = result.exceptionOrNull()?.message ?: "Gagal membuka shift"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun openForceCloseDialog() {
        checkLockoutStatus()
        _uiState.update { it.copy(isForceCloseDialogOpen = true, forceCloseErrorMessage = null) }
    }

    fun closeForceCloseDialog() {
        _uiState.update { it.copy(isForceCloseDialogOpen = false, forceCloseErrorMessage = null) }
    }

    fun verifyManagerPinAndForceClose(
        pin: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        val shift = _uiState.value.currentShift ?: return
        val now = System.currentTimeMillis()

        if (now < isManagerLockedUntilMs) {
            val remainingSec = ((isManagerLockedUntilMs - now) / 1000).coerceAtLeast(1).toInt()
            _uiState.update {
                it.copy(
                    isManagerLocked = true,
                    lockoutRemainingSeconds = remainingSec,
                    forceCloseErrorMessage = "Akun terkunci ($remainingSec dtk)"
                )
            }
            return
        }

        val isCorrect = ManagerPinVerifier.verify(pin)
        if (!isCorrect) {
            failedPinAttempts++
            if (failedPinAttempts >= 3) {
                isManagerLockedUntilMs = now + (300 * 1000L)
                _uiState.update {
                    it.copy(
                        isManagerLocked = true,
                        lockoutRemainingSeconds = 300,
                        forceCloseErrorMessage = "PIN salah 3x. Akun terkunci 5 menit (COR-07)"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(forceCloseErrorMessage = "PIN Manager salah (${3 - failedPinAttempts}x percobaan tersisa)")
                }
            }
            return
        }

        // PIN benar
        failedPinAttempts = 0
        viewModelScope.launch {
            _uiState.update { it.copy(isForceClosing = true, forceCloseErrorMessage = null) }

            val result = repository.forceCloseShift(
                shiftId = shift.shiftId,
                note = note.trim(),
                approvedBy = "u-manager-1"
            )

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isForceClosing = false,
                        isForceCloseDialogOpen = false,
                        currentShift = null
                    )
                }
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isForceClosing = false,
                        forceCloseErrorMessage = result.exceptionOrNull()?.message ?: "Gagal menutup paksa shift"
                    )
                }
            }
        }
    }

    private var failedPinAttempts = 0
    private var isManagerLockedUntilMs = 0L

    private fun checkLockoutStatus() {
        val now = System.currentTimeMillis()
        if (now < isManagerLockedUntilMs) {
            val remainingSec = ((isManagerLockedUntilMs - now) / 1000).coerceAtLeast(1).toInt()
            _uiState.update {
                it.copy(
                    isManagerLocked = true,
                    lockoutRemainingSeconds = remainingSec
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isManagerLocked = false,
                    lockoutRemainingSeconds = 0
                )
            }
        }
    }
}
