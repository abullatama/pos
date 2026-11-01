package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder

data class EmergencySopData(
    val storeName: String = "AA COFFEESHOP / ATHIA LAUNDRY",
    val emergencyPhone: String = "0823-4778-5986",
    val printedAt: String? = null
)

/**
 * Penyusun format cetak Prosedur / SOP Darurat Kasir 58 mm (ESC/POS).
 *
 * Mengimplementasikan aturan OPS-02, spek 06 (Prosedur Darurat Kasir), spek 18, dan F44:
 * - 4 Skenario darurat utama: Tablet mati, Printer mati, Internet putus, WA gagal.
 * - Format ringkas pas 32 karakter per baris untuk ditempel di meja kasir / laci kas.
 * - Menyertakan protokol Buku Nota Manual dan penandaan transaksi input susulan.
 * - Menyertakan larangan kritis (dilarang reset/logout saat offline) dan kontak darurat.
 */
object EmergencySopFormatter {

    fun format(
        data: EmergencySopData = EmergencySopData(),
        charsPerLine: Int = 32
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        builder.reset()
            .alignCenter()
            .boldOn()
            .textSize(1, 2)
            .textLine("SOP DARURAT KASIR")
            .textSize(1, 1)
            .textLine(data.storeName.uppercase())
            .boldOff()
            .textLine("Dicetak: ${data.printedAt ?: "Siap Tempel"}")
            .divider('=')

        builder.alignLeft()

        // 1. Tablet Mati / Rusak
        builder.boldOn().textLine("1. TABLET MATI / RUSAK").boldOff()
        builder.textLine(" - Gunakan Buku Nota Manual")
        builder.textLine("   bernomor urut (NCR).")
        builder.textLine(" - Catat: tgl, jam, item,")
        builder.textLine("   qty, total & metode bayar.")
        builder.textLine(" - Beri rangkap putih ke tamu.")
        builder.textLine(" - Saat tablet normal, lakukan")
        builder.textLine("   INPUT SUSULAN ke POS dan")
        builder.textLine("   beri catatan:")
        builder.boldOn()
        builder.textLine("   \"Input Susulan Nota #...\"")
        builder.boldOff()
        builder.divider('-')

        // 2. Printer Mati / Habis Kertas
        builder.boldOn().textLine("2. PRINTER MATI / KERTAS HABIS").boldOff()
        builder.textLine(" - Transaksi TETAP LANJUT.")
        builder.textLine(" - Terima Tunai / QRIS normal.")
        builder.textLine(" - Struk antre otomatis.")
        builder.textLine(" - Setelah printer nyala, struk")
        builder.textLine("   dapat dicetak ulang via")
        builder.textLine("   tab Riwayat Selesai.")
        builder.divider('-')

        // 3. Internet / Wi-Fi Mati
        builder.boldOn().textLine("3. INTERNET / WI-FI PUTUS").boldOff()
        builder.textLine(" - POS bekerja OFFLINE penuh.")
        builder.textLine(" - Tunai & QRIS Statis NORMAL.")
        builder.textLine(" - Shift tetap bisa ditutup.")
        builder.boldOn()
        builder.textLine(" - DILARANG LOGOUT / RESET!")
        builder.boldOff()
        builder.textLine(" - Data tersimpan di outbox &")
        builder.textLine("   sinkron saat online lagi.")
        builder.divider('-')

        // 4. WhatsApp / Notifikasi Gagal
        builder.boldOn().textLine("4. WHATSAPP / NOTIFIKASI GAGAL").boldOff()
        builder.textLine(" - Buka detail cucian di POS.")
        builder.textLine(" - Tekan tombol \"Kirim via WA\".")
        builder.textLine(" - Kirim manual via WhatsApp HP.")
        builder.divider('=')

        // Catatan Wajib & Kontak
        builder.boldOn().textLine("ATURAN KRITIS KASIR:").boldOff()
        builder.textLine("1. Refund SELALU TUNAI.")
        builder.textLine("2. QRIS: Cek Soundbox/mutasi.")
        builder.textLine("3. JANGAN RESET/CLEAR DATA APP.")
        builder.divider('-')

        builder.alignCenter()
        builder.boldOn()
        builder.textLine("KONTAK DARURAT OWNER/TEKNISI:")
        builder.textSize(1, 2)
        builder.textLine(data.emergencyPhone)
        builder.textSize(1, 1)
        builder.boldOff()
        builder.divider('=')

        builder.feed(5)
        builder.cut(partial = true)

        return builder.build()
    }
}
