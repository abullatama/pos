package id.a3.core.domain.laundry

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

object LaundryCalculator {

    /**
     * Menghitung total order laundry:
     * - per kg : berat dibulatkan ke kelipatan step (misal 0,5 kg), minimum minWeight (misal 1,0 kg)
     *            harga baris = billed_weight * rate
     * - per pcs: harga baris = quantity * rate
     */
    fun calculateOrder(lines: List<LaundryOrderLine>): LaundryCalculationResult {
        val billedWeights = mutableListOf<Double>()
        val lineTotals = mutableListOf<Long>()

        for (line in lines) {
            when (line.unit) {
                LaundryUnit.KG -> {
                    val weight = line.weight ?: 0.0
                    val step = line.step
                    val rawHalves = weight / step
                    val roundedHalves = Math.round(rawHalves * 1e9) / 1e9
                    val halves = ceil(roundedHalves)
                    val billed = max(line.minWeight, halves * step)
                    val lineTotal = (billed * line.rate).roundToLong()

                    billedWeights.add(billed)
                    lineTotals.add(lineTotal)
                }
                LaundryUnit.PCS -> {
                    val qty = line.quantity ?: 0
                    val lineTotal = qty.toLong() * line.rate
                    lineTotals.add(lineTotal)
                }
            }
        }

        val total = lineTotals.sum()
        return LaundryCalculationResult(
            billedWeights = billedWeights,
            lineTotals = lineTotals,
            total = total
        )
    }

    /**
     * Menghitung status pembayaran laundry (DP vs Pelunasan):
     * - Sisa = total - sum(payments)
     * - Status: 'dp' bila pembayaran di antara 0 dan total, 'paid' bila lunas, 'unpaid' bila belum bayar
     */
    fun calculatePayment(input: LaundryPaymentInput): LaundryPaymentResult {
        val totalPaid = input.payments.sum()
        val remaining = input.total - totalPaid
        val status = when {
            totalPaid > 0L && totalPaid < input.total -> "dp"
            totalPaid >= input.total -> "paid"
            else -> "unpaid"
        }

        return LaundryPaymentResult(
            remaining = remaining,
            paymentStatus = status,
            afterFullPayment = "paid"
        )
    }
}
