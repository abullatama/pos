package id.a3.feature.laundry.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Akhiran nama baris yang diberikan layar Order saat saklar Express menyala (LaundryOrderViewModel). */
internal const val EXPRESS_NAME_SUFFIX = " (Express)"

/** True bila baris pesanan dibuat dengan layanan ekspres. Hanya tampilan; tidak mengubah harga atau data. */
internal fun isExpressLineName(name: String): Boolean = name.endsWith(EXPRESS_NAME_SUFFIX)

/** Nama layanan tanpa akhiran "(Express)", karena ekspres ditampilkan sebagai penanda tersendiri. */
internal fun plainServiceName(name: String): String = name.removeSuffix(EXPRESS_NAME_SUFFIX)

/** Berat dalam kg ala Indonesia: 2 kg, 2,5 kg. */
internal fun formatWeightKg(weight: Double): String {
    val text = if (weight % 1.0 == 0.0) weight.toLong().toString() else "%.2f".format(Locale.US, weight).trimEnd('0').trimEnd('.')
    return text.replace('.', ',') + " kg"
}

private val timeFormat = DateTimeFormatter.ofPattern("HH.mm", Locale.forLanguageTag("id-ID"))
private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))

/**
 * Waktu pesanan untuk kartu daftar. Cucian yang belum diambil memakai umur ("Hari ini, 14.20", "Kemarin, 09.05",
 * "3 hari lalu") agar yang menumpuk mudah terlihat; yang sudah selesai memakai tanggal biasa ("5 Nov, 14.20").
 */
internal fun orderAgeLabel(
    createdAtMillis: Long,
    nowMillis: Long,
    unfinished: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val created = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(created.toLocalDate(), today)
    val time = created.format(timeFormat)
    return when {
        !unfinished -> "${created.format(dateFormat)}, $time"
        days <= 0L -> "Hari ini, $time"
        days == 1L -> "Kemarin, $time"
        else -> "$days hari lalu"
    }
}
