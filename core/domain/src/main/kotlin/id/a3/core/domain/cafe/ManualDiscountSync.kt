package id.a3.core.domain.cafe

/**
 * Diskon manual atau komplimen (disetujui PIN manager). Server tidak menerima diskon bebas pada
 * order.created: order dikirim pada harga penuh, lalu koreksi manual_discount/comp mengurangi tagihan
 * sebesar [adjustment] (rupiah, setelah pajak dan pembulatan).
 *
 * Nilai `full*` adalah total tanpa diskon dengan rumus yang sama dengan server.
 * [adjustment] = fullTotal dikurangi total setelah diskon di tablet, sehingga tagihan server
 * sama persis dengan yang dibayar pelanggan.
 */
data class ManualDiscountSync(
    val fullSubtotal: Long,
    val fullServiceCharge: Long,
    val fullTax: Long,
    val fullRounding: Long,
    val fullTotal: Long,
    val reasonCode: String,
    val note: String?,
    val approvedByUserId: Long,
    val approvedByName: String
) {
    /** Selisih tagihan yang harus dicatat sebagai koreksi, dihitung dari total setelah diskon di tablet. */
    fun adjustmentFor(discountedTotal: Long): Long = (fullTotal - discountedTotal).coerceAtLeast(0L)

    /** Diskon sampai nol berarti komplimen; selain itu diskon manual. */
    fun correctionTypeFor(discountedTotal: Long): String = if (discountedTotal <= 0L) "comp" else "manual_discount"
}
