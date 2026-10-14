package id.a3.core.domain.cash

object CashCalculator {

    /**
     * Menghitung kas seharusnya saat tutup shift:
     * kas = kas_awal + penjualan_tunai + dp_dan_pelunasan_tunai - refund_tunai - kas_keluar
     * Catatan: QRIS tidak masuk laci, jadi tidak ikut hitungan kas fisik.
     */
    fun calculateExpectedCash(input: ShiftCashInput): Long {
        return input.openingCash +
            input.cashSales +
            input.cashDpAndSettlement -
            input.cashRefunds -
            input.cashOuts
    }

    /**
     * Menghitung kembalian uang tunai:
     * change = tendered - total
     */
    fun calculateChange(total: Long, tendered: Long): Long {
        require(tendered >= total) {
            "Uang yang diterima (Rp$tendered) kurang dari total tagihan (Rp$total)"
        }
        return tendered - total
    }
}
