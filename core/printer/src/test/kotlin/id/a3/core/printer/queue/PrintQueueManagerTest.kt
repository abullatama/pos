package id.a3.core.printer.queue

import id.a3.core.printer.PrinterService
import id.a3.core.printer.PrinterStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrintQueueManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakePrinterService : PrinterService {
        val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
        override val status: StateFlow<PrinterStatus> = _status.asStateFlow()

        val printedPayloads = mutableListOf<ByteArray>()
        var shouldFailPrint: Boolean = false

        override fun isConnected(): Boolean = _status.value is PrinterStatus.Connected

        override suspend fun checkConnection(): Boolean = isConnected()

        override suspend fun print(bytes: ByteArray): Result<Unit> {
            if (!isConnected() || shouldFailPrint) {
                return Result.failure(IllegalStateException("Printer offline"))
            }
            printedPayloads.add(bytes)
            return Result.success(Unit)
        }

        override suspend fun openCashDrawer(): Result<Unit> = Result.success(Unit)
    }

    private lateinit var fakePrinter: FakePrinterService
    private lateinit var queueManager: PrintQueueManager

    @Before
    fun setUp() {
        fakePrinter = FakePrinterService()
        queueManager = PrintQueueManager(
            printerService = fakePrinter,
            scope = testScope,
            interJobDelayMs = 0L
        )
    }

    @Test
    fun `printOrEnqueue when printer is disconnected enqueues job with status PENDING without failing`() = runTest(testDispatcher) {
        val payload = "TEST STRUK".toByteArray()

        val job = queueManager.printOrEnqueue(
            payload = payload,
            description = "Struk C1-0001",
            type = PrintJobType.CUSTOMER_RECEIPT
        )

        assertEquals(PrintJobStatus.PENDING, job.status)
        assertEquals(1, queueManager.pendingCount.value)
        assertEquals(0, fakePrinter.printedPayloads.size)
    }

    @Test
    fun `F27 - printer disconnected then connected auto-drains pending queue and prints all jobs FIFO`() = runTest(testDispatcher) {
        val payload1 = "STRUK 1".toByteArray()
        val payload2 = "STRUK 2".toByteArray()

        // 1. Kirim 2 cetakan saat printer mati
        queueManager.printOrEnqueue(payload1, description = "Struk 1")
        queueManager.printOrEnqueue(payload2, description = "Struk 2")

        assertEquals(2, queueManager.pendingCount.value)
        assertEquals(0, fakePrinter.printedPayloads.size)

        // 2. Printer menyala dan terhubung (Connected)
        fakePrinter._status.value = PrinterStatus.Connected("iWare C58BT", "00:11:22:33:44:55")
        testScheduler.advanceUntilIdle()

        // 3. Verifikasi: Kedua dokumen otomatis tercetak berurutan (FIFO)
        assertEquals(0, queueManager.pendingCount.value)
        assertEquals(2, fakePrinter.printedPayloads.size)
        assertEquals("STRUK 1", String(fakePrinter.printedPayloads[0]))
        assertEquals("STRUK 2", String(fakePrinter.printedPayloads[1]))

        val allJobs = queueManager.jobs.value
        assertTrue(allJobs.all { it.status == PrintJobStatus.SUCCESS })
        assertTrue(allJobs.all { it.printedAt != null })
    }

    @Test
    fun `retryJob resets FAILED job to PENDING and prints when reconnected`() = runTest(testDispatcher) {
        val payload = "TIKET DAPUR".toByteArray()

        // Kirim saat printer terhubung tapi gagal socket
        fakePrinter._status.value = PrinterStatus.Connected("iWare C58BT", "00:11:22:33:44:55")
        fakePrinter.shouldFailPrint = true

        val job = queueManager.printOrEnqueue(payload, description = "Tiket Meja 2", type = PrintJobType.KITCHEN_TICKET)
        assertEquals(PrintJobStatus.PENDING, job.status)

        // Manual retry
        fakePrinter.shouldFailPrint = false
        val retryRes = queueManager.retryJob(job.id)
        testScheduler.advanceUntilIdle()

        assertTrue(retryRes.isSuccess)
        assertEquals(1, fakePrinter.printedPayloads.size)
        assertEquals("TIKET DAPUR", String(fakePrinter.printedPayloads.first()))
    }

    @Test
    fun `clearCompleted cleans finished jobs and preserves pending ones`() = runTest(testDispatcher) {
        fakePrinter._status.value = PrinterStatus.Connected("iWare C58BT", "00:11:22:33:44:55")

        // 1 job berhasil
        queueManager.printOrEnqueue("JOB SUCCESS".toByteArray(), description = "Job 1")
        testScheduler.advanceUntilIdle()

        // Matikan printer lalu buat 1 job pending
        fakePrinter._status.value = PrinterStatus.Disconnected
        queueManager.printOrEnqueue("JOB PENDING".toByteArray(), description = "Job 2")

        assertEquals(2, queueManager.jobs.value.size)

        // Clear completed
        queueManager.clearCompleted()

        assertEquals(1, queueManager.jobs.value.size)
        assertEquals("Job 2", queueManager.jobs.value[0].description)
        assertEquals(PrintJobStatus.PENDING, queueManager.jobs.value[0].status)
    }
}
