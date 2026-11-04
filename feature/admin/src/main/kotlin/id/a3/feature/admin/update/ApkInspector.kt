package id.a3.feature.admin.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.security.MessageDigest

/** Membaca ApkInfo dari aplikasi yang berjalan dan dari berkas APK. */
object ApkInspector {
    private const val TAG = "ApkInspector"

    fun readInstalled(context: Context): ApkInfo {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        return toApkInfo(info)
    }

    /** Null bila berkas bukan APK yang bisa dibaca. */
    fun readArchive(context: Context, apkFile: File): ApkInfo? = try {
        context.packageManager
            .getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?.let(::toApkInfo)
    } catch (e: Exception) {
        Log.w(TAG, "Gagal membaca APK: ${e.message}")
        null
    }

    private fun toApkInfo(info: PackageInfo): ApkInfo {
        val signers = info.signingInfo?.let {
            if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
        }.orEmpty()
        val digests = signers.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { b -> "%02x".format(b) }
        }.toSet()
        return ApkInfo(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            versionName = info.versionName,
            signerSha256 = digests
        )
    }
}
