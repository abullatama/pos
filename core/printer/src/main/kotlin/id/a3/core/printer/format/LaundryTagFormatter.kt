package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder

data class LaundryTagLine(
    val name: String,
    val quantityOrWeight: String,
    val isExpress: Boolean = false,
    val note: String? = null
)

data class LaundryTagData(
    val orderId: String,
    val displayNumber: String,
    val customerName: String,
    val customerPhone: String? = null,
    val receivedAt: String,
    val estimatedCompletion: String? = null,
    val isExpress: Boolean = false,
    val lines: List<LaundryTagLine>,
    val cashierOrStaffName: String? = null,
    val isCopy: Boolean = false,
    val reprintInfo: String? = null
)

/**
 * Penyusun format cetak tag cucian termal 58mm (ESC/POS).
 *
 * Sesuai aturan LDR-06, PRT-03, dan D-08:
 * - Dicetak di kertas roll thermal 58 mm.
 * - Memuat nomor order besar (misal: L1-0012) yang mudah dibaca dan ditulis tangan.
 * - Memuat QR code terpusat (128x128 raster bitmap) untuk dipindai kamera tablet (L07).
 * - Tanpa harga sama sekali (dilarang memuat nominal rupiah pada tag pakaian fisik).
 * - Memuat nama pelanggan, waktu masuk, estimasi selesai, dan catatan khusus noda/rapuh.
 */
object LaundryTagFormatter {

    fun format(
        data: LaundryTagData,
        charsPerLine: Int = 32
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        // 1. Header Toko & Tag Cucian
        builder.reset()

        if (data.isCopy) {
            builder.alignCenter()
                .boldOn()
                .textLine("*** SALINAN TAG CUCIAN ***")
                .boldOff()
            if (!data.reprintInfo.isNullOrBlank()) {
                builder.textLine(data.reprintInfo)
            }
            builder.feed(1)
        }

        builder.alignCenter()
            .boldOn()
            .textLine("ATHIA LAUNDRY HOUSE")
            .textSize(widthMultiplier = 1, heightMultiplier = 2)
            .textLine("TAG CUCIAN")
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
            .divider('=')

        // 2. Nomor Order Ukuran Ganda (Besar & Jelas)
        builder.alignCenter()
            .boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine(data.displayNumber)
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()

        if (data.isExpress) {
            builder.boldOn()
                .textLine(">>> LAYANAN EXPRESS <<<")
                .boldOff()
        }

        builder.divider('-')

        // 3. Metadata Pelanggan & Waktu
        builder.alignLeft()
            .twoColumns("Pelanggan:", data.customerName.take(18))

        if (!data.customerPhone.isNullOrBlank()) {
            builder.twoColumns("No. HP:", data.customerPhone)
        }

        builder.twoColumns("Masuk:", data.receivedAt)

        if (!data.estimatedCompletion.isNullOrBlank()) {
            builder.twoColumns("Estimasi:", data.estimatedCompletion)
        }

        if (!data.cashierOrStaffName.isNullOrBlank()) {
            builder.twoColumns("Staf/Kasir:", data.cashierOrStaffName)
        }

        builder.divider('-')

        // 4. Rincian Layanan & Pakaian (Tanpa Harga!)
        builder.boldOn()
            .textLine("RINCIAN PAKAIAN:")
            .boldOff()

        data.lines.forEach { line ->
            val expressBadge = if (line.isExpress) " [EXPRESS]" else ""
            builder.boldOn()
                .textLine("- ${line.name}$expressBadge")
                .boldOff()

            builder.textLine("  Jumlah/Berat: ${line.quantityOrWeight}")

            if (!line.note.isNullOrBlank()) {
                builder.textLine("  Catatan: ${line.note}")
            }
        }

        builder.divider('=')

        // 5. QR Code untuk Scan Status (L07)
        builder.alignCenter()
            .textLine("PINDAI UNTUK UBAH STATUS:")
            .feed(1)
            .qrCodeBitmap(data.orderId, size = 128)
            .feed(1)
            .textLine(data.orderId)
            .feed(1)
            .textLine("*** KERTAS ROLL 58MM ATHIA ***")
            .textLine("*** TANPA HARGA ***")
            .feed(5)

        return builder.build()
    }
}
