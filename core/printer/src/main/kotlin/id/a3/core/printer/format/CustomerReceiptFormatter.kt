package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CustomerReceiptLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,
    val optionsOrNote: String? = null
)

data class CustomerReceiptData(
    val storeName: String = "A3 COFFEESHOP",
    val storeAddress: String = "Jl. Kopi Pilihan No. 3",
    val storePhone: String = "0812-3456-7890",
    val orderDisplayNumber: String,
    val tableOrQueueLabel: String? = null,
    val cashierName: String = "Kasir",
    val occurredAt: String,
    val lines: List<CustomerReceiptLine>,
    val subtotal: Long,
    val discount: Long = 0L,
    val serviceCharge: Long = 0L,
    val tax: Long = 0L,
    val rounding: Long = 0L,
    val total: Long,
    val paymentMethodName: String,
    val tendered: Long? = null,
    val change: Long = 0L,
    val isCopy: Boolean = false
)

/**
 * Penyusun format struk pembayaran pelanggan termal 58mm (ESC/POS).
 * Sesuai aturan PRT-01, PRT-03, PRT-05, dan PAY-01:
 * - Menampilkan rincian pesanan, subtotal, diskon, biaya layanan, pajak, dan pembulatan.
 * - Menampilkan metode bayar (Tunai / QRIS Statis), uang diterima, dan kembalian.
 * - Berlabel *** SALINAN *** jika merupakan cetak ulang.
 */
object CustomerReceiptFormatter {

    fun format(
        data: CustomerReceiptData,
        charsPerLine: Int = 32
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        builder.reset()

        // 1. Tanda Cetak Ulang Salinan (PRT-05)
        if (data.isCopy) {
            builder.alignCenter()
                .boldOn()
                .textLine("*** SALINAN ***")
                .boldOff()
        }

        // 2. Header Toko
        builder.alignCenter()
            .boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine(data.storeName)
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
            .textLine(data.storeAddress)
            .textLine("Telp: ${data.storePhone}")
            .divider('=')

        // 3. Info Transaksi
        builder.alignLeft()
            .twoColumns("No. Order:", data.orderDisplayNumber)
        if (!data.tableOrQueueLabel.isNullOrBlank()) {
            builder.twoColumns("Meja / Antrean:", data.tableOrQueueLabel)
        }
        builder.twoColumns("Kasir:", data.cashierName)
            .twoColumns("Waktu:", data.occurredAt)
            .divider('-')

        // 4. Daftar Item
        data.lines.forEach { line ->
            builder.twoColumns("${line.quantity}x ${line.name}", formatRupiah(line.lineTotal))
            if (!line.optionsOrNote.isNullOrBlank()) {
                builder.textLine("   + ${line.optionsOrNote}")
            }
        }
        builder.divider('-')

        // 5. Rincian Perhitungan Keuangan
        builder.twoColumns("Subtotal", formatRupiah(data.subtotal))
        if (data.discount > 0L) {
            builder.twoColumns("Diskon", "-${formatRupiah(data.discount)}")
        }
        if (data.serviceCharge > 0L) {
            builder.twoColumns("Biaya Layanan", formatRupiah(data.serviceCharge))
        }
        if (data.tax > 0L) {
            builder.twoColumns("Pajak", formatRupiah(data.tax))
        }
        if (data.rounding != 0L) {
            val prefix = if (data.rounding > 0) "+" else ""
            builder.twoColumns("Pembulatan", "$prefix${formatRupiah(data.rounding)}")
        }

        builder.divider('=')

        // 6. Total Akhir & Pembayaran
        builder.boldOn()
            .textSize(widthMultiplier = 1, heightMultiplier = 2)
            .twoColumns("TOTAL", formatRupiah(data.total))
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()

        val tenderedAmount = data.tendered ?: data.total
        builder.twoColumns("Bayar (${data.paymentMethodName})", formatRupiah(tenderedAmount))
        builder.twoColumns("Kembalian", formatRupiah(data.change))

        builder.divider('=')

        // 7. Footer
        builder.alignCenter()
            .textLine("Terima kasih atas kunjungan Anda")
            .textLine("Nikmati momen kopi terbaik!")
            .feed(4)

        return builder.build()
    }

    private fun formatRupiah(amount: Long): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
            groupingSeparator = '.'
            monetaryDecimalSeparator = ','
        }
        val formatter = DecimalFormat("Rp#,###", symbols)
        return if (amount == 0L) "Rp0" else formatter.format(amount)
    }
}
