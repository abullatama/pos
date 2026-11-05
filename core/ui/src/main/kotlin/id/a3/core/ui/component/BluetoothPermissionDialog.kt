package id.a3.core.ui.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import id.a3.core.ui.theme.A3Theme

/**
 * Izin "Perangkat di sekitar" (BLUETOOTH_CONNECT) wajib di Android 12 ke atas agar aplikasi dapat melihat
 * printer yang sudah dipasangkan. Bila ditolak dengan pilihan "jangan tanya lagi", dialog sistem tidak muncul
 * lagi dan status printer hanya tertulis "Printer Putus" tanpa sebab. Dialog ini menjelaskan sebabnya.
 */
fun isBluetoothConnectMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Layar pengaturan tidak tersedia; pengguna dapat membukanya manual dari Pengaturan > Aplikasi.
    }
}

@Composable
fun BluetoothPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = A3Theme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Izin printer belum aktif", color = colors.ink) },
        text = {
            Text(
                "Izin \"Perangkat di sekitar\" belum diberikan, sehingga printer tidak bisa tersambung. " +
                    "Ketuk Buka Pengaturan, pilih Izin, lalu aktifkan Perangkat di sekitar.",
                color = colors.inkMuted
            )
        },
        confirmButton = {
            A3Button(
                label = "Buka Pengaturan",
                variant = ButtonVariant.PRIMARY,
                onClick = {
                    openAppPermissionSettings(context)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            A3Button(label = "Nanti", variant = ButtonVariant.GHOST, onClick = onDismiss)
        },
        containerColor = colors.surface
    )
}
