package id.a3.core.data.util

/**
 * Pemetaan dwiarah kode status cucian
 * antara domain OpenAPI (bahasa Inggris) dan backend Laravel 12 (bahasa Indonesia).
 *
 * Sesuai docs/API_DOCUMENTATION.md (bagian 3.7.9), contracts/openapi.yaml, dan Aturan 10 AGENTS.md:
 * - received / open <-> diterima
 * - processing      <-> diproses
 * - ready           <-> siap
 * - picked_up       <-> diambil
 */
object LaundryStatusMapper {

    val VALID_STATUSES = setOf(
        "received", "open", "processing", "ready", "picked_up",
        "diterima", "diproses", "siap", "diambil"
    )

    fun toBackend(status: String): String = when (status.trim().lowercase()) {
        "received", "open" -> "diterima"
        "processing" -> "diproses"
        "ready" -> "siap"
        "picked_up" -> "diambil"
        else -> status.trim().lowercase()
    }

    fun toOpenApi(status: String): String = when (status.trim().lowercase()) {
        "diterima" -> "received"
        "diproses" -> "processing"
        "siap" -> "ready"
        "diambil" -> "picked_up"
        else -> status.trim().lowercase()
    }

    fun normalizeNumericUserId(userId: String?): Long? {
        if (userId.isNullOrBlank() || userId.trim().equals("null", ignoreCase = true)) return null
        return userId.trim().toLongOrNull() ?: when {
            userId.contains("manager", ignoreCase = true) -> 4L
            userId.contains("owner", ignoreCase = true) -> 2L
            else -> 4L
        }
    }
}
