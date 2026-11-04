package id.a3.feature.admin.update

/** Ringkasan APK yang relevan untuk pembaruan. [signerSha256] boleh kosong bila tanda tangan tidak terbaca. */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val signerSha256: Set<String> = emptySet()
)

sealed interface ApkCheckResult {
    /** [summary] mis. "Terpasang 1.0.3, akan menjadi 1.0.4". */
    data class Ok(val summary: String) : ApkCheckResult

    /** [message] dalam bahasa awam untuk owner. */
    data class Rejected(val message: String) : ApkCheckResult
}

/**
 * Memeriksa APK yang dipilih sebelum dipasang (OPS-03, Fase 1): aplikasi yang benar, versi lebih baru,
 * tanda tangan sama. Tanpa Android; mudah dites. Android tetap penjaga terakhir untuk tanda tangan.
 */
object ApkUpdateChecker {

    fun appDisplayName(packageName: String): String = when (packageName) {
        "id.a3coffeeshop" -> "A3 Coffeeshop"
        "id.athialoundry" -> "Athia Laundry"
        else -> packageName
    }

    fun check(installed: ApkInfo, candidate: ApkInfo?): ApkCheckResult {
        if (candidate == null) {
            return ApkCheckResult.Rejected(
                "Berkas ini bukan aplikasi yang valid atau rusak. Unduh ulang dari tautan Drive lalu coba lagi."
            )
        }
        if (candidate.packageName != installed.packageName) {
            val candidateName = appDisplayName(candidate.packageName)
            val installedName = appDisplayName(installed.packageName)
            return ApkCheckResult.Rejected(
                "Berkas ini untuk aplikasi $candidateName, bukan $installedName. " +
                    "Buka Menu Admin di aplikasi $candidateName untuk memasangnya."
            )
        }
        val installedLabel = installed.versionName ?: installed.versionCode.toString()
        val candidateLabel = candidate.versionName ?: candidate.versionCode.toString()
        if (candidate.versionCode == installed.versionCode) {
            return ApkCheckResult.Rejected(
                "Versi $candidateLabel sudah terpasang. Tidak perlu diperbarui."
            )
        }
        if (candidate.versionCode < installed.versionCode) {
            return ApkCheckResult.Rejected(
                "Berkas ini versi lebih lama ($candidateLabel) dari yang terpasang ($installedLabel). " +
                    "Versi lama tidak boleh dipasang. Gunakan berkas terbaru dari pengembang."
            )
        }
        if (installed.signerSha256.isNotEmpty() && candidate.signerSha256.isNotEmpty() &&
            installed.signerSha256.intersect(candidate.signerSha256).isEmpty()
        ) {
            return ApkCheckResult.Rejected(
                "Berkas ini bukan berkas resmi dari pengembang (tanda tangan berbeda). Jangan dipasang."
            )
        }
        return ApkCheckResult.Ok("Terpasang $installedLabel, akan menjadi $candidateLabel")
    }
}
