package id.a3.feature.payment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.domain.cash.CashCalculator
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.CustomerReceiptData
import id.a3.core.printer.format.CustomerReceiptFormatter
import id.a3.core.printer.format.CustomerReceiptLine
import id.a3.feature.payment.repository.PaymentMethod
import id.a3.feature.payment.repository.PaymentRepository
import id.a3.feature.payment.repository.PaymentTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PaymentStep {
    METHOD_SELECTION, // S05: Pilih Tunai / QRIS Statis
    CASH_INPUT,       // S06: Uang Diterima & Kembalian
    QRIS_CONFIRM,     // S07: Petunjuk Merchant & Konfirmasi Nominal
    SUCCESS           // S08: Pembayaran Berhasil & Cetak Struk
}

data class PaymentUiState(
    val orderId: String = "",
    val orderDisplayNumber: String = "C1-0001",
    val tableOrQueueLabel: String? = null,
    val cashierName: String = "Kasir",
    val storeName: String = "AA COFFEESHOP",
    val storeAddress: String = "Jl. Kopi Pilihan No. 3",
    val storePhone: String = "0812-3456-7890",
    val totalAmount: Long = 0L,
    val isDp: Boolean = false,
    val currentStep: PaymentStep = PaymentStep.METHOD_SELECTION,
    val selectedMethod: PaymentMethod = PaymentMethod.CASH,
    val cashReceived: Long = 0L,
    val change: Long = 0L,
    val isExactPayment: Boolean = false,
    val quickCashOptions: List<Long> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val printStatusMessage: String? = null,
    val orderDetail: OrderWithLines? = null
)

class PaymentViewModel(
    private val repository: PaymentRepository,
    val printerService: PrinterService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    fun initializeWithOrder(
        orderId: String,
        totalAmount: Long,
        displayNumber: String = "C1-0001",
        tableOrQueueLabel: String? = null,
        cashierName: String = "Kasir",
        storeName: String = "AA COFFEESHOP",
        storeAddress: String = "Jl. Kopi Pilihan No. 3",
        storePhone: String = "0812-3456-7890",
        isDp: Boolean = false
    ) {
        val quickOptions = generateQuickCashOptions(totalAmount)
        _uiState.value = PaymentUiState(
            orderId = orderId,
            orderDisplayNumber = displayNumber,
            tableOrQueueLabel = tableOrQueueLabel,
            cashierName = cashierName,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            totalAmount = totalAmount,
            isDp = isDp,
            currentStep = PaymentStep.METHOD_SELECTION,
            selectedMethod = PaymentMethod.CASH,
            cashReceived = totalAmount,
            change = 0L,
            isExactPayment = true,
            quickCashOptions = quickOptions
        )

        // Muat detail item pesanan untuk pencetakan struk
        viewModelScope.launch {
            val orderWithLines = repository.getOrderWithLines(orderId)
            if (orderWithLines != null) {
                _uiState.value = _uiState.value.copy(
                    orderDetail = orderWithLines,
                    orderDisplayNumber = orderWithLines.order.displayNumber,
                    tableOrQueueLabel = tableOrQueueLabel ?: orderWithLines.order.tableLabel
                )
            }
        }
    }

    fun selectMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(
            selectedMethod = method,
            errorMessage = null
        )
    }

    fun proceedToMethodInput() {
        val next = when (_uiState.value.selectedMethod) {
            PaymentMethod.CASH -> PaymentStep.CASH_INPUT
            PaymentMethod.STATIC_QRIS -> PaymentStep.QRIS_CONFIRM
        }
        _uiState.value = _uiState.value.copy(
            currentStep = next,
            errorMessage = null
        )
    }

    fun backToMethodSelection() {
        _uiState.value = _uiState.value.copy(
            currentStep = PaymentStep.METHOD_SELECTION,
            errorMessage = null
        )
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
                errorMessage = "Uang diterima kurang dari total tagihan"
            )
        }
    }

    fun applyQuickCash(amount: Long) {
        setCashReceived(amount)
    }

    fun processPayment() {
        val current = _uiState.value
        if (current.selectedMethod == PaymentMethod.CASH && current.cashReceived < current.totalAmount) {
            _uiState.value = current.copy(errorMessage = "Uang diterima kurang dari total tagihan")
            return
        }

        _uiState.value = current.copy(isProcessing = true, errorMessage = null)

        viewModelScope.launch {
            val transaction = PaymentTransaction(
                orderId = current.orderId,
                method = current.selectedMethod,
                totalAmount = current.totalAmount,
                cashReceived = current.cashReceived,
                change = current.change,
                isDp = current.isDp
            )

            val result = repository.processPayment(transaction)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    currentStep = PaymentStep.SUCCESS
                )
                // Otomatis cetak struk pertama
                printCustomerReceipt(isCopy = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Gagal memproses pembayaran"
                )
            }
        }
    }

    fun printCustomerReceipt(isCopy: Boolean = false) {
        val state = _uiState.value
        val orderWithLines = state.orderDetail
        val currentTimeStr = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date())

        val receiptLines = orderWithLines?.lines?.map { line ->
            CustomerReceiptLine(
                name = line.nameSnapshot,
                quantity = line.quantity,
                unitPrice = line.unitPriceSnapshot,
                lineTotal = line.unitPriceSnapshot * line.quantity,
                optionsOrNote = line.note
            )
        } ?: listOf(
            CustomerReceiptLine(
                name = "Pesanan #${state.orderDisplayNumber}",
                quantity = 1,
                unitPrice = state.totalAmount,
                lineTotal = state.totalAmount
            )
        )

        val receiptData = CustomerReceiptData(
            storeName = state.storeName,
            storeAddress = state.storeAddress,
            storePhone = state.storePhone,
            orderDisplayNumber = state.orderDisplayNumber,
            tableOrQueueLabel = state.tableOrQueueLabel,
            cashierName = state.cashierName,
            occurredAt = currentTimeStr,
            lines = receiptLines,
            subtotal = orderWithLines?.order?.subtotal ?: state.totalAmount,
            discount = orderWithLines?.order?.discount ?: 0L,
            serviceCharge = orderWithLines?.order?.serviceCharge ?: 0L,
            tax = orderWithLines?.order?.tax ?: 0L,
            rounding = orderWithLines?.order?.rounding ?: 0L,
            total = state.totalAmount,
            paymentMethodName = state.selectedMethod.label + if (state.isDp) " (DP)" else "",
            tendered = if (state.selectedMethod == PaymentMethod.CASH) state.cashReceived else null,
            change = state.change,
            isCopy = isCopy,
            reprintInfo = if (isCopy) "Dicetak ulang oleh ${state.cashierName} · $currentTimeStr" else null
        )

        val bytes = CustomerReceiptFormatter.format(receiptData)
        viewModelScope.launch {
            val printRes = printerService?.print(bytes)
            _uiState.value = _uiState.value.copy(
                printStatusMessage = if (printRes?.isSuccess == true) {
                    if (isCopy) "Salinan struk berhasil dicetak" else "Struk berhasil dicetak"
                } else {
                    "Printer tidak terhubung atau offline"
                }
            )
        }
    }

    companion object {
        fun generateQuickCashOptions(total: Long): List<Long> {
            if (total <= 0L) return listOf(10000L, 20000L, 50000L, 100000L)
            val options = linkedSetOf<Long>()
            options.add(total) // Uang pas

            val ceil10k = ((total + 9999L) / 10000L) * 10000L
            if (ceil10k > total) options.add(ceil10k)

            val ceil50k = ((total + 49999L) / 50000L) * 50000L
            if (ceil50k > total) options.add(ceil50k)

            val ceil100k = ((total + 99999L) / 100000L) * 100000L
            if (ceil100k > total) options.add(ceil100k)

            listOf(50000L, 100000L, 200000L).forEach {
                if (it > total) options.add(it)
            }

            return options.take(4)
        }
    }
}
