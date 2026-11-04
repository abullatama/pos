package id.a3.feature.cafe.model

import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.security.ManagerApproval

/** Preset dari server (nama persis seperti di pengaturan owner). Tidak ada preset bawaan di tablet. */
data class DiscountPreset(
    val id: String,
    val name: String,
    val type: DiscountType,
    val value: Long
)

/**
 * Alasan diskon manual. [code] mengikuti daftar tetap COR-01 yang dipakai koreksi
 * (wrong_input, customer_cancelled, quality_complaint, wrong_made, promo, other).
 */
enum class DiscountReason(val label: String, val code: String) {
    SPECIAL_PROMO("Promo Khusus / Tamu VIP", "promo"),
    QUALITY_COMPLAINT("Komplain Kualitas / Pelayanan", "quality_complaint"),
    OWNER_COMPLIMENT("Kerabat / Komplimen Owner", "other"),
    INPUT_MISTAKE("Salah Input Sebelumnya", "wrong_input"),
    OTHER("Lainnya (+ Catatan)", "other")
}

/**
 * Diskon yang dipasang pada keranjang. Diskon manual wajib membawa [approval]: manager yang benar-benar
 * memasukkan PIN (bukan tulisan tetap), karena identitasnya dikirim ke server sebagai approved_by.
 */
data class AppliedDiscount(
    val discount: OrderDiscount,
    val label: String,
    val isPreset: Boolean,
    val approval: ManagerApproval? = null,
    val reason: String? = null,
    val reasonCode: String? = null,
    val note: String? = null
)
