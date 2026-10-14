package id.a3.core.domain.cafe

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DiscountType {
    @SerialName("percent")
    PERCENT,

    @SerialName("amount")
    AMOUNT
}

@Serializable
data class OrderDiscount(
    val type: DiscountType,
    val value: Long
)

@Serializable
enum class TaxMode {
    @SerialName("added")
    ADDED,

    @SerialName("included")
    INCLUDED
}

@Serializable
data class CafeOrderLine(
    @SerialName("unit_price")
    val unitPrice: Long,
    val quantity: Int
)

@Serializable
data class CafeCalculationInput(
    val lines: List<CafeOrderLine>,
    val discount: OrderDiscount? = null,
    @SerialName("service_percent")
    val servicePercent: Int = 0,
    @SerialName("tax_percent")
    val taxPercent: Int = 0,
    @SerialName("tax_mode")
    val taxMode: TaxMode = TaxMode.ADDED
)

@Serializable
data class CafeCalculationResult(
    val subtotal: Long,
    val discount: Long,
    @SerialName("service_charge")
    val serviceCharge: Long,
    val tax: Long,
    val rounding: Long,
    val total: Long
)
