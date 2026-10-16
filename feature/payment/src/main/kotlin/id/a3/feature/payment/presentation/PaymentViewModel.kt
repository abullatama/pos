package id.a3.feature.payment.presentation

import androidx.lifecycle.ViewModel
import id.a3.core.domain.cash.CashCalculator
import id.a3.feature.payment.repository.PaymentMethod
import id.a3.feature.payment.repository.PaymentRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaymentUiState(
    val totalAmount: Long = 0L,
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
    val cashReceived: Long = 0L,
    val change: Long = 0L,
    val isDp: Boolean = false,
    val isExactPayment: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

class PaymentViewModel(
    private val repository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    fun initialize(totalAmount: Long, isDp: Boolean = false) {
        _uiState.value = PaymentUiState(
            totalAmount = totalAmount,
            isDp = isDp,
            cashReceived = totalAmount,
            change = 0L,
            isExactPayment = true
        )
    }

    fun selectMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(selectedMethod = method)
    }

    fun setCashReceived(amount: Long) {
        val total = _uiState.value.totalAmount
        if (amount >= total) {
            val change = CashCalculator.calculateChange(total = total, tendered = amount)
            _uiState.value = _uiState.value.copy(
                cashReceived = amount,
                change = change,
                isExactPayment = amount == total,
                errorMessage = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                cashReceived = amount,
                change = 0L,
                isExactPayment = false,
                errorMessage = "Uang yang diterima kurang dari total tagihan"
            )
        }
    }
}
