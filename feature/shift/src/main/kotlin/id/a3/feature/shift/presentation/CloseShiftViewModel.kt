package id.a3.feature.shift.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.domain.security.ManagerPinVerifier
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.ZReportData
import id.a3.core.printer.format.ZReportFormatter
import id.a3.feature.shift.model.StandardDenominations
import id.a3.feature.shift.repository.ActiveShift
import id.a3.feature.shift.repository.ShiftRepository
import id.a3.feature.shift.repository.ShiftSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class CloseShiftStep {
    STEP_1_BLIND_COUNT,    // S09: Hitung Kas Buta per Pecahan (angka sistem disembunyikan 100%)
    STEP_2_DIFF_AND_QRIS,   // S10: Selisih vs Kas Seharusnya & Ceklis QRIS Statis
    STEP_3_ALLOCATION_AND_Z // S11: Alokasi Kas (Laci & Setor) & Pratinjau Laporan Z
}

data class CloseShiftUiState(
    val currentStep: CloseShiftStep = CloseShiftStep.STEP_1_BLIND_COUNT,
    val summary: ShiftSummary? = null,
    val isLoading: Boolean = false,
    val isClosing: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,

    // Step 1: Blind Count (TUP-02)
    val denominations: Map<Long, Int> = emptyMap(),
    val countedCash: Long = 0L,
    val isStep1ConfirmationDialogOpen: Boolean = false,

    // Step 2: QRIS Check & Difference (TUP-03, TUP-04)
    val checkedQrisPaymentIds: Set<String> = emptySet(),
    val differenceReason: String = "",

    // Step 3: Allocation (TUP-06)
    val cashLeft: Long = 200000L,
    val cashDeposited: Long = 0L,
    val cashTakenBy: String = "",

    // Cetak Laporan Z (TUP-05)
    val storeName: String = "AA COFFEESHOP",
    val storeAddress: String = "Jl. Kopi Pilihan No. 3",
    val storePhone: String = "0812-3456-7890",
    val printStatusMessage: String? = null,

    // Proteksi Hitung Ulang Kasir (TUP-03: Kunci Hitungan Buta & Wajib PIN Manager)
    val isRecountPinDialogOpen: Boolean = false,
    val recountPinError: String? = null,
    val isRecountLocked: Boolean = false,
    val recountLockoutSeconds: Int = 0
) {
    val expectedCash: Long get() = summary?.expectedCash ?: 0L
    val cashDifference: Long get() = countedCash - expectedCash

    // Toleransi selisih OQ-07: Rp 2.000
    val isDifferenceOverTolerance: Boolean
        get() = abs(cashDifference) > 2000L

    val isStep2Valid: Boolean
        get() = !isDifferenceOverTolerance || differenceReason.trim().isNotBlank()

    val totalAllocated: Long
        get() = cashLeft + cashDeposited

    // Validasi alokasi TUP-06: cashLeft + cashDeposited == countedCash
    val isAllocationBalanced: Boolean
        get() = totalAllocated == countedCash

    val canProceedToClose: Boolean
        get() = isAllocationBalanced && !isClosing && (cashDeposited == 0L || cashTakenBy.trim().isNotBlank())

    val cashDepositedOverZeroWithoutTaker: Boolean
        get() = cashDeposited > 0L && cashTakenBy.trim().isBlank()
}

class CloseShiftViewModel(
    private val repository: ShiftRepository,
    private val printerService: PrinterService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloseShiftUiState())
    val uiState: StateFlow<CloseShiftUiState> = _uiState.asStateFlow()

    fun resetState() {
        failedRecountPinAttempts = 0
        recountLockedUntilMs = 0L
        _uiState.value = CloseShiftUiState()
    }

    fun initializeWithShift(
        shiftId: String,
        storeName: String = "AA COFFEESHOP",
        storeAddress: String = "Jl. Kopi Pilihan No. 3",
        storePhone: String = "0812-3456-7890"
    ) {
        failedRecountPinAttempts = 0
        recountLockedUntilMs = 0L
        _uiState.value = CloseShiftUiState(
            currentStep = CloseShiftStep.STEP_1_BLIND_COUNT,
            isLoading = true,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            error = null
        )

        viewModelScope.launch {
            val result = repository.getShiftSummary(shiftId)
            if (result.isSuccess) {
                val summary = result.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        // Tandai otomatis seluruh QRIS sebagai default atau kosong
                        checkedQrisPaymentIds = emptySet()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Gagal memuat ringkasan shift"
                    )
                }
            }
        }
    }

    // --- STEP 1: BLIND COUNT (S09) ---

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

            // Auto-calculate default alokasi untuk Step 3
            val defaultCashLeft = 200000L.coerceAtMost(newTotal)
            val defaultDeposited = (newTotal - defaultCashLeft).coerceAtLeast(0L)

            current.copy(
                denominations = updatedMap,
                countedCash = newTotal,
                cashLeft = defaultCashLeft,
                cashDeposited = defaultDeposited,
                error = null
            )
        }
    }

    fun clearAllDenominations() {
        _uiState.update { current ->
            current.copy(
                denominations = emptyMap(),
                countedCash = 0L,
                cashLeft = 0L,
                cashDeposited = 0L
            )
        }
    }

    fun openStep1ConfirmationDialog() {
        if (_uiState.value.countedCash > 0L) {
            _uiState.update { it.copy(isStep1ConfirmationDialogOpen = true, error = null) }
        }
    }

    fun closeStep1ConfirmationDialog() {
        _uiState.update { it.copy(isStep1ConfirmationDialogOpen = false) }
    }

    fun proceedToStep2() {
        _uiState.update {
            it.copy(
                currentStep = CloseShiftStep.STEP_2_DIFF_AND_QRIS,
                isStep1ConfirmationDialogOpen = false,
                error = null
            )
        }
    }

    // --- STEP 2: SELISIH & CEK QRIS (S10) ---

    fun toggleQrisChecked(paymentId: String) {
        _uiState.update { current ->
            val set = current.checkedQrisPaymentIds.toMutableSet()
            if (set.contains(paymentId)) {
                set.remove(paymentId)
            } else {
                set.add(paymentId)
            }
            current.copy(checkedQrisPaymentIds = set)
        }
    }

    fun checkAllQris() {
        val allIds = _uiState.value.summary?.qrisPayments?.map { it.id }?.toSet() ?: emptySet()
        _uiState.update { it.copy(checkedQrisPaymentIds = allIds) }
    }

    fun setDifferenceReason(reason: String) {
        _uiState.update { it.copy(differenceReason = reason, error = null) }
    }

    private var failedRecountPinAttempts = 0
    private var recountLockedUntilMs = 0L

    fun proceedToStep3() {
        val state = _uiState.value
        if (!state.isStep2Valid) {
            _uiState.update { it.copy(error = "Selisih kas melebihi batas toleransi (Rp 2.000). Alasan wajib diisi.") }
            return
        }
        _uiState.update { it.copy(currentStep = CloseShiftStep.STEP_3_ALLOCATION_AND_Z, error = null) }
    }

    fun openRecountPinDialog() {
        val now = System.currentTimeMillis()
        if (now < recountLockedUntilMs) {
            val remainingSec = ((recountLockedUntilMs - now) / 1000).coerceAtLeast(1).toInt()
            _uiState.update {
                it.copy(
                    isRecountPinDialogOpen = true,
                    isRecountLocked = true,
                    recountLockoutSeconds = remainingSec,
                    recountPinError = "Akses terkunci sementara. Sisa $remainingSec detik"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isRecountPinDialogOpen = true,
                    isRecountLocked = false,
                    recountLockoutSeconds = 0,
                    recountPinError = null
                )
            }
        }
    }

    fun closeRecountPinDialog() {
        _uiState.update { it.copy(isRecountPinDialogOpen = false, recountPinError = null) }
    }

    fun verifyManagerPinForRecount(pin: String) {
        val now = System.currentTimeMillis()
        if (now < recountLockedUntilMs) {
            val remainingSec = ((recountLockedUntilMs - now) / 1000).coerceAtLeast(1).toInt()
            _uiState.update {
                it.copy(
                    isRecountLocked = true,
                    recountLockoutSeconds = remainingSec,
                    recountPinError = "Akses terkunci sementara. Sisa $remainingSec detik"
                )
            }
            return
        }

        val isCorrect = ManagerPinVerifier.verify(pin)
        if (isCorrect) {
            failedRecountPinAttempts = 0
            _uiState.update {
                it.copy(
                    isRecountPinDialogOpen = false,
                    recountPinError = null,
                    currentStep = CloseShiftStep.STEP_1_BLIND_COUNT,
                    error = null
                )
            }
        } else {
            failedRecountPinAttempts++
            if (failedRecountPinAttempts >= 3) {
                recountLockedUntilMs = now + (300 * 1000L)
                _uiState.update {
                    it.copy(
                        isRecountLocked = true,
                        recountLockoutSeconds = 300,
                        recountPinError = "PIN salah 3 kali. Akses terkunci selama 5 menit"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(recountPinError = "PIN Manager salah. Sisa ${3 - failedRecountPinAttempts} percobaan")
                }
            }
        }
    }

    fun backToStep1() {
        openRecountPinDialog()
    }

    // --- STEP 3: ALOKASI KAS & LAPORAN Z (S11) ---

    fun setCashLeft(amount: Long) {
        _uiState.update { current ->
            val sanitized = amount.coerceAtLeast(0L)
            val deposited = (current.countedCash - sanitized).coerceAtLeast(0L)
            current.copy(cashLeft = sanitized, cashDeposited = deposited, error = null)
        }
    }

    fun setCashDeposited(amount: Long) {
        _uiState.update { current ->
            val sanitized = amount.coerceAtLeast(0L)
            current.copy(cashDeposited = sanitized, error = null)
        }
    }

    fun setCashTakenBy(name: String) {
        _uiState.update { it.copy(cashTakenBy = name, error = null) }
    }

    fun backToStep2() {
        _uiState.update { it.copy(currentStep = CloseShiftStep.STEP_2_DIFF_AND_QRIS, error = null) }
    }

    fun submitCloseShift(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        val summary = state.summary ?: return

        if (!state.canProceedToClose) {
            if (!state.isAllocationBalanced) {
                _uiState.update { it.copy(error = "Total alokasi kas harus sama dengan total kas fisik yang dihitung") }
            } else if (state.cashDepositedOverZeroWithoutTaker) {
                _uiState.update { it.copy(error = "Kas yang disetor wajib mencatat nama penerimanya") }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isClosing = true, error = null) }

            val result = repository.closeShift(
                shiftId = summary.shift.shiftId,
                countedCash = state.countedCash,
                differenceReason = if (state.cashDifference != 0L) state.differenceReason.trim() else null,
                cashLeft = state.cashLeft,
                cashDeposited = state.cashDeposited,
                cashTakenBy = if (state.cashDeposited > 0L) state.cashTakenBy.trim() else null,
                qrisCheckedPaymentIds = state.checkedQrisPaymentIds.toList(),
                handoverOrderIds = summary.openOrderIds
            )

            if (result.isSuccess) {
                _uiState.update { it.copy(isClosing = false, isSuccess = true) }
                // Otomatis cetak Laporan Z (TUP-05)
                printZReport(isCopy = false)
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isClosing = false,
                        error = result.exceptionOrNull()?.message ?: "Gagal menutup shift"
                    )
                }
            }
        }
    }

    fun printZReport(isCopy: Boolean = false) {
        val state = _uiState.value
        val summary = state.summary ?: return
        val printer = printerService ?: return

        viewModelScope.launch {
            try {
                val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.forLanguageTag("id-ID"))
                val currentTimeStr = dateFormat.format(Date())

                val zReportData = ZReportData(
                    storeName = state.storeName,
                    storeAddress = state.storeAddress,
                    storePhone = state.storePhone,
                    shiftId = summary.shift.shiftId,
                    cashierName = summary.shift.cashierName,
                    openedAt = summary.shift.openedAt,
                    closedAt = currentTimeStr,
                    grossSales = summary.grossSales,
                    totalDiscount = summary.totalDiscount,
                    totalRefund = summary.totalRefund,
                    netSales = summary.netSales,
                    transactionCount = summary.transactionCount,
                    cashSales = summary.cashSales,
                    cashDpAndSettlement = summary.cashDpAndSettlement,
                    qrisSales = summary.qrisSales,
                    openingCash = summary.shift.openingCash,
                    cashOuts = summary.cashOuts,
                    expectedCash = state.expectedCash,
                    countedCash = state.countedCash,
                    cashDifference = state.cashDifference,
                    differenceReason = state.differenceReason.ifBlank { null },
                    cashLeft = state.cashLeft,
                    cashDeposited = state.cashDeposited,
                    cashTakenBy = state.cashTakenBy.ifBlank { null },
                    isCopy = isCopy,
                    reprintInfo = if (isCopy) "Dicetak ulang oleh ${summary.shift.cashierName} · $currentTimeStr" else null
                )

                val bytes = ZReportFormatter.format(zReportData)
                printer.print(bytes)
                _uiState.update { it.copy(printStatusMessage = "Laporan Z berhasil dikirim ke printer") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal mencetak Laporan Z: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null, printStatusMessage = null) }
    }
}
