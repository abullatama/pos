package id.a3.core.domain.payment

/**
 * Gabungkan metode bayar per pesanan menjadi label singkat untuk riwayat: "Tunai", "QRIS", atau "Tunai + QRIS".
 * Metode tak dikenal diabaikan. Hanya tampilan; tidak menyentuh angka uang.
 */
fun paymentLabelsByOrder(rows: List<Pair<String, String>>): Map<String, String> =
    rows.groupBy({ it.first }, { it.second })
        .mapValues { (_, methods) ->
            methods.distinct().mapNotNull {
                when (it) {
                    "cash" -> "Tunai"
                    "qris_static" -> "QRIS"
                    else -> null
                }
            }.joinToString(" + ")
        }
        .filterValues { it.isNotEmpty() }
