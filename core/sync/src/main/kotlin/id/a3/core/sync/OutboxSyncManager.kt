package id.a3.core.sync

import id.a3.core.data.local.dao.OutboxDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pengelola siklus sinkronisasi outbox di tablet (F43, SYN-03, SYN-05):
 * 1. Menjalankan foreground loop kontinu (default per 15 detik) saat aplikasi POS aktif.
 * 2. Mengirimkan bundel transaksi berstatus PENDING secara berurutan & idempoten melalui SyncSender.
 * 3. Menyediakan fungsi triggerSync() manual untuk tombol 'Sinkron Sekarang' atau rekoneksi internet.
 * 4. Menjamin hanya satu proses sinkron yang berjalan pada satu waktu menggunakan Mutex (SYN-05).
 */
class OutboxSyncManager(
    private val syncSender: SyncSender,
    private val outboxDao: OutboxDao,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 15_000L
) {
    private val syncMutex = Mutex()
    private var syncLoopJob: Job? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncSendSummary?>(null)
    val lastSyncResult: StateFlow<SyncSendSummary?> = _lastSyncResult.asStateFlow()

    fun startForegroundSyncLoop() {
        if (syncLoopJob?.isActive == true) return
        syncLoopJob = scope.launch(ioDispatcher) {
            while (isActive) {
                try {
                    val pending = outboxDao.getPendingCount()
                    val failed = outboxDao.getFailedCount()
                    if (pending > 0 || failed > 0) {
                        triggerSync()
                    }
                } catch (_: Exception) {
                    // Penanganan error jaringan/koneksi tanpa menghentikan loop foreground
                }
                delay(pollIntervalMillis)
            }
        }
    }

    fun stopForegroundSyncLoop() {
        syncLoopJob?.cancel()
        syncLoopJob = null
    }

    suspend fun triggerSync(limit: Int = 50): SyncSendSummary {
        return syncMutex.withLock {
            _isSyncing.value = true
            try {
                val summary = syncSender.sendPendingBundles(limit)
                _lastSyncResult.value = summary
                summary
            } catch (e: Exception) {
                val errorSummary = SyncSendSummary(sent = 0, duplicates = 0, failed = 1)
                _lastSyncResult.value = errorSummary
                errorSummary
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
