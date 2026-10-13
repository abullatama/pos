package id.a3.pos.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun KioskSpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isDeviceOwner by remember { mutableStateOf(false) }
    var isLockTaskRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Siap menguji Kiosk Mode.") }

    LaunchedEffect(Unit) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Spike F03: Uji Kiosk Mode & Silent Install di Redmi Pad 2",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Kiosk mode mencegah kasir keluar dari POS. Device Owner memungkinkan update APK senyap tanpa Google Play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Hak Akses Sistem:", fontWeight = FontWeight.Bold)
                Text("• Package: ${context.packageName}")
                Text("• Device Owner Status: ${if (isDeviceOwner) "AKTIF (Full Kiosk & Silent Install)" else "TIDAK AKTIF (Hanya Screen Pinning biasa)"}")
                Text("• LockTask State: ${if (isLockTaskRunning) "TERKUNCI (LockTask Aktif)" else "NORMAL"}")
                Text("• Pesan: $statusText")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Kontrol Pengujian Kiosk Mode (LockTask):",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    try {
                        activity?.startLockTask()
                        isLockTaskRunning = true
                        statusText = "startLockTask() dipanggil. Coba tekan tombol Home / Recents di tablet!"
                    } catch (e: Exception) {
                        statusText = "Gagal startLockTask: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Mulai Kiosk (Lock Task)")
            }

            OutlinedButton(
                onClick = {
                    try {
                        activity?.stopLockTask()
                        isLockTaskRunning = false
                        statusText = "stopLockTask() berhasil dipanggil. Mode normal kembali."
                    } catch (e: Exception) {
                        statusText = "Gagal stopLockTask: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Lepas Kiosk (Unlock)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Catatan Verifikasi Spesifikasi A3 (F03):", fontWeight = FontWeight.Bold)
                Text("1. Pada tablet ritel tanpa MDM khusus, aplikasi memanggil `startLockTask()` sehingga tombol Navigasi (Back, Home, Recents) terkunci di layar POS.")
                Text("2. Untuk mengaktifkan mode Device Owner sejati (agar tidak muncul popup konfirmasi sistem), perintah adb dpm dijalankan sekali saat setup perangkat toko:")
                Text(
                    "`adb shell dpm set-device-owner id.a3.pos/.receiver.DeviceAdminReceiver`",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("3. Fitur silent APK update menggunakan `PackageInstaller.Session` bawaan Android untuk menginstal pembaruan APK tanpa ketergantungan Google Play.")
            }
        }
    }
}
