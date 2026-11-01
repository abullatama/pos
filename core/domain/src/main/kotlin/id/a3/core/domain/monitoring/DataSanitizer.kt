package id.a3.core.domain.monitoring

/**
 * Sanitizer data sensitif sesuai aturan AGENTS.md (Aturan 14) dan OPS-01:
 * Melindungi data pribadi pelanggan, PIN kasir, dan token autentikasi
 * agar tidak pernah bocor ke sistem logging atau Crashlytics.
 */
object DataSanitizer {
    private val PHONE_REGEX = Regex("""(?:\+?62|08)[0-9]{8,12}""")
    private val BEARER_TOKEN_REGEX = Regex("""Bearer\s+[A-Za-z0-9\-_.~+/]+=*""", RegexOption.IGNORE_CASE)
    private val PIN_KEY_REGEX = Regex("""(?i)(pin|password|secret|credential)["':=\s]+[0-9a-zA-Z]{4,64}""")

    fun sanitize(input: String): String {
        var result = input
        // 1. Redact Bearer Tokens
        result = BEARER_TOKEN_REGEX.replace(result, "Bearer [REDACTED_TOKEN]")
        // 2. Redact PIN / Password / Credential patterns
        result = PIN_KEY_REGEX.replace(result) { match ->
            val prefix = match.value.substring(0, match.value.indexOfFirst { it == ':' || it == '=' || it == '\'' || it == '"' } + 1)
            "$prefix[REDACTED]"
        }
        // 3. Redact Indonesian Phone Numbers
        result = PHONE_REGEX.replace(result, "[REDACTED_PHONE]")
        return result
    }

    fun sanitizeThrowable(throwable: Throwable): Throwable {
        val originalMessage = throwable.message
        val sanitizedMessage = originalMessage?.let { sanitize(it) } ?: "Exception"
        val sanitizedException = RuntimeException(
            "${throwable.javaClass.simpleName}: $sanitizedMessage"
        )
        sanitizedException.stackTrace = throwable.stackTrace
        return sanitizedException
    }
}
