package id.a3.core.printer

import id.a3.core.printer.format.KitchenTicketFormatter
import id.a3.core.printer.format.KitchenTicketItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenTicketFormatterTest {

    @Test
    fun `kitchen ticket contains header and new items without price`() {
        val items = listOf(
            KitchenTicketItem(
                name = "Caffe Latte",
                quantity = 2,
                optionsSummary = "Dingin, Shot Tambahan",
                note = "Gula aren dipisah"
            ),
            KitchenTicketItem(
                name = "Americano",
                quantity = 1,
                optionsSummary = "Panas",
                note = null
            )
        )

        val bytes = KitchenTicketFormatter.format(
            orderDisplayNumber = "C1-0007",
            tableLabel = "Meja 05",
            occurredAt = "14:32 WIB",
            items = items,
            orderType = "Dine In",
            staffName = "Barista 1"
        )

        val output = String(bytes, Charsets.ISO_8859_1)

        // Verifikasi judul dan nomor meja
        assertTrue(output.contains("TIKET DAPUR"))
        assertTrue(output.contains("MEJA 05"))
        assertTrue(output.contains("C1-0007"))

        // Verifikasi item baru yang dicetak
        assertTrue(output.contains("2x Caffe Latte"))
        assertTrue(output.contains("+ Dingin, Shot Tambahan"))
        assertTrue(output.contains("Catatan: Gula aren dipisah"))
        assertTrue(output.contains("1x Americano"))

        // Verifikasi banner khusus tanpa harga (PRT-03 & F18)
        assertTrue(output.contains("*** TANPA HARGA ***"))

        // Pastikan TIDAK ADA tanda mata uang atau harga sama sekali
        assertFalse("Tiket dapur tidak boleh mencantumkan Rp", output.contains("Rp"))
        assertFalse("Tiket dapur tidak boleh mencantumkan subtotal", output.contains("Subtotal"))
        assertFalse("Tiket dapur tidak boleh mencantumkan total", output.contains("Total:"))
    }
}
