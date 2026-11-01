package id.a3.core.domain.monitoring

/**
 * Kontrak pemantauan kesalahan dan telemetri (OPS-01).
 * Mengabstraksi pelaporan galat ke Firebase Crashlytics atau logger lokal.
 */
interface CrashReporter {
    fun log(message: String)
    fun recordException(throwable: Throwable)
    fun setCustomKey(key: String, value: String)
    fun setCustomKey(key: String, value: Boolean)
    fun setCustomKey(key: String, value: Int)
    fun setUserId(userId: String)
}
