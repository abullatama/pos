package id.a3.feature.cafe.model

import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.OrderDiscount

data class DiscountPreset(
    val id: String,
    val name: String,
    val type: DiscountType,
    val value: Long
)

val DEFAULT_DISCOUNT_PRESETS = listOf(
    DiscountPreset(id = "p-5", name = "Diskon 5%", type = DiscountType.PERCENT, value = 5L),
    DiscountPreset(id = "p-10", name = "Diskon 10%", type = DiscountType.PERCENT, value = 10L),
    DiscountPreset(id = "p-5k", name = "Potongan Rp5.000", type = DiscountType.AMOUNT, value = 5000L)
)

enum class DiscountReason(val label: String) {
    SPECIAL_PROMO("Promo Khusus / Tamu VIP"),
    QUALITY_COMPLAINT("Komplain Kualitas / Pelayanan"),
    OWNER_COMPLIMENT("Kerabat / Komplimen Owner"),
    INPUT_MISTAKE("Salah Input Sebelumnya"),
    OTHER("Lainnya (+ Catatan)")
}

data class AppliedDiscount(
    val discount: OrderDiscount,
    val label: String,
    val isPreset: Boolean,
    val approvedBy: String? = null,
    val reason: String? = null,
    val note: String? = null
)
