package id.a3.feature.admin.presentation

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.domain.security.ManagerPinVerifier
import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import id.a3.core.printer.escpos.EscPosBuilder
import id.a3.feature.admin.kiosk.KioskManager
import id.a3.feature.admin.update.ApkInstallerHelper
import id.a3.feature.admin.update.UpdateEligibility
import id.a3.feature.admin.update.UpdatePreconditionValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AdminUiState(
    val isOwnerUnlocked: Boolean = false,
    val enteredPin: String = "",
    val pinError: String? = null,
    val isPinLocked: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val isKioskActive: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateEligibility: UpdateEligibility? = null,
    val activeShiftCashierName: String? = null,
    val activeShiftId: String? = null,
    val unsyncedOutboxCount: Int = 0,
    val printerStatusText: String = "Memeriksa...",
    val isPrinterConnected: Boolean = false,
    val isTestingPrinter: Boolean = false,
    val printerTestSuccess: Boolean? = null,
    val appVersionName: String = "1.0.0",
    val appVersionCode: Long = 1L,
    val deviceId: String = "DEV-TABLET-01",
    val businessUnitId: String = "BU-01",
    val statusMessage: String? = null
)

class AdminViewModel(
    private val shiftDao: ShiftDao,
    private val outboxDao: OutboxDao,
    private val updateValidator: UpdatePreconditionValidator,
    private val printerService: PrinterService? = null,
    private val deviceId: String = "DEV-TABLET-01",
    private val businessUnitId: String = "BU-01",
    private val appVersion: String = "1.0.0",
    private val ownerPinHash: String = ManagerPinVerifier.hashPin("9999")
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdminUiState(
            deviceId = deviceId,
            businessUnitId = businessUnitId,
            appVersionName = appVersion
        )
    )
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var failedPinAttempts = 0
    private var lockoutJob: Job? = null

    init {
        observeActiveShift()
        observeUnsyncedOutbox()
        observePrinterStatus()
    }

    private fun observeActiveShift() {
        viewModelScope.launch {
            shiftDao.observeActiveShift().collect { shift ->
                _uiState.update { state ->
                    state.copy(
                        activeShiftCashierName = shift?.cashierName,
                        activeShiftId = shift?.id
                    )
                }
            }
        }
    }

    private fun observeUnsyncedOutbox() {
        viewModelScope.launch {
            outboxDao.observeUnsyncedCount().collect { count ->
                _uiState.update { state ->
                    state.copy(unsyncedOutboxCount = count)
                }
            }
        }
    }

    private fun observePrinterStatus() {
        if (printerService != null) {
            viewModelScope.launch {
                printerService.status.collect { status ->
                    val (text, connected) = when (status) {
                        is PrinterStatus.Connected -> Pair("Terkoneksi (${status.deviceName})", true)
                        is PrinterStatus.Connecting -> Pair("Menghubungkan...", false)
                        is PrinterStatus.Disconnected -> Pair("Terputus", false)
                        is PrinterStatus.Error -> Pair("Galat: ${status.message}", false)
                    }
                    _uiState.update { it.copy(printerStatusText = text, isPrinterConnected = connected) }
                }
            }
        }
    }

    fun onPinDigit(digit: String) {
        if (_uiState.value.isPinLocked || _uiState.value.enteredPin.length >= 4) return
        val newPin = _uiState.value.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, pinError = null) }
        if (newPin.length == 4) {
            verifyOwnerPin(newPin)
        }
    }

    fun onPinBackspace() {
        if (_uiState.value.isPinLocked || _uiState.value.enteredPin.isEmpty()) return
        _uiState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), pinError = null) }
    }

    fun onPinClear() {
        if (_uiState.value.isPinLocked || _uiState.value.enteredPin.isEmpty()) return
        _uiState.update { it.copy(enteredPin = "", pinError = null) }
    }

    fun verifyOwnerPin(pin: String = _uiState.value.enteredPin) {
        if (_uiState.value.isPinLocked) return
        val isCorrect = ManagerPinVerifier.hashPin(pin) == ownerPinHash
        if (isCorrect) {
            failedPinAttempts = 0
            _uiState.update {
                it.copy(
                    isOwnerUnlocked = true,
                    enteredPin = "",
                    pinError = null
                )
            }
            checkUpdateEligibility()
        } else {
            failedPinAttempts++
            if (failedPinAttempts >= 3) {
                triggerLockout(300) // 5 menit
            } else {
                val sisa = 3 - failedPinAttempts
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        pinError = "PIN Owner salah. Sisa kesempatan: $sisa kali"
                    )
                }
            }
        }
    }

    private fun triggerLockout(seconds: Int) {
        lockoutJob?.cancel()
        _uiState.update {
            it.copy(
                isPinLocked = true,
                lockoutRemainingSeconds = seconds,
                enteredPin = "",
                pinError = "PIN salah 3 kali berturut-turut. Menu Admin terkunci selama ${seconds / 60} menit."
            )
        }
        lockoutJob = viewModelScope.launch {
            var rem = seconds
            while (rem > 0) {
                delay(1000)
                rem--
                _uiState.update { it.copy(lockoutRemainingSeconds = rem) }
            }
            failedPinAttempts = 0
            _uiState.update {
                it.copy(
                    isPinLocked = false,
                    lockoutRemainingSeconds = 0,
                    pinError = null
                )
            }
        }
    }

    fun lockAdmin() {
        _uiState.update {
            it.copy(
                isOwnerUnlocked = false,
                enteredPin = "",
                pinError = null
            )
        }
    }

    fun refreshKioskState(context: Context) {
        val active = KioskManager.isLockTaskActive(context)
        _uiState.update { it.copy(isKioskActive = active) }
    }

    fun enableKiosk(activity: Activity) {
        val result = KioskManager.startKiosk(activity)
        result.onSuccess {
            _uiState.update {
                it.copy(
                    isKioskActive = true,
                    statusMessage = "Mode Kios (Lock Task) diaktifkan. Tombol Back/Home/Recents diblokir."
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(statusMessage = "Gagal mengaktifkan mode kios: ${err.message}")
            }
        }
    }

    fun disableKiosk(activity: Activity) {
        val result = KioskManager.stopKiosk(activity)
        result.onSuccess {
            _uiState.update {
                it.copy(
                    isKioskActive = false,
                    statusMessage = "Mode Kios dilepaskan. Navigasi tablet bebas."
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(statusMessage = "Gagal menonaktifkan mode kios: ${err.message}")
            }
        }
    }

    fun openWifi(context: Context) {
        KioskManager.openWifiSettings(context)
    }

    fun testPrinter() {
        val service = printerService ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTestingPrinter = true, printerTestSuccess = null) }
            val connected = service.checkConnection()
            if (!connected) {
                _uiState.update {
                    it.copy(
                        isTestingPrinter = false,
                        printerTestSuccess = false,
                        statusMessage = "Uji Cetak Gagal: Printer tidak terhubung."
                    )
                }
                return@launch
            }

            val testBytes = EscPosBuilder()
                .reset()
                .alignCenter()
                .boldOn()
                .textLine("=== UJI PRINTER A3 ===")
                .boldOff()
                .textLine("AA Coffeeshop / Athia Laundry")
                .textLine("Printer iWare C58BT Siap")
                .textLine("Status: OK (ESC/POS 58mm)")
                .feed(3)
                .cut()
                .build()

            val printResult = service.print(testBytes)
            _uiState.update {
                it.copy(
                    isTestingPrinter = false,
                    printerTestSuccess = printResult.isSuccess,
                    statusMessage = if (printResult.isSuccess) {
                        "Uji cetak berhasil dikirim ke printer."
                    } else {
                        "Uji cetak gagal: ${printResult.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    fun checkUpdateEligibility() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val eligibility = updateValidator.checkUpdateEligibility()
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    updateEligibility = eligibility
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        viewModelScope.launch {
            // Evaluasi ulang kepatuhan OPS-03 tepat sebelum eksekusi
            val eligibility = updateValidator.checkUpdateEligibility()
            _uiState.update { it.copy(updateEligibility = eligibility) }
            if (eligibility is UpdateEligibility.Blocked) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Update diblokir (OPS-03):\n${eligibility.userFriendlyMessage}"
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(statusMessage = "Memulai pemasangan pembaruan via PackageInstaller...") }
            ApkInstallerHelper.installApk(context, apkFile) { success, msg ->
                _uiState.update {
                    it.copy(
                        statusMessage = if (success) "Pembaruan berhasil dipasang!" else "Pemasangan: $msg"
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
