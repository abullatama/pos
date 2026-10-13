package id.a3.pos.printer

object TestReceiptGenerator {

    /**
     * Menghasilkan byte cetak untuk pengujian spike iWare C58BT / RPP02N:
     * - Header toko
     * - Teks batas 32 karakter (verifikasi fisik lebar kertas 58 mm)
     * - Item transaksi tiruan (2 kolom rapi)
     * - Total & metode bayar
     * - QR Code via Raster Bitmap (GS v 0) agar kompatibel penuh dengan printer 58mm murah
     * - Feed baris yang cukup agar melewati cutter / tear-bar
     */
    fun createSpikeReceipt(
        charsPerLine: Int = 32,
        includeQr: Boolean = true,
        includeCut: Boolean = true,
        testCashDrawer: Boolean = false
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        builder.reset()

        if (testCashDrawer) {
            builder.openCashDrawer()
        }

        // Header Toko
        builder.alignCenter()
            .boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine("AA COFFEESHOP")
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .boldOff()
            .textLine("Jl. Pengujian No. 1, M0 Spike")
            .textLine("Telp: 0812-3456-7890")
            .divider('=')

        // Info transaksi
        builder.alignLeft()
            .twoColumns("No. Order:", "C1-0001")
            .twoColumns("Kasir:", "Budi (Shift 1)")
            .twoColumns("Waktu:", "2026-10-12 14:30")
            .divider('-')

        // Item
        builder.twoColumns("1x Espresso Single", "Rp18.000")
        builder.twoColumns("1x Caffe Latte (Oat)", "Rp28.000")
        builder.divider('-')

        // Ringkasan
        builder.twoColumns("Subtotal", "Rp46.000")
        builder.twoColumns("Diskon (0%)", "Rp0")
        builder.boldOn()
        builder.twoColumns("TOTAL", "Rp46.000")
        builder.boldOff()
        builder.twoColumns("Bayar (QRIS Statis)", "Rp46.000")
        builder.twoColumns("Kembalian", "Rp0")
        builder.divider('=')

        // Verifikasi batas lebar 32 karakter
        builder.alignCenter()
            .textLine("--- UJI 32 KARAKTER PERSIS ---")
            .textLine("12345678901234567890123456789012")
            .divider('-')

        // QR Code untuk uji scan laundry & order (via Raster Bitmap GS v 0)
        if (includeQr) {
            builder.alignCenter()
                .textLine("Pindai QR Verifikasi Order:")
                .qrCodeBitmap("ORD-20261012-C1-0001", size = 180)
                .textLine()
        }

        // Footer
        builder.alignCenter()
            .textLine("Terima Kasih atas Kunjungan Anda")
            .textLine("Simpan struk sebagai bukti")

        // Feed kertas ekstra (6 baris) agar seluruh teks dan QR keluar melewati gigi perobek kertas
        builder.feed(6)

        if (includeCut) {
            builder.cut(partial = true)
        }

        return builder.build()
    }
}
