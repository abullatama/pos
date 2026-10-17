package id.a3.core.printer

import id.a3.core.printer.format.CustomerReceiptData
import id.a3.core.printer.format.CustomerReceiptFormatter
import id.a3.core.printer.format.CustomerReceiptLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerReceiptFormatterTest {

    @Test
    fun `customer receipt contains store header, items, totals, payment, and change`() {
        val lines = listOf(
            CustomerReceiptLine(
                name = "Caffe Latte",
                quantity = 1,
                unitPrice = 34000L,
                lineTotal = 34000L,
                optionsOrNote = "Dingin, Susu Oat"
            ),
            CustomerReceiptLine(
                name = "Croissant",
                quantity = 1,
                unitPrice = 28000L,
                lineTotal = 28000L
            )
        )

        val receiptData = CustomerReceiptData(
            storeName = "A3 COFFEESHOP",
            orderDisplayNumber = "C1-0007",
            tableOrQueueLabel = "Meja 05",
            cashierName = "Budi",
            occurredAt = "18-10-2026 14:32",
            lines = lines,
            subtotal = 62000L,
            discount = 6200L,
            serviceCharge = 0L,
            tax = 5580L,
            rounding = 20L,
            total = 61400L,
            paymentMethodName = "Tunai",
            tendered = 70000L,
            change = 8600L,
            isCopy = false
        )

        val bytes = CustomerReceiptFormatter.format(receiptData)
        val text = String(bytes, Charsets.ISO_8859_1)

        // Verifikasi Header & Transaksi
        assertTrue(text.contains("A3 COFFEESHOP"))
        assertTrue(text.contains("C1-0007"))
        assertTrue(text.contains("Meja 05"))
        assertTrue(text.contains("Budi"))

        // Verifikasi Rincian Item
        assertTrue(text.contains("1x Caffe Latte"))
        assertTrue(text.contains("+ Dingin, Susu Oat"))
        assertTrue(text.contains("1x Croissant"))

        // Verifikasi Perhitungan (Lolos Test Vector C1 & K2)
        assertTrue(text.contains("Subtotal"))
        assertTrue(text.contains("Rp62.000"))
        assertTrue(text.contains("Diskon"))
        assertTrue(text.contains("-Rp6.200"))
        assertTrue(text.contains("Pajak"))
        assertTrue(text.contains("Rp5.580"))
        assertTrue(text.contains("Pembulatan"))
        assertTrue(text.contains("+Rp20"))
        assertTrue(text.contains("TOTAL"))
        assertTrue(text.contains("Rp61.400"))

        // Verifikasi Pembayaran Tunai & Kembalian
        assertTrue(text.contains("Bayar (Tunai)"))
        assertTrue(text.contains("Rp70.000"))
        assertTrue(text.contains("Kembalian"))
        assertTrue(text.contains("Rp8.600"))

        // Bukan salinan
        assertFalse(text.contains("*** SALINAN ***"))
    }

    @Test
    fun `reprinted customer receipt has SALINAN label`() {
        val receiptData = CustomerReceiptData(
            orderDisplayNumber = "C1-0007",
            occurredAt = "18-10-2026 14:32",
            lines = emptyList(),
            subtotal = 34000L,
            total = 34000L,
            paymentMethodName = "QRIS Statis",
            tendered = null,
            change = 0L,
            isCopy = true
        )

        val bytes = CustomerReceiptFormatter.format(receiptData)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN ***"))
        assertTrue(text.contains("Bayar (QRIS Statis)"))
    }
}
