package id.a3.feature.admin.presentation

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Card
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.admin.update.UpdateEligibility
import java.io.File
import java.io.FileOutputStream

@Composable
fun AdminMenuScreen(
    viewModel: AdminViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.refreshKioskState(context)
        viewModel.checkUpdateEligibility()
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "update_installer.apk")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.installApk(context, tempFile)
            } catch (e: Exception) {
                // error handling handled via viewModel status message
            }
        }
    }

    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        if (!uiState.isOwnerUnlocked) {
            // Gerbang Otorisasi PIN Owner
            OwnerPinGate(
                uiState = uiState,
                onPinDigit = viewModel::onPinDigit,
                onPinBackspace = viewModel::onPinBackspace,
                onBackClick = onBackClick
            )
        } else {
            // Dashboard Menu Admin Tablet (S15)
            AdminDashboard(
                uiState = uiState,
                onLockAdmin = viewModel::lockAdmin,
                onBackClick = onBackClick,
                onEnableKiosk = { if (activity != null) viewModel.enableKiosk(activity) },
                onDisableKiosk = { if (activity != null) viewModel.disableKiosk(activity) },
                onOpenWifi = { viewModel.openWifi(context) },
                onTestPrinter = viewModel::testPrinter,
                onCheckUpdate = viewModel::checkUpdateEligibility,
                onSelectApkFile = { apkPickerLauncher.launch("application/vnd.android.package-archive") }
            )
        }

        // Dialog Notifikasi / Pesan Status
        uiState.statusMessage?.let { msg ->
            Dialog(onDismissRequest = viewModel::clearStatusMessage) {
                A3Card(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = colors.ink,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Informasi Sistem",
                            style = A3Theme.typography.headline,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = msg,
                            style = A3Theme.typography.body,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        A3Button(
                            label = "Tutup",
                            onClick = viewModel::clearStatusMessage,
                            fillWidth = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerPinGate(
    uiState: AdminUiState,
    onPinDigit: (String) -> Unit,
    onPinBackspace: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = A3Theme.colors

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sisi Kiri: Informasi Otorisasi
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Gembok Owner",
                    tint = colors.ink,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Menu Admin Tablet (S15)",
                style = A3Theme.typography.display,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Area operasional terlindungi. Masukkan 4-digit PIN Owner untuk mengakses pengaturan sistem perangkat, kontrol mode kios, dan pembaruan aplikasi.",
                style = A3Theme.typography.body,
                color = colors.inkMuted,
                lineHeight = 22.sp
            )

            if (uiState.pinError != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFBE9E7))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Peringatan",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = uiState.pinError,
                        style = A3Theme.typography.body,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            A3Button(
                label = "Kembali ke Kasir",
                onClick = onBackClick,
                variant = ButtonVariant.GHOST
            )
        }

        // Sisi Kanan: Tampilan PIN Dots & Keypad Numerik 3x4
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            A3Card(
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PIN OWNER",
                        style = A3Theme.typography.label,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 4 Digit Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < uiState.enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) colors.ink else Color.Transparent)
                                    .border(
                                        width = 2.dp,
                                        color = if (isFilled) colors.ink else colors.border,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Keypad
                    A3Keypad(
                        onDigitClick = onPinDigit,
                        onBackspace = onPinBackspace,
                        onClear = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDashboard(
    uiState: AdminUiState,
    onLockAdmin: () -> Unit,
    onBackClick: () -> Unit,
    onEnableKiosk: () -> Unit,
    onDisableKiosk: () -> Unit,
    onOpenWifi: () -> Unit,
    onTestPrinter: () -> Unit,
    onCheckUpdate: () -> Unit,
    onSelectApkFile: () -> Unit
) {
    val colors = A3Theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Bilah Header Admin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Menu Admin Tablet",
                        style = A3Theme.typography.display,
                        color = colors.ink
                    )
                    A3StatusChip(
                        label = "Akses Owner Aktif",
                        token = StatusToken.SUCCESS,
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ID Perangkat: ${uiState.deviceId}  ·  Unit: ${uiState.businessUnitId}  ·  Versi: v${uiState.appVersionName}",
                    style = A3Theme.typography.body,
                    color = colors.inkMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                A3Button(
                    label = "Kunci Akses Admin",
                    onClick = onLockAdmin,
                    variant = ButtonVariant.SECONDARY
                )
                A3Button(
                    label = "Tutup Menu",
                    onClick = onBackClick,
                    variant = ButtonVariant.PRIMARY
                )
            }
        }

        // Area Grid 2-Kolom Tablet Landscape
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // KOLOM KIRI (48%): Status Perangkat, Wi-Fi, Printer & Mode Kios
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Kartu 1: Status Perangkat & Jaringan
                A3Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Pengaturan Perangkat & Hardware",
                            style = A3Theme.typography.headline,
                            color = colors.ink
                        )

                        // Wi-Fi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Wifi,
                                    contentDescription = "Wi-Fi",
                                    tint = colors.ink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Koneksi Wi-Fi",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Buka pengaturan jaringan Android",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                            A3Button(
                                label = "Buka Wi-Fi",
                                onClick = onOpenWifi,
                                variant = ButtonVariant.SECONDARY
                            )
                        }

                        // Printer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Print,
                                    contentDescription = "Printer",
                                    tint = colors.ink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Printer iWare C58BT",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = uiState.printerStatusText,
                                        style = A3Theme.typography.bodySmall,
                                        color = if (uiState.isPrinterConnected) colors.ink else colors.status.danger.text
                                    )
                                }
                            }
                            A3Button(
                                label = if (uiState.isTestingPrinter) "Mencetak..." else "Uji Cetak",
                                onClick = onTestPrinter,
                                variant = ButtonVariant.SECONDARY,
                                enabled = !uiState.isTestingPrinter
                            )
                        }

                        // Sinkronisasi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = "Sync",
                                    tint = colors.ink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Antrean Outbox",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = if (uiState.unsyncedOutboxCount == 0) "Seluruh data tersinkron" else "${uiState.unsyncedOutboxCount} data menunggu sinkron",
                                        style = A3Theme.typography.bodySmall,
                                        color = if (uiState.unsyncedOutboxCount == 0) colors.inkMuted else colors.status.warning.text
                                    )
                                }
                            }
                            A3StatusChip(
                                label = if (uiState.unsyncedOutboxCount == 0) "Sinkron" else "${uiState.unsyncedOutboxCount} Pending",
                                token = if (uiState.unsyncedOutboxCount == 0) StatusToken.SUCCESS else StatusToken.WARNING
                            )
                        }
                    }
                }

                // Kartu 2: Mode Kios (Lock Task SET-04, SET-05)
                A3Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode Kios Kasir (Lock Task)",
                                style = A3Theme.typography.headline,
                                color = colors.ink
                            )
                            A3StatusChip(
                                label = if (uiState.isKioskActive) "Kios Terkunci" else "Kios Bebas",
                                token = if (uiState.isKioskActive) StatusToken.INFO else StatusToken.NEUTRAL,
                                icon = if (uiState.isKioskActive) Icons.Outlined.Lock else Icons.Outlined.LockOpen
                            )
                        }

                        Text(
                            text = "Mode Kios mengunci tablet pada aplikasi kasir sehingga navigasi Back, Home, dan Recent Apps diblokir oleh sistem Android. Kasir toko tidak dapat menutup atau beralih ke aplikasi lain.",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (uiState.isKioskActive) {
                            A3Button(
                                label = "Lepaskan Kios (Buka Navigasi Tablet)",
                                onClick = onDisableKiosk,
                                variant = ButtonVariant.DANGER,
                                fillWidth = true
                            )
                        } else {
                            A3Button(
                                label = "Kunci Tablet ke Mode Kios",
                                onClick = onEnableKiosk,
                                variant = ButtonVariant.PRIMARY,
                                fillWidth = true
                            )
                        }
                    }
                }
            }

            // KOLOM KANAN (52%): Pembaruan Aplikasi Mandiri (OPS-03 & F39)
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                A3Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SystemUpdate,
                                    contentDescription = "Update",
                                    tint = colors.ink,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Pembaruan Aplikasi (OPS-03)",
                                    style = A3Theme.typography.headline,
                                    color = colors.ink
                                )
                            }

                            IconButton(onClick = onCheckUpdate) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Periksa Ulang",
                                    tint = colors.ink
                                )
                            }
                        }

                        Text(
                            text = "Aturan Kepatuhan OPS-03: Distribusi lewat jalur privat. Update hanya saat tidak ada shift aktif dan outbox kosong, di luar jam ramai.",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted,
                            lineHeight = 20.sp
                        )

                        // 2 Indikator Prasyarat OPS-03
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceAlt)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "STATUS PRASYARAT PEMBARUAN",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted,
                                fontWeight = FontWeight.Bold
                            )

                            // Syarat 1: Shift Aktif
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1. Tidak Ada Shift Berjalan:",
                                    style = A3Theme.typography.body,
                                    color = colors.ink
                                )
                                if (uiState.activeShiftCashierName != null) {
                                    A3StatusChip(
                                        label = "Shift Aktif: ${uiState.activeShiftCashierName}",
                                        token = StatusToken.DANGER,
                                        icon = Icons.Outlined.Warning
                                    )
                                } else {
                                    A3StatusChip(
                                        label = "Shift Ditutup (Aman)",
                                        token = StatusToken.SUCCESS,
                                        icon = Icons.Outlined.CheckCircle
                                    )
                                }
                            }

                            // Syarat 2: Outbox Kosong
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "2. Seluruh Outbox Tersinkron:",
                                    style = A3Theme.typography.body,
                                    color = colors.ink
                                )
                                if (uiState.unsyncedOutboxCount > 0) {
                                    A3StatusChip(
                                        label = "${uiState.unsyncedOutboxCount} Belum Sinkron",
                                        token = StatusToken.WARNING,
                                        icon = Icons.Outlined.Warning
                                    )
                                } else {
                                    A3StatusChip(
                                        label = "Outbox Kosong (Aman)",
                                        token = StatusToken.SUCCESS,
                                        icon = Icons.Outlined.CheckCircle
                                    )
                                }
                            }
                        }

                        // Hasil Evaluasi Kelayakan
                        when (val eligibility = uiState.updateEligibility) {
                            is UpdateEligibility.Allowed -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = "Aman",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Siap Menerima Pembaruan APK",
                                            style = A3Theme.typography.label,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Kondisi tablet memenuhi seluruh aturan OPS-03. Tidak ada data transaksi yang berisiko terganggu.",
                                        style = A3Theme.typography.body,
                                        color = Color(0xFF1B5E20)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                A3Button(
                                    label = "Pilih & Pasang Paket APK Pembaruan",
                                    onClick = onSelectApkFile,
                                    variant = ButtonVariant.PRIMARY,
                                    fillWidth = true
                                )
                            }
                            is UpdateEligibility.Blocked -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Warning,
                                            contentDescription = "Diblokir",
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Pembaruan Diblokir (Aturan OPS-03)",
                                            style = A3Theme.typography.label,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    for (reason in eligibility.reasons) {
                                        Text(
                                            text = "• ${reason.description}",
                                            style = A3Theme.typography.body,
                                            color = Color(0xFFBF360C)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                A3Button(
                                    label = "Pembaruan Tidak Dapat Dijalankan",
                                    onClick = {},
                                    variant = ButtonVariant.SECONDARY,
                                    enabled = false,
                                    fillWidth = true
                                )
                            }
                            null -> {
                                if (uiState.isCheckingUpdate) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
