package id.a3.core.printer

import id.a3.core.printer.format.ZReportData
import id.a3.core.printer.format.ZReportFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZReportFormatterTest {

    private val sampleZReport = ZReportData(
        storeName = "A3 COFFEESHOP",
        storeAddress = "Jl. Trans Sulawesi Kelurahan Bintauna",
        storePhone = "0823-4778-5986",
        shiftId = "00000000-0000-4000-8000-000000000001",
        cashierName = "Budi Santoso",
        openedAt = "19-10-2026 08:00",
        closedAt = "19-10-2026 16:00",
        grossSales = 600000L,
        totalDiscount = 10000L,
        totalRefund = 25000L,
        netSales = 565000L,
        transactionCount = 18,
        cashSales = 500000L,
        cashDpAndSettlement = 100000L,
        qrisSales = 100000L,
        // Sesuai Test Vector K1: Kas Awal 200k + Masuk 600k - Refund 25k - Kas Keluar 50k = Kas Seharusnya 725k
        openingCash = 200000L,
        cashOuts = 50000L,
        expectedCash = 725000L,
        countedCash = 725000L,
        cashDifference = 0L,
        differenceReason = null,
        cashLeft = 200000L,
        cashDeposited = 525000L,
        cashTakenBy = "Pak Owner"
    )

    @Test
    fun `format generates valid ESC_POS bytes containing K1 figures and Z report sections`() {
        val bytes = ZReportFormatter.format(sampleZReport)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("A3 COFFEESHOP"))
        assertTrue(text.contains("Owner: DEVENTRI SILAMATITI BULUT"))
        assertTrue(text.contains("Jl. Trans Sulawesi Kelurahan Bintauna"))
        assertTrue(text.contains("0823-4778-5986"))
        assertTrue(text.contains("LAPORAN Z (TUTUP SHIFT)"))
        assertTrue(text.contains("Budi Santoso"))
        assertTrue(text.contains("RINGKASAN PENJUALAN:"))
        assertTrue(text.contains("Penjualan Bersih"))
        assertTrue(text.contains("RINCIAN METODE BAYAR:"))
        assertTrue(text.contains("QRIS Statis"))
        assertTrue(text.contains("PENGENDALIAN KAS (LACI):"))
        assertTrue(text.contains("Kas Seharusnya"))
        assertTrue(text.contains("725.000"))
        assertTrue(text.contains("ALOKASI KAS FISIK:"))
        assertTrue(text.contains("Ditinggal di Laci"))
        assertTrue(text.contains("Pak Owner"))
        assertTrue(text.contains("Rp 0 (Pas)"))
        assertFalse(text.contains("*** SALINAN ***"))
    }

    @Test
    fun `format with isCopy true includes SALINAN watermark`() {
        val copyReport = sampleZReport.copy(
            isCopy = true,
            reprintInfo = "Dicetak ulang oleh Manager"
        )
        val bytes = ZReportFormatter.format(copyReport)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("*** SALINAN LAPORAN Z ***"))
        assertTrue(text.contains("Dicetak ulang oleh Manager"))
    }

    @Test
    fun `format with difference displays difference reason`() {
        val diffReport = sampleZReport.copy(
            countedCash = 723000L,
            cashDifference = -2000L,
            differenceReason = "Uang kembalian kurang Rp2.000"
        )
        val bytes = ZReportFormatter.format(diffReport)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue(text.contains("Selisih Kas"))
        assertTrue(text.contains("-Rp 2.000"))
        assertTrue(text.contains("Uang kembalian kurang Rp2.000"))
    }
}
