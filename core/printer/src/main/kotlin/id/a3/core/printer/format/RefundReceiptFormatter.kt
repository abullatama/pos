package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class RefundReceiptData(
    val storeName: String = "A3 COFFEESHOP",
    val storeAddress: String = "Jl. Kopi Pilihan No. 3",
    val storePhone: String = "0812-3456-7890",
    val orderDisplayNumber: String,
    val tableOrQueueLabel: String? = null,
    val cashierName: String = "Kasir",
    val approvedBy: String,
    val occurredAt: String,
    val correctionType: String = "REFUND TUNAI", // "REFUND TUNAI" atau "VOID PESANAN"
    val reason: String,
    val note: String? = null,
    val isOldTransaction: Boolean = false,
    val lines: List<CustomerReceiptLine> = emptyList(),
    val originalTotal: Long,
    val refundAmount: Long,
    val isCopy: Boolean = false,
    val reprintInfo: String? = null
)

/**
 * Penyusun format struk pengembalian dana (refund tunai) dan pembatalan (void) termal 58mm (ESC/POS).
 * Sesuai aturan COR-03, COR-04, COR-05, PRT-01, PRT-03, dan PRT-05:
 * - Dicetak di kertas thermal roll 58 mm (lebar 32 karakter).
 * - Menampilkan alasan pembatalan dan otorisasi manager (approved_by).
 * - Mencatat refund selalu tunai (uang keluar dari laci kas kasir).
 * - Menandai [REFUND TRANSAKSI SHIFT LALU] jika transaksi berasal dari shift lampau.
 * - Berlabel *** SALINAN *** jika merupakan cetak ulang.
 */
object RefundReceiptFormatter {

    fun format(
        data: RefundReceiptData,
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
            if (!data.reprintInfo.isNullOrBlank()) {
                builder.textLine(data.reprintInfo)
            }
            builder.feed(1)
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

        // 3. Judul Dokumen Koreksi
        builder.alignCenter()
            .boldOn()
            .textLine("*** ${data.correctionType.uppercase()} ***")
            .boldOff()

        if (data.isOldTransaction) {
            builder.boldOn()
                .textLine("[REFUND TRANSAKSI SHIFT LALU]")
                .boldOff()
        }
        builder.feed(1)

        // 4. Info Koreksi & Audit
        builder.alignLeft()
            .twoColumns("No. Order Asal:", data.orderDisplayNumber)
        if (!data.tableOrQueueLabel.isNullOrBlank()) {
            builder.twoColumns("Meja / Antrean:", data.tableOrQueueLabel)
        }
        builder.twoColumns("Kasir:", data.cashierName)
            .twoColumns("Disetujui:", data.approvedBy)
            .twoColumns("Waktu:", data.occurredAt)
            .twoColumns("Alasan:", data.reason)

        if (!data.note.isNullOrBlank()) {
            builder.textLine("Catatan: ${data.note}")
        }
        builder.divider('-')

        // 5. Rincian Item (jika ada)
        if (data.lines.isNotEmpty()) {
            data.lines.forEach { line ->
                builder.twoColumns("${line.quantity}x ${line.name}", formatRupiah(line.lineTotal))
                if (!line.optionsOrNote.isNullOrBlank()) {
                    builder.textLine("   + ${line.optionsOrNote}")
                }
            }
            builder.divider('-')
        }

        // 6. Ringkasan Keuangan
        builder.twoColumns("Total Order Asal", formatRupiah(data.originalTotal))
        builder.divider('=')

        builder.boldOn()
            .textSize(widthMultiplier = 1, heightMultiplier = 2)
            .twoColumns("DANA KELUAR", formatRupiah(data.refundAmount))
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
        builder.twoColumns("Metode Pengembalian", "TUNAI (Laci Kas)")

        builder.divider('=')

        // 7. Footer Pengesahan
        builder.alignCenter()
            .textLine("Dana tunai telah diserahkan")
            .textLine("kepada pelanggan.")
            .textLine("Simpan struk ini sebagai bukti.")
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
