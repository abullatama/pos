package id.a3.core.printer

import id.a3.core.printer.queue.PrintQueueManager
import kotlinx.coroutines.flow.StateFlow

sealed interface PrinterStatus {
    data object Disconnected : PrinterStatus
    data object Connecting : PrinterStatus
    data class Connected(val deviceName: String, val address: String) : PrinterStatus
    data class Error(val message: String) : PrinterStatus
}

/**
 * Layanan pencetakan struk, tiket dapur, nota laundry, dan laporan Z:
 * Mengabstraksi komunikasi serial Bluetooth SPP dan USB ESC/POS 58mm.
 */
interface PrinterService {
    val status: StateFlow<PrinterStatus>
    fun isConnected(): Boolean
    suspend fun checkConnection(): Boolean
    suspend fun print(bytes: ByteArray): Result<Unit>
    suspend fun openCashDrawer(): Result<Unit>

    // Pengelola antrean cetak saat printer offline/putus (PRT-02 & F27)
    val queueManager: PrintQueueManager? get() = null
}
