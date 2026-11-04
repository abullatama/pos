package id.a3.feature.admin.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Pembantu instalasi paket aplikasi APK mandiri via PackageInstaller API (OPS-03).
 */
object ApkInstallerHelper {
    private const val TAG = "ApkInstallerHelper"
    const val ACTION_INSTALL_STATUS = "id.a3.feature.admin.INSTALL_STATUS"

    fun installApk(
        context: Context,
        apkFile: File,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!apkFile.exists()) {
            onResult(false, "File APK tidak ditemukan: ${apkFile.absolutePath}")
            return
        }
        try {
            FileInputStream(apkFile).use { input ->
                installApkStream(context, input, apkFile.length(), onResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca berkas APK", e)
            onResult(false, "Gagal membaca berkas APK: ${e.message}")
        }
    }

    fun installApkStream(
        context: Context,
        inputStream: InputStream,
        contentLength: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        var sessionId = -1
        try {
            sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package_install_stream", 0, contentLength).use { out ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                session.fsync(out)
            }

            // Daftarkan status receiver secara dinamis
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status: $status"
                    Log.d(TAG, "Install status diterima: status=$status, msg=$message")
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        onResult(true, "Instalasi pembaruan berhasil!")
                    } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmIntent)
                            onResult(false, "Konfirmasi pengguna diperlukan.")
                        } else {
                            onResult(false, "Menunggu konfirmasi tetapi intent konfirmasi kosong.")
                        }
                    } else {
                        onResult(false, "Gagal memperbarui: $message (kode $status)")
                    }
                }
            }

            val filter = IntentFilter(ACTION_INSTALL_STATUS)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            val intent = Intent(ACTION_INSTALL_STATUS).apply {
                setPackage(context.packageName)
            }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, pendingFlags)

            session.commit(pendingIntent.intentSender)
            session.close()
            Log.d(TAG, "Session installer di-commit untuk ID $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error session installer", e)
            if (sessionId != -1) {
                try {
                    packageInstaller.abandonSession(sessionId)
                } catch (_: Exception) {}
            }
            onResult(false, "Gagal memasang APK: ${e.message}")
        }
    }
}
