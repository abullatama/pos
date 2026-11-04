package id.a3.core.data.util

/**
 * Pemetaan dwiarah kode alasan koreksi (Void, Refund, dll)
 * antara domain OpenAPI (bahasa Inggris) dan backend Laravel 12 (bahasa Indonesia).
 *
 * Sesuai docs/API_DOCUMENTATION.md (bagian 3.7.11) dan contracts/openapi.yaml:
 * - wrong_input        <-> salah_input
 * - customer_cancelled <-> pelanggan_batal
 * - quality_complaint  <-> komplain_kualitas
 * - wrong_made         <-> salah_buat
 * - promo              <-> promo
 * - other              <-> lainnya
 */
object CorrectionReasonMapper {

    val VALID_REASONS = setOf(
        "wrong_input", "customer_cancelled", "quality_complaint", "wrong_made", "promo", "other",
        "salah_input", "pelanggan_batal", "komplain_kualitas", "salah_buat", "lainnya"
    )

    fun toBackend(reasonCode: String): String = when (reasonCode.trim().lowercase()) {
        "wrong_input" -> "salah_input"
        "customer_cancelled" -> "pelanggan_batal"
        "quality_complaint" -> "komplain_kualitas"
        "wrong_made" -> "salah_buat"
        "promo" -> "promo"
        "other" -> "lainnya"
        else -> reasonCode.trim()
    }

    fun toOpenApi(reasonCode: String): String = when (reasonCode.trim().lowercase()) {
        "salah_input" -> "wrong_input"
        "pelanggan_batal" -> "customer_cancelled"
        "komplain_kualitas" -> "quality_complaint"
        "salah_buat" -> "wrong_made"
        "promo" -> "promo"
        "lainnya" -> "other"
        else -> reasonCode.trim()
    }
}
