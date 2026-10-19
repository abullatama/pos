package id.a3.core.printer

import id.a3.core.printer.format.CustomerReceiptLine
import id.a3.core.printer.format.RefundReceiptData
import id.a3.core.printer.format.RefundReceiptFormatter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundReceiptFormatterTest {

    private val sampleRefund = RefundReceiptData(
        storeName = "AA COFFEESHOP",
        storeAddress = "Jl. Kopi Pilihan No. 3",
        storePhone = "0812-3456-7890",
        orderDisplayNumber = "C1-0012",
        tableOrQueueLabel = "Meja 04",
        cashierName = "Budi Kasir",
        approvedBy = "Manager Anto (u-manager-1)",
        occurredAt = "19/10/2026 15:30",
        correctionType = "REFUND TUNAI",
        reason = "Komplain kualitas",
        note = "Kopi tumpah oleh staf",
        isOldTransaction = false,
        lines = listOf(
            CustomerReceiptLine(
                name = "Cappuccino",
                quantity = 1,
                unitPrice = 28000L,
                lineTotal = 28000L
            )
        ),
        originalTotal = 28000L,
        refundAmount = 28000L
    )

    @Test
    fun `format generates valid non-empty byte array with refund details`() {
        val bytes = RefundReceiptFormatter.format(sampleRefund)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("AA COFFEESHOP"))
        assertTrue(text.contains("REFUND TUNAI"))
        assertTrue(text.contains("C1-0012"))
        assertTrue(text.contains("Komplain kualitas"))
        assertTrue(text.contains("DANA KELUAR"))
        assertTrue(text.contains("TUNAI"))
    }

    @Test
    fun `format includes watermark on copy reprint`() {
        val copyRefund = sampleRefund.copy(
            isCopy = true,
            reprintInfo = "Dicetak ulang 19/10/2026 16:00"
        )
        val bytes = RefundReceiptFormatter.format(copyRefund)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("*** SALINAN ***"))
        assertTrue(text.contains("Dicetak ulang"))
    }

    @Test
    fun `format includes tag for old transaction refund`() {
        val oldTxRefund = sampleRefund.copy(
            isOldTransaction = true
        )
        val bytes = RefundReceiptFormatter.format(oldTxRefund)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("[REFUND TRANSAKSI SHIFT LALU]"))
    }
}
