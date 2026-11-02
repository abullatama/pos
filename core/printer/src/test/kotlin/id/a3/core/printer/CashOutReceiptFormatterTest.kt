package id.a3.core.printer

import id.a3.core.printer.format.CashOutReceiptData
import id.a3.core.printer.format.CashOutReceiptFormatter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashOutReceiptFormatterTest {

    private val sampleCashOut = CashOutReceiptData(
        storeName = "A3 COFFEESHOP",
        storeAddress = "Jl. Trans Sulawesi Kelurahan Bintauna",
        storePhone = "0823-4778-5986",
        cashoutId = "00000000-0000-4000-8000-000000000052",
        shiftId = "shift-001",
        cashierName = "Budi Kasir",
        occurredAt = "19/10/2026 14:00",
        category = "Operasional",
        reason = "Beli es batu kristal",
        amount = 30000L,
        hasPhotoReceipt = false,
        approvedBy = null
    )

    @Test
    fun `format generates valid cash out receipt text`() {
        val bytes = CashOutReceiptFormatter.format(sampleCashOut)
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("A3 COFFEESHOP"))
        assertTrue(text.contains("Owner: DEVENTRI SILAMATITI BULUT"))
        assertTrue(text.contains("Jl. Trans Sulawesi Kelurahan Bintauna"))
        assertTrue(text.contains("0823-4778-5986"))
        assertTrue(text.contains("BUKTI KAS KELUAR"))
        assertTrue(text.contains("Beli es batu kristal"))
        assertTrue(text.contains("TOTAL KELUAR"))
        assertTrue(text.contains("Rp30.000"))
        assertTrue(text.contains("TUNAI"))
    }

    @Test
    fun `format includes manager approval and photo status when present`() {
        val approvedCashOut = sampleCashOut.copy(
            amount = 75000L,
            hasPhotoReceipt = true,
            approvedBy = "Manager Anto (u-manager-1)",
            isCopy = true,
            reprintInfo = "Salinan dicetak 19/10/2026 15:00"
        )
        val bytes = CashOutReceiptFormatter.format(approvedCashOut)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN ***"))
        assertTrue(text.contains("Manager Anto"))
        assertTrue(text.contains("Ada (Terlampir)"))
        assertTrue(text.contains("Rp75.000"))
    }
}
