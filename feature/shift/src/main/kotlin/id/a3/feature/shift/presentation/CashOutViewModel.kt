package id.a3.feature.shift.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.domain.security.ApprovalResult
import id.a3.core.domain.security.ManagerApprover
import id.a3.core.domain.security.failureMessage
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.CashOutReceiptData
import id.a3.core.printer.format.CashOutReceiptFormatter
import id.a3.feature.shift.repository.ShiftRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CashOutTab {
    INPUT,
    HISTORY
}

val CASHOUT_CATEGORIES = listOf(
    "Bahan baku",
    "Operasional",
    "Perawatan",
    "Kemasan",
    "Lain-lain"
)

data class CashOutUiState(
    val selectedTab: CashOutTab = CashOutTab.INPUT,
    val amount: Long = 0L,
    val category: String = "Bahan baku",
    val reason: String = "",
    val receiptBitmap: Bitmap? = null,
    val receiptFilePath: String? = null,
    val receiptFileId: String? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isManagerPinDialogOpen: Boolean = false,
    val managerPinErrorMessage: String? = null,
    val isManagerLocked: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val activeShiftId: String? = null,
    val cashierName: String = "Kasir",
    val storeName: String = "A3 COFFEESHOP",
    val storeAddress: String = "Jl. Trans Sulawesi Kelurahan Bintauna",
    val storePhone: String = "0823-4778-5986",
    val printStatusMessage: String? = null,
    val cashOutHistory: List<CashOutEntity> = emptyList(),
    val totalCashOutAmount: Long = 0L
) {
    val isPinRequired: Boolean get() = amount > 50_000L
    val isPhotoRequired: Boolean get() = amount > 50_000L

    val isFormValid: Boolean
        get() = amount > 0L && reason.trim().isNotBlank() && (!isPhotoRequired || receiptBitmap != null || receiptFilePath != null)
}

class CashOutViewModel(
    private val repository: ShiftRepository,
    private val printerService: PrinterService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val managerApprover: ManagerApprover? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashOutUiState())
    val uiState: StateFlow<CashOutUiState> = _uiState.asStateFlow()


    init {
        viewModelScope.launch {
            val shift = repository.getActiveShift()
            val shiftId = shift?.shiftId
            _uiState.update {
                it.copy(
                    activeShiftId = shiftId,
                    cashierName = shift?.cashierName ?: "Kasir"
                )
            }
            if (shiftId != null) {
                observeShiftHistory(shiftId)
            }
        }
    }

    private fun observeShiftHistory(shiftId: String) {
        viewModelScope.launch {
            repository.observeCashOutsForShift(shiftId).collect { history ->
                val total = history.sumOf { it.amount }
                _uiState.update {
                    it.copy(
                        cashOutHistory = history,
                        totalCashOutAmount = total
                    )
                }
            }
        }
    }

    fun setStoreProfile(name: String, address: String, phone: String) {
        _uiState.update {
            it.copy(
                storeName = name,
                storeAddress = address,
                storePhone = phone
            )
        }
    }

    fun initializeStore(
        storeName: String,
        storeAddress: String,
        storePhone: String
    ) {
        _uiState.update {
            it.copy(
                storeName = storeName,
                storeAddress = storeAddress,
                storePhone = storePhone
            )
        }
    }

    fun setAmount(amount: Long) {
        _uiState.update { it.copy(amount = amount.coerceAtLeast(0L), errorMessage = null) }
    }

    fun addAmountPreset(delta: Long) {
        _uiState.update { it.copy(amount = (it.amount + delta).coerceAtLeast(0L), errorMessage = null) }
    }

    fun setCategory(category: String) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
    }

    fun setReason(reason: String) {
        _uiState.update { it.copy(reason = reason, errorMessage = null) }
    }

    fun setReceiptPhoto(bitmap: Bitmap?, filePath: String? = null, fileId: String? = null) {
        val effectiveFileId = fileId ?: if (bitmap != null || filePath != null) UUID.randomUUID().toString() else null
        _uiState.update {
            it.copy(
                receiptBitmap = bitmap,
                receiptFilePath = filePath,
                receiptFileId = effectiveFileId,
                errorMessage = null
            )
        }
    }

    fun selectTab(tab: CashOutTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }
    }

    fun resetSuccess() {
        _uiState.update {
            it.copy(
                amount = 0L,
                reason = "",
                receiptBitmap = null,
                receiptFilePath = null,
                receiptFileId = null,
                isSuccess = false,
                errorMessage = null
            )
        }
    }

    fun reprintCashOut(cashOut: CashOutEntity) {
        val state = _uiState.value
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date(cashOut.createdAt))
        val currentTimeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date())
        val receiptData = CashOutReceiptData(
            storeName = state.storeName,
            storeAddress = state.storeAddress,
            storePhone = state.storePhone,
            cashoutId = cashOut.id,
            shiftId = cashOut.shiftId,
            cashierName = state.cashierName,
            occurredAt = dateStr,
            category = cashOut.category,
            reason = cashOut.reason,
            amount = cashOut.amount,
            hasPhotoReceipt = !cashOut.receiptFileId.isNullOrBlank() || !cashOut.receiptFilePath.isNullOrBlank(),
            approvedBy = cashOut.approvedBy,
            isCopy = true,
            reprintInfo = "Dicetak ulang oleh ${state.cashierName} · $currentTimeStr"
        )

        printerService?.let { printer ->
            viewModelScope.launch(ioDispatcher) {
                try {
                    val bytes = CashOutReceiptFormatter.format(receiptData)
                    printer.print(bytes)
                    _uiState.update { it.copy(printStatusMessage = "Salinan bukti kas keluar berhasil dicetak") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Gagal mencetak salinan: ${e.message}") }
                }
            }
        }
    }

    fun clearReceiptPhoto() {
        _uiState.update {
            it.copy(
                receiptBitmap = null,
                receiptFilePath = null,
                receiptFileId = null
            )
        }
    }

    fun closeManagerPinDialog() {
        _uiState.update { it.copy(isManagerPinDialogOpen = false, managerPinErrorMessage = null) }
    }

    fun submitCashOut() {
        val state = _uiState.value
        if (state.amount <= 0L) {
            _uiState.update { it.copy(errorMessage = "Nominal kas keluar harus lebih besar dari Rp 0") }
            return
        }
        if (state.reason.trim().isBlank()) {
            _uiState.update { it.copy(errorMessage = "Alasan pengeluaran kas wajib diisi") }
            return
        }

        // Penegakan KAS-02 & OQ-08
        if (state.amount > 50_000L) {
            if (state.receiptBitmap == null && state.receiptFilePath == null) {
                _uiState.update { it.copy(errorMessage = "Pengeluaran kas di atas Rp 50.000 wajib menyertakan foto nota (KAS-02)") }
                return
            }
            // Wajib otorisasi PIN Manager
            _uiState.update { it.copy(isManagerPinDialogOpen = true, managerPinErrorMessage = null) }
            return
        }

        // Nominal <= 50.000: Kasir boleh tanpa PIN
        executeSaveCashOut(approvedBy = null)
    }

    fun verifyManagerPinAndSubmit(pin: String) {
        viewModelScope.launch {
            // PIN diperiksa terhadap akun manager/owner dari server; identitasnya dikirim sebagai approved_by.
            val verdict = managerApprover?.verify(pin) ?: ApprovalResult.NoApproverAvailable
            if (verdict !is ApprovalResult.Approved) {
                _uiState.update {
                    it.copy(
                        isManagerLocked = verdict is ApprovalResult.Locked,
                        lockoutRemainingSeconds = (verdict as? ApprovalResult.Locked)?.retryAfterSeconds?.toInt() ?: 0,
                        managerPinErrorMessage = verdict.failureMessage()
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isManagerPinDialogOpen = false,
                    managerPinErrorMessage = null,
                    isManagerLocked = false,
                    lockoutRemainingSeconds = 0
                )
            }
            executeSaveCashOut(
                approvedBy = verdict.approval.userId.toString(),
                approvedByName = verdict.approval.name
            )
        }
    }

    private fun executeSaveCashOut(approvedBy: String?, approvedByName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val state = _uiState.value

            val currentShift = repository.getActiveShift()
            val effectiveShiftId = currentShift?.shiftId ?: state.activeShiftId
            val currentDeviceId = currentShift?.deviceId ?: "dev-1"
            val effectiveCashierName = currentShift?.cashierName ?: state.cashierName

            if (state.activeShiftId == null && effectiveShiftId != null) {
                _uiState.update { it.copy(activeShiftId = effectiveShiftId, cashierName = effectiveCashierName) }
                observeShiftHistory(effectiveShiftId)
            }

            val result = repository.recordCashOut(
                amount = state.amount,
                category = state.category,
                reason = state.reason.trim(),
                receiptFileId = state.receiptFileId,
                receiptFilePath = state.receiptFilePath,
                approvedBy = approvedBy,
                deviceId = currentDeviceId
            )

            if (result.isSuccess) {
                // Cetak struk bukti kas keluar 58 mm
                val cashoutId = UUID.randomUUID().toString()
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date())
                val receiptData = CashOutReceiptData(
                    storeName = state.storeName,
                    storeAddress = state.storeAddress,
                    storePhone = state.storePhone,
                    cashoutId = cashoutId,
                    shiftId = effectiveShiftId ?: "shift-1",
                    cashierName = effectiveCashierName,
                    occurredAt = dateStr,
                    category = state.category,
                    reason = state.reason.trim(),
                    amount = state.amount,
                    hasPhotoReceipt = state.receiptBitmap != null || state.receiptFilePath != null,
                    approvedBy = approvedByName ?: approvedBy
                )

                printerService?.let { printer ->
                    try {
                        val bytes = CashOutReceiptFormatter.format(receiptData)
                        withContext(ioDispatcher) {
                            printer.print(bytes)
                        }
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSuccess = true,
                        printStatusMessage = "Bukti kas keluar berhasil dicetak"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Gagal mencatat kas keluar"
                    )
                }
            }
        }
    }
}
