package id.a3.feature.admin.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Pengelola Mode Kios (Lock Task / Screen Pinning) dan Pengaturan Sistem Perangkat (SET-04, SET-05).
 */
object KioskManager {

    private const val TAG = "KioskManager"

    /**
     * Memeriksa apakah aplikasi saat ini sedang dalam mode Lock Task (Kios terkunci).
     */
    fun isLockTaskActive(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memeriksa status lock task", e)
            false
        }
    }

    /**
     * Mengaktifkan Mode Kios (Lock Task) pada Activity.
     * Mengunci navigasi kasir (Back, Home, Recents diblokir oleh sistem Android).
     */
    fun startKiosk(activity: Activity): Result<Unit> {
        return try {
            activity.startLockTask()
            Log.i(TAG, "Kiosk mode (LockTask) berhasil diaktifkan")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengaktifkan kiosk mode", e)
            Result.failure(e)
        }
    }

    /**
     * Melepaskan Mode Kios (Lock Task) pada Activity.
     * Memerlukan verifikasi PIN Owner sebelum dipanggil dari antarmuka kasir.
     */
    fun stopKiosk(activity: Activity): Result<Unit> {
        return try {
            activity.stopLockTask()
            Log.i(TAG, "Kiosk mode (LockTask) berhasil dinonaktifkan")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menonaktifkan kiosk mode", e)
            Result.failure(e)
        }
    }

    /**
     * Membuka pengaturan Wi-Fi sistem Android (SET-05).
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuka pengaturan Wi-Fi", e)
        }
    }
}
