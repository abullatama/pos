package id.a3.pos.printer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSpikeScreen(
    printerManager: PrinterManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bluetoothPrinters = remember { mutableStateListOf<PrinterConnection.Bluetooth>() }
    val usbPrinters = remember { mutableStateListOf<PrinterConnection.Usb>() }

    var selectedPrinter by remember { mutableStateOf<PrinterConnection?>(null) }
    var charsPerLine by remember { mutableIntStateOf(32) }
    var testQr by remember { mutableStateOf(true) }
    var testCut by remember { mutableStateOf(true) }
    var testDrawer by remember { mutableStateOf(false) }

    var isPrinting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Siap menguji printer.") }

    fun refreshDevices() {
        val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasBtPermission) {
            bluetoothPrinters.clear()
            bluetoothPrinters.addAll(printerManager.getPairedBluetoothPrinters())
        }

        usbPrinters.clear()
        usbPrinters.addAll(printerManager.getConnectedUsbPrinters())

        // Otomatis pilih RPP02N atau printer pertama yang tersedia jika belum dipilih
        if (selectedPrinter == null) {
            selectedPrinter = bluetoothPrinters.find { it.name.contains("RPP", ignoreCase = true) }
                ?: bluetoothPrinters.firstOrNull()
                ?: usbPrinters.firstOrNull()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDevices()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            permissionLauncher.launch(permissions)
        } else {
            refreshDevices()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spike M0: Uji Printer iWare C58BT (Bluetooth & USB)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Panel Kiri: Pemilihan Perangkat & Opsi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Printer Terhubung",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { refreshDevices() }) {
                        Text("Pindai Ulang")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Bluetooth (Sudah Dipasangkan):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (bluetoothPrinters.isEmpty()) {
                        item {
                            Text(
                                "Tidak ada printer Bluetooth paired. Pasangkan iWare C58BT di Pengaturan Tablet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    } else {
                        items(bluetoothPrinters) { bt ->
                            val isSelected = selectedPrinter == bt
                            DeviceItemCard(
                                title = bt.name,
                                subtitle = "MAC: ${bt.address} [Bluetooth]",
                                isSelected = isSelected,
                                onClick = { selectedPrinter = bt }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "USB Host:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (usbPrinters.isEmpty()) {
                        item {
                            Text(
                                "Tidak ada printer USB terdeteksi (colok kabel OTG/USB).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    } else {
                        items(usbPrinters) { usb ->
                            val isSelected = selectedPrinter == usb
                            DeviceItemCard(
                                title = usb.name,
                                subtitle = "${usb.address} [USB Host]",
                                isSelected = isSelected,
                                onClick = { selectedPrinter = usb }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Parameter Uji
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Parameter Uji ESC/POS", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lebar Karakter: ")
                            OutlinedButton(
                                onClick = { charsPerLine = 32 },
                                colors = if (charsPerLine == 32) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                            ) { Text("32 Char (Standar 58mm)") }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { charsPerLine = 30 },
                                colors = if (charsPerLine == 30) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                            ) { Text("30 Char") }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = testQr, onCheckedChange = { testQr = it })
                            Text("Sertakan QR Code (GS ( k)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = testCut, onCheckedChange = { testCut = it })
                            Text("Perintah Cut Kertas (GS V)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = testDrawer, onCheckedChange = { testDrawer = it })
                            Text("Buka Laci Kas RJ11 (ESC p)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tombol Aksi Cetak
                Button(
                    onClick = {
                        val currentTarget = selectedPrinter
                        if (currentTarget == null) {
                            Toast.makeText(context, "Pilih printer terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val printBytes = TestReceiptGenerator.createSpikeReceipt(
                            charsPerLine = charsPerLine,
                            includeQr = testQr,
                            includeCut = testCut,
                            testCashDrawer = testDrawer
                        )

                        scope.launch {
                            isPrinting = true
                            statusMessage = "Mengirim data cetak ke ${currentTarget.name}..."

                            val result = when (currentTarget) {
                                is PrinterConnection.Bluetooth -> {
                                    printerManager.printBluetooth(currentTarget.device, printBytes)
                                }
                                is PrinterConnection.Usb -> {
                                    printerManager.printUsb(currentTarget.device, printBytes)
                                }
                            }

                            isPrinting = false
                            result.onSuccess {
                                statusMessage = "BERHASIL: Data cetak terkirim ke ${currentTarget.name}."
                                Toast.makeText(context, "Cetak Berhasil!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                statusMessage = "GAGAL: ${err.message}"
                                Toast.makeText(context, "Gagal mencetak: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isPrinting && selectedPrinter != null
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(24.dp).width(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(if (isPrinting) "Sedang Mencetak..." else "Cetak Struk Uji Spike")
                }
            }

            // Panel Kanan: Preview Struk Monospace & Log Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Text(
                    text = "Pratinjau Format Struk ($charsPerLine Kolom)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    val previewText = remember(charsPerLine, testQr, testCut, testDrawer) {
                        buildString {
                            appendLine("       AA COFFEESHOP       ")
                            appendLine(" Jl. Pengujian No. 1, M0 Spike ")
                            appendLine("      Telp: 0812-3456-7890     ")
                            appendLine("=".repeat(charsPerLine))
                            appendLine("No. Order:               C1-0001")
                            appendLine("Kasir:           Budi (Shift 1)")
                            appendLine("Waktu:          2026-10-12 14:30")
                            appendLine("-".repeat(charsPerLine))
                            appendLine("1x Espresso Single      Rp18.000")
                            appendLine("1x Caffe Latte (Oat)    Rp28.000")
                            appendLine("-".repeat(charsPerLine))
                            appendLine("Subtotal                Rp46.000")
                            appendLine("Diskon (0%)                  Rp0")
                            appendLine("TOTAL                   Rp46.000")
                            appendLine("Bayar (QRIS Statis)     Rp46.000")
                            appendLine("Kembalian                    Rp0")
                            appendLine("=".repeat(charsPerLine))
                            appendLine(" --- UJI 32 KARAKTER PERSIS --- ")
                            appendLine("12345678901234567890123456789012")
                            appendLine("-".repeat(charsPerLine))
                            if (testQr) {
                                appendLine(" Pindai QR Verifikasi Order: ")
                                appendLine("  [QR RASTER: ORD-...-C1-0001]  ")
                            }
                            appendLine(" Terima Kasih atas Kunjungan Anda")
                            appendLine("    Simpan struk sebagai bukti  ")
                            if (testCut) {
                                appendLine("<----------- [CUT] ----------->")
                            }
                            if (testDrawer) {
                                appendLine("<----- [OPEN CASH DRAWER] ----->")
                            }
                        }
                    }

                    Text(
                        text = previewText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusMessage.startsWith("GAGAL")) Color(0xFFFFEBEE)
                        else if (statusMessage.startsWith("BERHASIL")) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Status Uji:", fontWeight = FontWeight.Bold)
                        Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color.Gray
            )
        }
    }
}
