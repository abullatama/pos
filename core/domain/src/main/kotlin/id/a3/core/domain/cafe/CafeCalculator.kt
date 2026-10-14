package id.a3.core.domain.cafe

import id.a3.core.domain.money.MoneyMath
import kotlin.math.min

object CafeCalculator {

    /**
     * Menghitung rincian transaksi Cafe secara deterministik:
     * 1. Subtotal = sum(unit_price * quantity)
     * 2. Diskon = persen atas subtotal atau nominal tetap (maksimum sebesar subtotal)
     * 3. DPP = subtotal - diskon
     * 4. Biaya Layanan = persen atas DPP (bila aktif)
     * 5. Pajak:
     *    - Mode 'added': persen atas (DPP + layanan)
     *    - Mode 'included': harga sudah memuat pajak (rumus OQ-04)
     * 6. Total = pembulatan ke kelipatan Rp100 terdekat (setengah naik)
     * 7. Pembulatan = total - sebelum_pembulatan
     */
    fun calculate(input: CafeCalculationInput): CafeCalculationResult {
        val subtotal = input.lines.fold(0L) { acc, line ->
            acc + (line.unitPrice * line.quantity.toLong())
        }

        val discount = when (val d = input.discount) {
            null -> 0L
            else -> when (d.type) {
                DiscountType.PERCENT -> MoneyMath.pct(subtotal, d.value.toInt())
                DiscountType.AMOUNT -> min(d.value, subtotal)
            }
        }

        val dpp = subtotal - discount
        val serviceCharge = MoneyMath.pct(dpp, input.servicePercent)

        val (tax, beforeRounding) = when (input.taxMode) {
            TaxMode.ADDED -> {
                val taxAmount = MoneyMath.pct(dpp + serviceCharge, input.taxPercent)
                val before = dpp + serviceCharge + taxAmount
                taxAmount to before
            }
            TaxMode.INCLUDED -> {
                val before = dpp + serviceCharge
                val taxAmount = if (input.taxPercent > 0) {
                    (before * input.taxPercent * 2L + (100L + input.taxPercent)) / (2L * (100L + input.taxPercent))
                } else {
                    0L
                }
                taxAmount to before
            }
        }

        val total = MoneyMath.roundTo100(beforeRounding)
        val rounding = total - beforeRounding

        return CafeCalculationResult(
            subtotal = subtotal,
            discount = discount,
            serviceCharge = serviceCharge,
            tax = tax,
            rounding = rounding,
            total = total
        )
    }
}
