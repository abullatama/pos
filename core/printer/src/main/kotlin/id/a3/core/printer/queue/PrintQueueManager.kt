package id.a3.core.printer.queue

import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class PrintJobStatus {
    PENDING,
    PRINTING,
    SUCCESS,
    FAILED
}

enum class PrintJobType {
    CUSTOMER_RECEIPT,
    KITCHEN_TICKET,
    LAUNDRY_TAG,
    Z_REPORT,
    GENERIC
}

data class PrintJob(
    val id: String = UUID.randomUUID().toString(),
    val type: PrintJobType = PrintJobType.GENERIC,
    val description: String,
    val payload: ByteArray,
    val status: PrintJobStatus = PrintJobStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val isCopy: Boolean = false,
    val printedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val printedAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrintJob
        return id == other.id && status == other.status && attempts == other.attempts
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Pengelola Antrean Cetak Otomatis (PRT-02 & F27):
 * - Menampung cetakan saat printer mati atau Bluetooth SPP terputus.
 * - Mengamati status koneksi printer dan otomatis mencetak ulang (auto-drain FIFO) saat printer terhubung kembali.
 * - Menyediakan retry manual dan pencatatan riwayat pekerjaan cetak.
 */
class PrintQueueManager(
    private val printDirect: suspend (ByteArray) -> Result<Unit>,
    private val isConnected: () -> Boolean,
    private val statusFlow: StateFlow<PrinterStatus>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val maxAttempts: Int = 3,
    private val interJobDelayMs: Long = 800L
) {
    constructor(
        printerService: PrinterService,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        maxAttempts: Int = 3,
        interJobDelayMs: Long = 800L
    ) : this(
        printDirect = { printerService.print(it) },
        isConnected = { printerService.isConnected() },
        statusFlow = printerService.status,
        scope = scope,
        maxAttempts = maxAttempts,
        interJobDelayMs = interJobDelayMs
    )

    private val mutex = Mutex()
    private val _jobs = MutableStateFlow<List<PrintJob>>(emptyList())
    val jobs: StateFlow<List<PrintJob>> = _jobs.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        // Auto-drain: Dengarkan perubahan status printer. Saat Connected, proses antrean pending secara otomatis
        scope.launch {
            statusFlow.collect { status ->
                if (status is PrinterStatus.Connected) {
                    processPendingQueue()
                }
            }
        }
    }

    /**
     * Mengirim cetakan langsung bila printer terhubung, atau memasukkannya ke antrean PENDING
     * bila printer sedang offline/putus (PRT-01: kegagalan printer tidak membatalkan transaksi).
     */
    suspend fun printOrEnqueue(
        payload: ByteArray,
        description: String = "Dokumen POS",
        type: PrintJobType = PrintJobType.GENERIC,
        isCopy: Boolean = false,
        staffName: String? = null
    ): PrintJob = mutex.withLock {
        val job = PrintJob(
            type = type,
            description = description,
            payload = payload,
            status = PrintJobStatus.PENDING,
            isCopy = isCopy,
            printedBy = staffName
        )

        // Simpan ke antrean terlebih dahulu
        updateJobsList(_jobs.value + job)

        if (isConnected()) {
            return@withLock executeJobUnderLock(job)
        }

        job
    }

    /**
     * Memproses semua pekerjaan berstatus PENDING secara berurutan (FIFO).
     */
    suspend fun processPendingQueue(): Int = mutex.withLock {
        if (!isConnected()) {
            return@withLock 0
        }

        var successCount = 0
        val pendingList = _jobs.value.filter { it.status == PrintJobStatus.PENDING }

        for (job in pendingList) {
            if (!isConnected()) break

            val updated = executeJobUnderLock(job)
            if (updated.status == PrintJobStatus.SUCCESS) {
                successCount++
            }

            if (interJobDelayMs > 0) {
                delay(interJobDelayMs)
            }
        }

        successCount
    }

    /**
     * Mencoba lagi pekerjaan tertentu yang berstatus FAILED atau PENDING.
     */
    suspend fun retryJob(jobId: String): Result<Unit> = mutex.withLock {
        val job = _jobs.value.find { it.id == jobId }
            ?: return@withLock Result.failure(IllegalArgumentException("Job tidak ditemukan: $jobId"))

        val resetJob = job.copy(
            status = PrintJobStatus.PENDING,
            attempts = 0,
            lastError = null
        )
        updateJobInList(resetJob)

        if (isConnected()) {
            val res = executeJobUnderLock(resetJob)
            return@withLock if (res.status == PrintJobStatus.SUCCESS) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(res.lastError ?: "Gagal mencetak ulang"))
            }
        }

        Result.success(Unit)
    }

    /**
     * Mencoba lagi seluruh pekerjaan yang berstatus FAILED.
     */
    suspend fun retryAllFailed(): Int = mutex.withLock {
        val failed = _jobs.value.filter { it.status == PrintJobStatus.FAILED }
        for (job in failed) {
            updateJobInList(job.copy(status = PrintJobStatus.PENDING, attempts = 0, lastError = null))
        }

        if (isConnected()) {
            var successCount = 0
            val pendingList = _jobs.value.filter { it.status == PrintJobStatus.PENDING }
            for (job in pendingList) {
                if (!isConnected()) break
                val updated = executeJobUnderLock(job)
                if (updated.status == PrintJobStatus.SUCCESS) successCount++
                if (interJobDelayMs > 0) delay(interJobDelayMs)
            }
            return@withLock successCount
        }

        0
    }

    /**
     * Membersihkan riwayat cetakan yang telah selesai (SUCCESS).
     */
    fun clearCompleted() {
        updateJobsList(_jobs.value.filter { it.status != PrintJobStatus.SUCCESS })
    }

    private suspend fun executeJobUnderLock(job: PrintJob): PrintJob {
        val printingJob = job.copy(status = PrintJobStatus.PRINTING)
        updateJobInList(printingJob)

        val result = printDirect(job.payload)
        return if (result.isSuccess) {
            val completed = printingJob.copy(
                status = PrintJobStatus.SUCCESS,
                printedAt = System.currentTimeMillis(),
                lastError = null
            )
            updateJobInList(completed)
            completed
        } else {
            val nextAttempts = job.attempts + 1
            val nextStatus = if (nextAttempts >= maxAttempts) PrintJobStatus.FAILED else PrintJobStatus.PENDING
            val failed = printingJob.copy(
                status = nextStatus,
                attempts = nextAttempts,
                lastError = result.exceptionOrNull()?.message ?: "Gagal mencetak serial"
            )
            updateJobInList(failed)
            failed
        }
    }

    private fun updateJobsList(newList: List<PrintJob>) {
        _jobs.value = newList
        _pendingCount.value = newList.count { it.status == PrintJobStatus.PENDING }
    }

    private fun updateJobInList(updated: PrintJob) {
        updateJobsList(_jobs.value.map { if (it.id == updated.id) updated else it })
    }
}
