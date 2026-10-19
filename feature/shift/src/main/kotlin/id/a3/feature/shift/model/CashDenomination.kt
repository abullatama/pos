package id.a3.feature.shift.model

enum class DenominationType {
    BILL, // Uang Kertas
    COIN  // Keping Uang Logam
}

data class CashDenomination(
    val value: Long,
    val label: String,
    val type: DenominationType
) {
    val apiKey: String get() = value.toString()
}

object StandardDenominations {
    const val STANDARD_OPENING_CASH: Long = 200000L // Bawaan OQ-06 Rp200.000

    val ALL: List<CashDenomination> = listOf(
        CashDenomination(100000L, "Rp 100.000", DenominationType.BILL),
        CashDenomination(50000L, "Rp 50.000", DenominationType.BILL),
        CashDenomination(20000L, "Rp 20.000", DenominationType.BILL),
        CashDenomination(10000L, "Rp 10.000", DenominationType.BILL),
        CashDenomination(5000L, "Rp 5.000", DenominationType.BILL),
        CashDenomination(2000L, "Rp 2.000", DenominationType.BILL),
        CashDenomination(1000L, "Rp 1.000", DenominationType.BILL),
        CashDenomination(500L, "Rp 500", DenominationType.COIN),
        CashDenomination(100L, "Rp 100", DenominationType.COIN)
    )

    val PRESET_200K: Map<Long, Int> = mapOf(
        50000L to 2, // 100.000
        20000L to 3, // 60.000
        10000L to 2, // 20.000
        5000L to 2,  // 10.000
        2000L to 5   // 10.000
    ) // Total: 200.000

    fun calculateTotal(breakdown: Map<Long, Int>): Long {
        return breakdown.entries.sumOf { (value, count) -> value * count.coerceAtLeast(0) }
    }
}
