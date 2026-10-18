package id.a3.core.printer

import id.a3.core.printer.format.CustomerReceiptData
import id.a3.core.printer.format.CustomerReceiptFormatter
import id.a3.core.printer.format.CustomerReceiptLine
import id.a3.core.printer.format.KitchenTicketFormatter
import id.a3.core.printer.format.KitchenTicketItem
import id.a3.core.printer.format.LaundryTagData
import id.a3.core.printer.format.LaundryTagFormatter
import id.a3.core.printer.format.LaundryTagLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReprintWatermarkTest {

    @Test
    fun `CustomerReceiptFormatter with isCopy true embeds SALINAN watermark and audit info (PRT-05)`() {
        val data = CustomerReceiptData(
            orderDisplayNumber = "C1-0005",
            cashierName = "Budi",
            occurredAt = "18/10/2026 14:00",
            lines = listOf(
                CustomerReceiptLine("Espresso", 1, 20000L, 20000L)
            ),
            subtotal = 20000L,
            total = 20000L,
            paymentMethodName = "Tunai",
            isCopy = true,
            reprintInfo = "Dicetak ulang: Kasir Budi · 18/10/2026 14:35"
        )

        val bytes = CustomerReceiptFormatter.format(data)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN ***"))
        assertTrue(text.contains("Dicetak ulang: Kasir Budi"))
    }

    @Test
    fun `CustomerReceiptFormatter with isCopy false does not embed SALINAN watermark`() {
        val data = CustomerReceiptData(
            orderDisplayNumber = "C1-0005",
            cashierName = "Budi",
            occurredAt = "18/10/2026 14:00",
            lines = listOf(
                CustomerReceiptLine("Espresso", 1, 20000L, 20000L)
            ),
            subtotal = 20000L,
            total = 20000L,
            paymentMethodName = "Tunai",
            isCopy = false
        )

        val bytes = CustomerReceiptFormatter.format(data)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertFalse(text.contains("*** SALINAN ***"))
    }

    @Test
    fun `KitchenTicketFormatter with isCopy true embeds SALINAN watermark while strictly omitting prices (PRT-03, PRT-05)`() {
        val bytes = KitchenTicketFormatter.format(
            orderDisplayNumber = "C1-0010",
            tableLabel = "Meja 4",
            occurredAt = "18/10/2026 14:15",
            items = listOf(
                KitchenTicketItem("Cappuccino", 2, "Less Sugar", "Gelas Kaca")
            ),
            isCopy = true,
            reprintInfo = "Salinan ke-2 oleh Kasir 1"
        )
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN TIKET DAPUR ***"))
        assertTrue(text.contains("Salinan ke-2 oleh Kasir 1"))
        assertTrue(text.contains("*** TANPA HARGA ***"))
        assertFalse(text.contains("Rp"))
    }

    @Test
    fun `LaundryTagFormatter with isCopy true embeds SALINAN watermark and preserves PRT-03 strict no price`() {
        val tagData = LaundryTagData(
            orderId = "ORD-20261018-L1-0020",
            displayNumber = "L1-0020",
            customerName = "Ibu Siti",
            receivedAt = "18/10/2026 15:00",
            lines = listOf(
                LaundryTagLine("Cuci Komplit Reguler", "3.0 kg")
            ),
            isCopy = true,
            reprintInfo = "Cetak ulang tag oleh Staf Cuci A"
        )

        val bytes = LaundryTagFormatter.format(tagData)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN TAG CUCIAN ***"))
        assertTrue(text.contains("Cetak ulang tag oleh Staf Cuci A"))
        assertTrue(text.contains("*** TANPA HARGA ***"))
        assertFalse(text.contains("Rp"))
    }
}
