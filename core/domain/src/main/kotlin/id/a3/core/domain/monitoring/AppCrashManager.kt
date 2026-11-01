package id.a3.core.domain.monitoring

/**
 * Manajer terpusat pemantauan kesalahan aplikasi:
 * - Menginisialisasi pelapor galat (Firebase Crashlytics atau fallback lokal).
 * - Menangkap fatal crash via Thread.setDefaultUncaughtExceptionHandler tanpa membocorkan data sensitif.
 */
object AppCrashManager {
    private var _reporter: SanitizedCrashReporter = SanitizedCrashReporter()
    val reporter: SanitizedCrashReporter get() = _reporter

    private var isHandlerInstalled = false

    fun init(delegate: CrashReporter? = null) {
        _reporter = SanitizedCrashReporter(delegate)
    }

    fun installUncaughtExceptionHandler() {
        if (isHandlerInstalled) return
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                _reporter.recordException(throwable)
                _reporter.log("FATAL crash on thread ${thread.name}")
            } catch (_: Exception) {
                // Mencegah crash berulang di dalam handler
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        isHandlerInstalled = true
    }
}
