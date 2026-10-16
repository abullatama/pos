package id.a3.feature.shift.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.domain.cash.CashCalculator
import id.a3.core.domain.cash.ShiftCashInput
import id.a3.feature.shift.repository.ActiveShift
import id.a3.feature.shift.repository.ShiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShiftUiState(
    val currentShift: ActiveShift? = null,
    val openingCashInput: Long = 200000L, // Bawaan OQ-06 Rp200.000
    val countedCashInput: Long = 0L,
    val expectedCash: Long = 0L,
    val cashDifference: Long = 0L,
    val isOpeningShift: Boolean = false,
    val isClosingShift: Boolean = false,
    val error: String? = null
)

class ShiftViewModel(
    private val repository: ShiftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftUiState())
    val uiState: StateFlow<ShiftUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCurrentShift().collect { shift ->
                _uiState.value = _uiState.value.copy(currentShift = shift)
            }
        }
    }

    fun setCountedCash(
        amount: Long,
        cashSales: Long = 0L,
        cashDpAndSettlement: Long = 0L,
        cashRefunds: Long = 0L,
        cashOuts: Long = 0L
    ) {
        val opening = _uiState.value.currentShift?.openingCash ?: 0L
        val expected = CashCalculator.calculateExpectedCash(
            ShiftCashInput(
                openingCash = opening,
                cashSales = cashSales,
                cashDpAndSettlement = cashDpAndSettlement,
                cashRefunds = cashRefunds,
                cashOuts = cashOuts
            )
        )
        val diff = amount - expected

        _uiState.value = _uiState.value.copy(
            countedCashInput = amount,
            expectedCash = expected,
            cashDifference = diff
        )
    }

    fun openShift(cashierId: String, cashierName: String, cash: Long, reason: String? = null) {
        viewModelScope.launch {
            val result = repository.openShift(cashierId, cashierName, cash, reason)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = result.exceptionOrNull()?.message)
            }
        }
    }
}
