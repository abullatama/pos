package id.a3.core.printer.format

import android.graphics.Bitmap
import id.a3.core.printer.escpos.EscPosBuilder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CashOutReceiptData(
    val storeName: String = "A3 COFFEESHOP",
    val ownerName: String? = "DEVENTRI SILAMATITI BULUT",
    val storeAddress: String = "Jl. Trans Sulawesi Kelurahan Bintauna",
    val storePhone: String = "0823-4778-5986",
    val logoBitmap: Bitmap? = null,
    val cashoutId: String,
    val shiftId: String,
    val cashierName: String,
    val occurredAt: String,
    val category: String,
    val reason: String,
    val amount: Long,
    val hasPhotoReceipt: Boolean = false,
    val approvedBy: String? = null,
    val isCopy: Boolean = false,
    val reprintInfo: String? = null
)

/**
 * Penyusun format cetak bukti pengeluaran kas laci (petty cash / kas keluar) termal 58mm (ESC/POS).
 *
 * Mengimplementasikan aturan KAS-01, KAS-02, PRT-01, PRT-03, dan PRT-05:
 * - Dicetak di kertas roll thermal 58 mm (lebar 32 karakter).
 * - Menampilkan rincian pengeluaran, kategori, alasan, dan otorisasi manager (jika di atas batas).
 * - Menampilkan status foto bukti nota fisik.
 * - Berlabel *** SALINAN *** jika merupakan cetak ulang.
 */
object CashOutReceiptFormatter {

    fun format(
        data: CashOutReceiptData,
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

        if (data.logoBitmap != null) {
            builder.rasterCenteredBitmap(data.logoBitmap)
            builder.feed(1)
        }

        builder.boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine(data.storeName)
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
        if (!data.ownerName.isNullOrBlank()) {
            builder.textLine("Owner: ${data.ownerName}")
        }
        builder.textLine(data.storeAddress)
            .textLine("Telp: ${data.storePhone}")
            .divider('=')

        // 3. Judul Dokumen
        builder.alignCenter()
            .boldOn()
            .textLine("*** BUKTI KAS KELUAR ***")
            .boldOff()
            .feed(1)

        // 4. Informasi Detail
        builder.alignLeft()
            .twoColumns("No. Bukti:", data.cashoutId.take(8).uppercase())
            .twoColumns("Waktu:", data.occurredAt)
            .twoColumns("Kasir:", data.cashierName)
            .twoColumns("Kategori:", data.category)
            .twoColumns("Alasan:", data.reason)

        if (!data.approvedBy.isNullOrBlank()) {
            builder.twoColumns("Disetujui:", data.approvedBy)
        }

        val photoStatus = if (data.hasPhotoReceipt) "Ada (Terlampir)" else "Tidak Ada"
        builder.twoColumns("Bukti Nota:", photoStatus)

        builder.divider('=')

        // 5. Total Dana Keluar
        builder.boldOn()
            .textSize(widthMultiplier = 1, heightMultiplier = 2)
            .twoColumns("TOTAL KELUAR", formatRupiah(data.amount))
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
        builder.twoColumns("Sumber Dana:", "TUNAI (Laci Kas)")

        builder.divider('=')

        // 6. Tanda Pengesahan
        builder.feed(1)
            .alignCenter()
            .textLine("Simpan struk ini bersama nota asli")
            .textLine("di dalam laci kas.")
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
