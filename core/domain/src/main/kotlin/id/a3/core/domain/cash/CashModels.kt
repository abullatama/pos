package id.a3.core.domain.cash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShiftCashInput(
    @SerialName("opening_cash")
    val openingCash: Long,
    @SerialName("cash_sales")
    val cashSales: Long,
    @SerialName("cash_dp_and_settlement")
    val cashDpAndSettlement: Long,
    @SerialName("cash_refunds")
    val cashRefunds: Long,
    @SerialName("cash_outs")
    val cashOuts: Long
)

@Serializable
data class CashChangeInput(
    val total: Long,
    val tendered: Long
)
