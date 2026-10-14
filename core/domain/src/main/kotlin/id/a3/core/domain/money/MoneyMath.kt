package id.a3.core.domain.money

/**
 * Operasi matematika keuangan dalam satuan rupiah bulat (Long).
 *
 * Aturan bisnis:
 * 1. Seluruh uang wajib Long rupiah bulat (dilarang Float/Double untuk uang).
 * 2. Persentase per komponen dibulatkan ke rupiah terdekat, setengah naik (half-up).
 * 3. Pembulatan akhir Cafe ke kelipatan Rp100 terdekat, setengah naik (half-up).
 */
object MoneyMath {

    /**
     * Menghitung nilai persentase [percent] dari [amount] dengan pembulatan setengah naik ke rupiah bulat terdekat.
     * Rumus bilangan bulat murni: `(amount * percent * 2 + 100) / 200`
     */
    fun pct(amount: Long, percent: Int): Long {
        if (amount == 0L || percent == 0) return 0L
        return (amount * percent * 2L + 100L) / 200L
    }

    /**
     * Membulatkan nilai [amount] ke kelipatan Rp100 terdekat secara setengah naik (half-up).
     * Contoh:
     * - 61.380 -> 61.400
     * - 86.625 -> 86.600
     * - 10.050 -> 10.100
     */
    fun roundTo100(amount: Long): Long {
        return ((amount + 50L) / 100L) * 100L
    }
}
