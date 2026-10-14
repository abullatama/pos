package id.a3.core.domain.laundry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LaundryUnit {
    @SerialName("kg")
    KG,

    @SerialName("pcs")
    PCS
}

@Serializable
data class LaundryOrderLine(
    val unit: LaundryUnit,
    val rate: Long,
    val weight: Double? = null,
    @SerialName("min_weight")
    val minWeight: Double = 1.0,
    val step: Double = 0.5,
    val quantity: Int? = null
)

@Serializable
data class LaundryCalculationResult(
    @SerialName("billed_weights")
    val billedWeights: List<Double>,
    @SerialName("line_totals")
    val lineTotals: List<Long>,
    val total: Long
)

@Serializable
data class LaundryPaymentInput(
    val total: Long,
    val payments: List<Long>
)

@Serializable
data class LaundryPaymentResult(
    val remaining: Long,
    @SerialName("payment_status")
    val paymentStatus: String,
    @SerialName("after_full_payment")
    val afterFullPayment: String = "paid"
)
