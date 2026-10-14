package id.a3.pos.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun KioskSpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var isDeviceOwner by remember { mutableStateOf(false) }
    var isDeviceAdmin by remember { mutableStateOf(false) }
    var isLockTaskRunning by remember { mutableStateOf(false) }
    var canRequestPackageInstalls by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Siap menguji Kiosk Mode & Silent Install.") }
    var isInstalling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) == true
        isDeviceAdmin = dpm?.isAdminActive(android.content.ComponentName(context, A3DeviceAdminReceiver::class.java)) == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canRequestPackageInstalls = context.packageManager.canRequestPackageInstalls()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Spike F03: Uji Kiosk Mode & Pasang APK Senyap di Redmi Pad 2",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Menguji ketahanan mode kiosk kasir dan jalur pembaruan APK mandiri (PackageInstaller Session) tanpa ketergantungan Google Play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Hak Akses Tablet (HyperOS 2 / Android 16):", fontWeight = FontWeight.Bold)
                Text("• Package Target: ${context.packageName}")
                Text("• Device Admin Active: ${if (isDeviceAdmin) "AKTIF" else "TIDAK AKTIF"}")
                Text("• Device Owner: ${if (isDeviceOwner) "AKTIF (Penuh)" else "TIDAK AKTIF (Ditolak OS bila ada akun Google/Mi)"}")
                Text("• LockTask State: ${if (isLockTaskRunning) "TERKUNCI (LockTask Aktif)" else "NORMAL"}")
                Text("• Izin Pasang APK Eksternal: ${if (canRequestPackageInstalls) "DIIZINKAN" else "BELUM DIIZINKAN"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Log/Status: $statusText", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "1. Pengujian Kiosk Mode (LockTask):",
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
                        statusText = "LockTask aktif. Navigasi tablet (Back, Home, Recents) terkunci di layar POS."
                    } catch (e: Exception) {
                        statusText = "Gagal startLockTask: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Kunci Kiosk (Lock Task)")
            }

            OutlinedButton(
                onClick = {
                    try {
                        activity?.stopLockTask()
                        isLockTaskRunning = false
                        statusText = "LockTask dinonaktifkan. Mode normal kembali."
                    } catch (e: Exception) {
                        statusText = "Gagal stopLockTask: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Lepas Kiosk (Unlock)")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "2. Pengujian Update APK Senyap (PackageInstaller):",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Memasang update paket aplikasi melalui PackageInstaller API tanpa membuka Google Play.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                enabled = !isInstalling,
                onClick = {
                    scope.launch {
                        isInstalling = true
                        statusText = "Memulai pengujian instalasi mandiri via PackageInstaller..."
                        withContext(Dispatchers.IO) {
                            try {
                                val sourceDir = context.applicationInfo.sourceDir
                                val apkFile = File(sourceDir)
                                if (!apkFile.exists()) {
                                    withContext(Dispatchers.Main) {
                                        statusText = "Gagal: source APK tidak ditemukan di $sourceDir"
                                        isInstalling = false
                                    }
                                    return@withContext
                                }

                                withContext(Dispatchers.Main) {
                                    statusText = "Membuka PackageInstaller Session (ukuran APK: ${apkFile.length() / 1024} KB)..."
                                }

                                FileInputStream(apkFile).use { inputStream ->
                                    ApkInstallerHelper.installApkStream(
                                        context = context,
                                        inputStream = inputStream,
                                        contentLength = apkFile.length()
                                    ) { success, msg ->
                                        scope.launch(Dispatchers.Main) {
                                            isInstalling = false
                                            statusText = if (success) {
                                                "Berhasil: $msg"
                                            } else {
                                                "Hasil Pengujian: $msg"
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isInstalling = false
                                    statusText = "Eksepsi installer: ${e.message}"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isInstalling) "Memasang..." else "Uji Pasang Paket (Self-Update)")
            }

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Buka Izin Unknown Apps")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Catatan Verifikasi Spike M0 (F03):", fontWeight = FontWeight.Bold)
                Text("1. Device Owner murni (dpm set-device-owner) mensyaratkan tablet bersih dari akun. Pada tablet uji ritel yang sudah terpasang akun Google dan Akun Mi, Android menolak Device Owner.")
                Text("2. Sebagai cadangan resmi (D-09 & OQ-24), proteksi kasir di tablet operasional menggunakan mekanisme LockTask (Screen Pinning) yang aktif secara programatik.")
                Text("3. Jalur distribusi APK privat menggunakan PackageInstaller.Session. Pada mode tanpa Device Owner, sistem memunculkan prompt konfirmasi update paket mandiri kepada pengguna/kasir toko.")
            }
        }
    }
}
