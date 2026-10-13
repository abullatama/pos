package id.a3.pos.sync

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun SyncSpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("spike_sync_logs", Context.MODE_PRIVATE) }

    var isForegroundRunning by remember { mutableStateOf(false) }
    var foregroundCount by remember { mutableIntStateOf(0) }
    var batteryLevel by remember { mutableIntStateOf(100) }
    val logs = remember { mutableStateListOf<String>() }

    fun refreshLogs() {
        val raw = prefs.getString("logs", "") ?: ""
        logs.clear()
        if (raw.isNotEmpty()) {
            logs.addAll(raw.lines().filter { it.isNotBlank() })
        }
    }

    LaunchedEffect(Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        refreshLogs()
    }

    // Coroutine foreground loop jika aktif
    LaunchedEffect(isForegroundRunning) {
        if (isForegroundRunning) {
            while (isActive) {
                delay(5000)
                foregroundCount++
                val nowStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val entry = "[$nowStr] Foreground loop #$foregroundCount berjalan normal."
                val existing = prefs.getString("logs", "") ?: ""
                prefs.edit().putString("logs", "$entry\n$existing".take(5000)).apply()
                refreshLogs()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Spike F02: Uji Background Sync di Redmi Pad 2 (HyperOS 2)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Menguji Foreground Coroutine vs WorkManager saat tablet sleep / layar mati.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status Sistem Tablet:", fontWeight = FontWeight.Bold)
                Text("• Baterai: $batteryLevel%")
                Text("• Foreground Loop: ${if (isForegroundRunning) "BERJALAN (Setiap 5 detik)" else "BERHENTI"}")
                Text("• Log Tercatat: ${logs.size} baris")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    isForegroundRunning = !isForegroundRunning
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isForegroundRunning) "Hentikan Foreground Loop" else "Mulai Foreground Loop (5s)")
            }

            Button(
                onClick = {
                    val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                        .build()
                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                        "SpikePeriodicSync",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                    )
                    val nowStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val entry = "[$nowStr] WorkManager dijadwalkan (Periodic 15 Menit)."
                    val existing = prefs.getString("logs", "") ?: ""
                    prefs.edit().putString("logs", "$entry\n$existing".take(5000)).apply()
                    refreshLogs()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Jadwalkan WorkManager (15m)")
            }

            OutlinedButton(
                onClick = {
                    prefs.edit().clear().apply()
                    foregroundCount = 0
                    refreshLogs()
                }
            ) {
                Text("Hapus Log")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Daftar Riwayat Sinkronisasi / Latar Belakang:",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text(
                "Belum ada log. Klik 'Mulai Foreground Loop' atau 'Jadwalkan WorkManager'.",
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { logLine ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = logLine,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
