package id.a3.core.printer

import id.a3.core.printer.format.LaundryTagData
import id.a3.core.printer.format.LaundryTagFormatter
import id.a3.core.printer.format.LaundryTagLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaundryTagFormatterTest {

    @Test
    fun `laundry tag contains store header, display number, customer, and items without price`() {
        val tagData = LaundryTagData(
            orderId = "00000000-0000-4000-8000-000000000012",
            displayNumber = "L1-0012",
            customerName = "Budi Santoso",
            customerPhone = "0812 3456 7890",
            receivedAt = "18/10/2026 14:00",
            estimatedCompletion = "20/10/2026 14:00",
            isExpress = true,
            lines = listOf(
                LaundryTagLine(
                    name = "Kiloan Reguler",
                    quantityOrWeight = "4,0 kg",
                    isExpress = true,
                    note = "Kemeja putih ada noda tinta di saku"
                ),
                LaundryTagLine(
                    name = "Selimut Tebal",
                    quantityOrWeight = "1 pcs",
                    isExpress = false,
                    note = null
                )
            ),
            cashierOrStaffName = "Siti Rahma"
        )

        val bytes = LaundryTagFormatter.format(tagData)
        val output = String(bytes, Charsets.ISO_8859_1)

        // Verifikasi Header & Nomor Order
        assertTrue(output.contains("ATHIA LAUNDRY HOUSE"))
        assertTrue(output.contains("TAG CUCIAN"))
        assertTrue(output.contains("L1-0012"))
        assertTrue(output.contains(">>> LAYANAN EXPRESS <<<"))

        // Verifikasi Data Pelanggan & Waktu
        assertTrue(output.contains("Budi Santoso"))
        assertTrue(output.contains("0812 3456 7890"))
        assertTrue(output.contains("18/10/2026 14:00"))
        assertTrue(output.contains("Siti Rahma"))

        // Verifikasi Rincian Layanan & Catatan Khusus
        assertTrue(output.contains("Kiloan Reguler [EXPRESS]"))
        assertTrue(output.contains("4,0 kg"))
        assertTrue(output.contains("Kemeja putih ada noda tinta di saku"))
        assertTrue(output.contains("Selimut Tebal"))
        assertTrue(output.contains("1 pcs"))

        // Verifikasi Payload QR Code & Label
        assertTrue(output.contains("00000000-0000-4000-8000-000000000012"))
        assertTrue(output.contains("PINDAI UNTUK UBAH STATUS"))
        assertTrue(output.contains("*** TANPA HARGA ***"))

        // Verifikasi Ketat: Bebas dari harga Rupiah sama sekali (PRT-03)
        assertFalse("Tag fisik laundry dilarang memuat nominal Rp", output.contains("Rp"))
        assertFalse("Tag fisik laundry dilarang memuat subtotal", output.contains("Subtotal"))
        assertFalse("Tag fisik laundry dilarang memuat total biaya", output.contains("Total"))
    }
}
