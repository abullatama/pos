package id.a3.core.auth

/**
 * Mengunci layar kasir setelah tidak ada sentuhan selama [timeoutMs] (AUT-05, usulan 2 menit).
 * Hanya memanggil [onIdle] (mis. AuthManager.lock): tanpa logout, token dan transaksi tidak tersentuh.
 *
 * [clock] harus jam monoton (SystemClock.elapsedRealtime) agar tablet yang layarnya mati tetap terkunci
 * saat kembali. [isBusy] true berarti ada pekerjaan yang tidak boleh terputus; hitungan idle diulang.
 */
class IdleLockController(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long,
    private val isBusy: () -> Boolean = { false },
    private val onIdle: () -> Unit
) {
    private var lastActivityAt: Long = clock()

    /** Panggil pada setiap sentuhan. */
    fun onUserActivity() {
        lastActivityAt = clock()
    }

    /** Periksa secara berkala dan saat aplikasi kembali tampil. Hanya bermakna saat layar sedang terbuka. */
    fun tick(isUnlocked: Boolean) {
        if (!isUnlocked) {
            lastActivityAt = clock()
            return
        }
        if (isBusy()) {
            lastActivityAt = clock()
            return
        }
        if (clock() - lastActivityAt >= timeoutMs) {
            lastActivityAt = clock()
            onIdle()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 2 * 60 * 1000L
    }
}
