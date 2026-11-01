package id.a3.core.domain.monitoring

/**
 * Implementasi CrashReporter dengan penyaringan otomatis data sensitif (OPS-01 & Aturan 14).
 * Menjamin nomor telepon pelanggan, PIN, dan token autentikasi tidak pernah terlapor.
 */
class SanitizedCrashReporter(
    private val delegate: CrashReporter? = null
) : CrashReporter {

    private val localLogBuffer = mutableListOf<String>()

    fun getRecentLogs(): List<String> = synchronized(localLogBuffer) {
        localLogBuffer.toList()
    }

    override fun log(message: String) {
        val sanitized = DataSanitizer.sanitize(message)
        synchronized(localLogBuffer) {
            if (localLogBuffer.size >= 100) {
                localLogBuffer.removeAt(0)
            }
            localLogBuffer.add(sanitized)
        }
        delegate?.log(sanitized)
    }

    override fun recordException(throwable: Throwable) {
        val sanitizedThrowable = DataSanitizer.sanitizeThrowable(throwable)
        val sanitizedMessage = throwable.message?.let { DataSanitizer.sanitize(it) } ?: "Exception"
        log("ERROR: $sanitizedMessage")
        delegate?.recordException(sanitizedThrowable)
    }

    override fun setCustomKey(key: String, value: String) {
        val sanitizedKey = DataSanitizer.sanitize(key)
        val sanitizedValue = DataSanitizer.sanitize(value)
        delegate?.setCustomKey(sanitizedKey, sanitizedValue)
    }

    override fun setCustomKey(key: String, value: Boolean) {
        delegate?.setCustomKey(key, value)
    }

    override fun setCustomKey(key: String, value: Int) {
        delegate?.setCustomKey(key, value)
    }

    override fun setUserId(userId: String) {
        val sanitized = DataSanitizer.sanitize(userId)
        delegate?.setUserId(sanitized)
    }
}
