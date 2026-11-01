package id.a3.core.printer

import id.a3.core.printer.format.EmergencySopData
import id.a3.core.printer.format.EmergencySopFormatter
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySopFormatterTest {

    @Test
    fun `emergency sop slip contains all 4 emergency scenarios`() {
        val bytes = EmergencySopFormatter.format(
            EmergencySopData(
                storeName = "AA Coffeeshop",
                emergencyPhone = "0823-4778-5986",
                printedAt = "2026-11-01 10:00"
            )
        )

        val output = String(bytes, Charsets.ISO_8859_1)

        // Verifikasi Header
        assertTrue(output.contains("SOP DARURAT KASIR"))
        assertTrue(output.contains("AA COFFEESHOP"))

        // Verifikasi 4 Skenario Darurat (spek 06)
        assertTrue(output.contains("1. TABLET MATI / RUSAK"))
        assertTrue(output.contains("Buku Nota Manual"))
        assertTrue(output.contains("Input Susulan Nota #"))

        assertTrue(output.contains("2. PRINTER MATI / KERTAS HABIS"))
        assertTrue(output.contains("Transaksi TETAP LANJUT"))

        assertTrue(output.contains("3. INTERNET / WI-FI PUTUS"))
        assertTrue(output.contains("POS bekerja OFFLINE penuh"))
        assertTrue(output.contains("DILARANG LOGOUT / RESET!"))

        assertTrue(output.contains("4. WHATSAPP / NOTIFIKASI GAGAL"))
        assertTrue(output.contains("Kirim via WA"))
    }

    @Test
    fun `emergency sop slip contains critical cashier rules and contact`() {
        val bytes = EmergencySopFormatter.format(
            EmergencySopData(
                storeName = "Athia Laundry House",
                emergencyPhone = "0823-4778-5986"
            )
        )

        val output = String(bytes, Charsets.ISO_8859_1)

        // Verifikasi Aturan Kritis
        assertTrue(output.contains("ATURAN KRITIS KASIR"))
        assertTrue(output.contains("Refund SELALU TUNAI"))
        assertTrue(output.contains("QRIS: Cek Soundbox/mutasi"))
        assertTrue(output.contains("JANGAN RESET/CLEAR DATA APP"))

        // Verifikasi Kontak Darurat
        assertTrue(output.contains("KONTAK DARURAT OWNER/TEKNISI"))
        assertTrue(output.contains("0823-4778-5986"))
    }

    @Test
    fun `emergency sop lines do not exceed standard 32 chars per line`() {
        val bytes = EmergencySopFormatter.format()
        val text = String(bytes, Charsets.ISO_8859_1)
        val lines = text.split("\n")

        for (line in lines) {
            // Hilangkan kode kontrol dan parameter perintah ESC/POS (misal ESC E 0, ESC a 1, ESC !, GS !)
            val cleanLine = line
                .replace(Regex("\\x1B[@Eadp].?"), "")
                .replace(Regex("\\x1D[!vV].?"), "")
                .replace(Regex("[\\x00-\\x1F\\x7F-\\x9F]"), "")
            assertTrue("Baris melebihi 32 karakter: '$cleanLine' (${cleanLine.length})", cleanLine.length <= 32)
        }
    }
}
