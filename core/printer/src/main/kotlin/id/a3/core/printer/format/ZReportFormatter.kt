package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class ZReportData(
    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val shiftId: String,
    val cashierName: String,
    val openedAt: String,
    val closedAt: String,
    // Ringkasan Penjualan (TUP-05)
    val grossSales: Long,
    val totalDiscount: Long = 0L,
    val totalRefund: Long = 0L,
    val netSales: Long,
    val transactionCount: Int,
    // Rincian Metode Bayar (TUP-05)
    val cashSales: Long,
    val cashDpAndSettlement: Long = 0L,
    val qrisSales: Long,
    // Pengendalian Kas (TUP-03, Test Vector K1)
    val openingCash: Long,
    val cashOuts: Long = 0L,
    val expectedCash: Long,
    val countedCash: Long,
    val cashDifference: Long,
    val differenceReason: String? = null,
    // Alokasi Kas (TUP-06)
    val cashLeft: Long,
    val cashDeposited: Long,
    val cashTakenBy: String? = null,
    // Laundry Belum Lunas (TUP-05)
    val unpaidLaundryCount: Int = 0,
    val unpaidLaundryTotal: Long = 0L,
    // Cetak Ulang Salinan (PRT-05)
    val isCopy: Boolean = false,
    val reprintInfo: String? = null
)

/**
 * Penyusun format cetak Laporan Z penutupan shift termal 58mm (ESC/POS).
 *
 * Mengimplementasikan aturan TUP-05, TUP-06, PRT-03, dan PRT-05:
 * - Dicetak di kertas roll thermal 58 mm (lebar 32 karakter).
 * - Tanpa data modal dan margin laba kotor.
 * - Memuat penjualan kotor, diskon, refund, penjualan bersih, rincian per metode (Tunai, QRIS),
 *   pengendalian kas (K1), selisih, dan alokasi kas ditinggal/disetor.
 * - Tanda *** SALINAN LAPORAN Z *** jika dicetak ulang.
 */
object ZReportFormatter {

    fun format(
        data: ZReportData,
        charsPerLine: Int = 32
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        builder.reset()

        // 1. Tanda Cetak Ulang Salinan (PRT-05)
        if (data.isCopy) {
            builder.alignCenter()
                .boldOn()
                .textLine("*** SALINAN LAPORAN Z ***")
                .boldOff()
            if (!data.reprintInfo.isNullOrBlank()) {
                builder.textLine(data.reprintInfo)
            }
            builder.feed(1)
        }

        // 2. Header Toko & Judul Laporan
        builder.alignCenter()
            .boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine(data.storeName)
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
            .textLine(data.storeAddress)
            .textLine("Telp: ${data.storePhone}")
            .divider('=')
            .boldOn()
            .textSize(widthMultiplier = 1, heightMultiplier = 2)
            .textLine("LAPORAN Z (TUTUP SHIFT)")
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
            .divider('=')

        // 3. Metadata Shift
        builder.alignLeft()
            .twoColumns("Kasir Bertugas:", data.cashierName)
            .twoColumns("Buka Shift:", data.openedAt)
            .twoColumns("Tutup Shift:", data.closedAt)
            .twoColumns("ID Shift:", data.shiftId.take(12))
            .divider('-')

        // 4. Ringkasan Penjualan (TUP-05)
        builder.boldOn()
            .textLine("RINGKASAN PENJUALAN:")
            .boldOff()
            .twoColumns("Penjualan Kotor", formatRupiah(data.grossSales))

        if (data.totalDiscount > 0L) {
            builder.twoColumns("Total Diskon", "-${formatRupiah(data.totalDiscount)}")
        }

        if (data.totalRefund > 0L) {
            builder.twoColumns("Total Refund/Void", "-${formatRupiah(data.totalRefund)}")
        }

        builder.divider('-')
            .boldOn()
            .twoColumns("Penjualan Bersih", formatRupiah(data.netSales))
            .boldOff()
            .twoColumns("Total Transaksi", "${data.transactionCount} nota")
            .divider('-')

        // 5. Rincian Metode Pembayaran (TUP-05)
        builder.boldOn()
            .textLine("RINCIAN METODE BAYAR:")
            .boldOff()
            .twoColumns("Tunai (Penjualan)", formatRupiah(data.cashSales))

        if (data.cashDpAndSettlement > 0L) {
            builder.twoColumns("Tunai (DP/Pelunasan)", formatRupiah(data.cashDpAndSettlement))
        }

        builder.twoColumns("QRIS Statis", formatRupiah(data.qrisSales))
            .divider('-')

        // 6. Pengendalian Kas Fisik Laci (Test Vector K1)
        builder.boldOn()
            .textLine("PENGENDALIAN KAS (LACI):")
            .boldOff()
            .twoColumns("Kas Awal Shift", formatRupiah(data.openingCash))
            .twoColumns("Total Masuk (Tunai)", formatRupiah(data.cashSales + data.cashDpAndSettlement))

        if (data.cashOuts > 0L) {
            builder.twoColumns("Kas Keluar", "-${formatRupiah(data.cashOuts)}")
        }

        if (data.totalRefund > 0L) {
            builder.twoColumns("Refund Tunai", "-${formatRupiah(data.totalRefund)}")
        }

        builder.divider('-')
            .boldOn()
            .twoColumns("Kas Seharusnya", formatRupiah(data.expectedCash))
            .twoColumns("Kas Dihitung Fisik", formatRupiah(data.countedCash))
            .boldOff()

        val diffLabel = when {
            data.cashDifference == 0L -> "Nol (Sesuai)"
            data.cashDifference > 0L -> "+${formatRupiah(data.cashDifference)}"
            else -> formatRupiah(data.cashDifference)
        }
        builder.twoColumns("Selisih Kas", diffLabel)

        if (!data.differenceReason.isNullOrBlank()) {
            builder.textLine("Alasan Selisih:")
            builder.textLine("  ${data.differenceReason}")
        }

        builder.divider('-')

        // 7. Alokasi Kas (TUP-06)
        builder.boldOn()
            .textLine("ALOKASI KAS FISIK:")
            .boldOff()
            .twoColumns("Ditinggal di Laci", formatRupiah(data.cashLeft))
            .twoColumns("Disetor ke Owner", formatRupiah(data.cashDeposited))

        if (!data.cashTakenBy.isNullOrBlank()) {
            builder.twoColumns("Uang Dibawa oleh", data.cashTakenBy)
        }

        // 8. Order Laundry Belum Lunas jika ada (TUP-05)
        if (data.unpaidLaundryCount > 0) {
            builder.divider('-')
                .boldOn()
                .textLine("CUKUP BELUM LUNAS:")
                .boldOff()
                .twoColumns("Pesanan Laundry", "${data.unpaidLaundryCount} nota")
                .twoColumns("Total Belum Tertagih", formatRupiah(data.unpaidLaundryTotal))
        }

        builder.divider('=')
            .alignCenter()
            .textLine("*** LAPORAN Z SELESAI ***")
            .textLine("Sistem Kasir A3 POS Offline")
            .feed(5)

        return builder.build()
    }

    private fun formatRupiah(amount: Long): String {
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val formatter = DecimalFormat("#,###", symbols)
        return if (amount < 0) {
            "-Rp " + formatter.format(-amount)
        } else {
            "Rp " + formatter.format(amount)
        }
    }
}
