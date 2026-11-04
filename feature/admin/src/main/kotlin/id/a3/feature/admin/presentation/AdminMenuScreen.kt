package id.a3.feature.admin.presentation

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TabletAndroid
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
                onPinClear = viewModel::onPinClear,
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
                onPrintEmergencySop = { viewModel.printEmergencySop() },
                onCheckUpdate = viewModel::checkUpdateEligibility,
                onSelectApkFile = { apkPickerLauncher.launch("application/vnd.android.package-archive") },
                onTriggerHeartbeat = { viewModel.triggerHeartbeat() },
                onRetryFailedSync = { viewModel.retryAllFailedSync(currentDeviceId = uiState.deviceId) }
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
    onPinClear: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ==========================================
        // 1. SISI KIRI: Panel Otorisasi & Konteks Keamanan
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Badge & Kategori
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(shapes.cardShape)
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.accent.copy(alpha = 0.25f), shapes.cardShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AdminPanelSettings,
                        contentDescription = "Otorisasi Admin",
                        tint = colors.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    A3StatusChip(
                        label = "Otorisasi Owner",
                        token = StatusToken.INFO
                    )
                    Text(
                        text = "Area Khusus Owner",
                        style = A3Theme.typography.label,
                        color = colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Judul Layar & Deskripsi
            Text(
                text = "Pengaturan Admin",
                style = A3Theme.typography.headline.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Area konfigurasi tingkat sistem perangkat tablet POS. Memerlukan PIN Owner untuk memastikan stabilitas dan keamanan operasional kasir.",
                style = A3Theme.typography.body,
                color = colors.inkMuted,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Fitur-fitur yang dilindungi (3 Kartu Ringkas Elegan)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminFeatureItem(
                    icon = Icons.Outlined.LockOpen,
                    title = "Mode Kios & Keamanan",
                    subtitle = "Kontrol pinning layar tablet dan reset aplikasi"
                )
                AdminFeatureItem(
                    icon = Icons.Outlined.Print,
                    title = "Hardware & Jaringan",
                    subtitle = "Diagnostik printer thermal struk & setelan Wi-Fi"
                )
                AdminFeatureItem(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "Pembaruan Aplikasi",
                    subtitle = "Verifikasi prasyarat dan instalasi berkas APK rilis"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Baris Bawah: Info Perangkat & Tombol Kembali
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TabletAndroid,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${uiState.deviceId} · Unit ${uiState.businessUnitId} · v${uiState.appVersionName}",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                A3Button(
                    label = "Kembali ke Kasir",
                    onClick = onBackClick,
                    variant = ButtonVariant.GHOST
                )
            }
        }

        // ==========================================
        // 2. SISI KANAN: Kartu Input PIN & Keypad Ergonomis
        // ==========================================
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            A3Card(
                modifier = Modifier.widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Kartu
                    Text(
                        text = "Masukkan PIN Owner",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "4 digit angka keamanan",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4 Digit Dots dengan Umpan Balik Visual Halus
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val isFilled = i < uiState.enteredPin.length
                            val isError = uiState.pinError != null && !uiState.isPinLocked
                            val isLocked = uiState.isPinLocked

                            val dotScale by animateFloatAsState(
                                targetValue = if (isFilled) 1.15f else 1.0f,
                                animationSpec = tween(durationMillis = 150),
                                label = "pin_dot_scale"
                            )

                            val dotBgColor by animateColorAsState(
                                targetValue = when {
                                    isLocked -> colors.status.danger.background
                                    isError -> colors.status.danger.background
                                    isFilled -> colors.accent
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(durationMillis = 150),
                                label = "pin_dot_bg"
                            )

                            val dotBorderColor by animateColorAsState(
                                targetValue = when {
                                    isLocked -> colors.status.danger.text
                                    isError -> colors.status.danger.text
                                    isFilled -> colors.accent
                                    else -> colors.border
                                },
                                animationSpec = tween(durationMillis = 150),
                                label = "pin_dot_border"
                            )

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(dotScale)
                                    .clip(CircleShape)
                                    .background(dotBgColor)
                                    .border(
                                        width = if (isFilled || isError || isLocked) 2.dp else 1.5.dp,
                                        color = dotBorderColor,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Kotak Status Kontekstual (Lockout / Error / Petunjuk)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            uiState.isPinLocked -> {
                                val minutes = uiState.lockoutRemainingSeconds / 60
                                val seconds = uiState.lockoutRemainingSeconds % 60
                                val timeText = String.format("%02d:%02d", minutes, seconds)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.status.danger.background)
                                        .border(1.dp, colors.status.danger.text.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LockClock,
                                        contentDescription = "Terkunci",
                                        tint = colors.status.danger.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Terkunci sementara ($timeText)",
                                        style = A3Theme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.status.danger.text
                                    )
                                }
                            }
                            uiState.pinError != null -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.status.danger.background)
                                        .border(1.dp, colors.status.danger.text.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = "Galat",
                                        tint = colors.status.danger.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.pinError,
                                        style = A3Theme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.status.danger.text,
                                        maxLines = 1
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Masukkan 4 digit PIN otorisasi",
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Keypad Numerik 3x4 (dengan Tombol C Clear Aktif & Disabled saat Lockout)
                    A3Keypad(
                        onDigitClick = onPinDigit,
                        onBackspace = onPinBackspace,
                        onClear = onPinClear,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (uiState.isPinLocked) 0.38f else 1.0f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.surface)
            .border(0.5.dp, colors.border, shapes.cardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = A3Theme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink
            )
            Text(
                text = subtitle,
                style = A3Theme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.inkMuted
            )
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
    onPrintEmergencySop: () -> Unit = {},
    onCheckUpdate: () -> Unit,
    onSelectApkFile: () -> Unit,
    onTriggerHeartbeat: () -> Unit = {},
    onRetryFailedSync: () -> Unit = {}
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
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pengaturan Admin",
                        style = A3Theme.typography.headline.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    A3StatusChip(
                        label = "Akses Aktif",
                        token = StatusToken.SUCCESS,
                        icon = Icons.Outlined.CheckCircle
                    )
                }
                Text(
                    text = "ID: ${uiState.deviceId}  ·  Unit: ${uiState.businessUnitId}  ·  v${uiState.appVersionName}",
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                A3Button(
                    label = "Kunci Akses",
                    onClick = onLockAdmin,
                    variant = ButtonVariant.SECONDARY
                )
                A3Button(
                    label = "Tutup",
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Perangkat & Hardware",
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
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Koneksi Wi-Fi",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Jaringan Wi-Fi tablet",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                            A3Button(
                                label = "Atur Wi-Fi",
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
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Printer Kasir",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = uiState.printerStatusText,
                                        style = A3Theme.typography.bodySmall,
                                        color = if (uiState.isPrinterConnected) colors.inkMuted else colors.status.danger.text
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                A3Button(
                                    label = if (uiState.isPrintingEmergencySop) "Mencetak SOP..." else "Cetak SOP Darurat",
                                    onClick = onPrintEmergencySop,
                                    variant = ButtonVariant.SECONDARY,
                                    enabled = !uiState.isPrintingEmergencySop && !uiState.isTestingPrinter
                                )
                                A3Button(
                                    label = if (uiState.isTestingPrinter) "Mencetak..." else "Uji Cetak",
                                    onClick = onTestPrinter,
                                    variant = ButtonVariant.SECONDARY,
                                    enabled = !uiState.isTestingPrinter && !uiState.isPrintingEmergencySop
                                )
                            }
                        }

                        // Sinkronisasi
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Antrean Sinkron",
                                            style = A3Theme.typography.label,
                                            color = colors.ink
                                        )
                                        Text(
                                            text = when {
                                                uiState.failedOutboxCount > 0 -> "${uiState.failedOutboxCount} data bermasalah (${uiState.unsyncedOutboxCount} antre)"
                                                uiState.unsyncedOutboxCount == 0 -> "Tersinkron penuh"
                                                else -> "${uiState.unsyncedOutboxCount} data menunggu"
                                            },
                                            style = A3Theme.typography.bodySmall,
                                            color = when {
                                                uiState.failedOutboxCount > 0 -> colors.status.danger.text
                                                uiState.unsyncedOutboxCount == 0 -> colors.inkMuted
                                                else -> colors.status.warning.text
                                            }
                                        )
                                    }
                                }
                                A3StatusChip(
                                    label = when {
                                        uiState.failedOutboxCount > 0 -> "${uiState.failedOutboxCount} Bermasalah"
                                        uiState.unsyncedOutboxCount == 0 -> "Sinkron"
                                        else -> "${uiState.unsyncedOutboxCount} Pending"
                                    },
                                    token = when {
                                        uiState.failedOutboxCount > 0 -> StatusToken.DANGER
                                        uiState.unsyncedOutboxCount == 0 -> StatusToken.SUCCESS
                                        else -> StatusToken.WARNING
                                    }
                                )
                            }

                            if (uiState.failedOutboxCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.status.danger.background)
                                        .border(1.dp, colors.status.danger.text.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.lastFailedError?.let { "Error: $it" } ?: "Beberapa bundel transaksi ditolak server.",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.status.danger.text,
                                        modifier = Modifier.weight(1f)
                                    )
                                    A3Button(
                                        label = if (uiState.isRetryingSync) "Mencoba..." else "Coba Kirim Ulang",
                                        onClick = onRetryFailedSync,
                                        enabled = !uiState.isRetryingSync,
                                        variant = ButtonVariant.SECONDARY
                                    )
                                }
                            }
                        }

                        // Detak Jantung / Heartbeat Perangkat (F41 & OPS-01)
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
                                    imageVector = Icons.Outlined.Sensors,
                                    contentDescription = "Heartbeat",
                                    tint = colors.ink,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Detak Jantung (Heartbeat)",
                                        style = A3Theme.typography.label,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = uiState.lastHeartbeatStatus ?: "Kirim telemetri berkala (15m)",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                            A3Button(
                                label = if (uiState.isSendingHeartbeat) "Mengirim..." else "Kirim",
                                onClick = onTriggerHeartbeat,
                                variant = ButtonVariant.SECONDARY,
                                enabled = !uiState.isSendingHeartbeat
                            )
                        }
                    }
                }

                // Kartu 2: Mode Kios
                A3Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode Kios",
                                style = A3Theme.typography.headline,
                                color = colors.ink
                            )
                            A3StatusChip(
                                label = if (uiState.isKioskActive) "Kios Aktif" else "Kios Bebas",
                                token = if (uiState.isKioskActive) StatusToken.INFO else StatusToken.NEUTRAL,
                                icon = if (uiState.isKioskActive) Icons.Outlined.Lock else Icons.Outlined.LockOpen
                            )
                        }

                        Text(
                            text = "Mengunci tablet pada aplikasi kasir dan membatasi navigasi sistem Android.",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (uiState.isKioskActive) {
                            A3Button(
                                label = "Lepas Mode Kios",
                                onClick = onDisableKiosk,
                                variant = ButtonVariant.DANGER,
                                fillWidth = true
                            )
                        } else {
                            A3Button(
                                label = "Aktifkan Mode Kios",
                                onClick = onEnableKiosk,
                                variant = ButtonVariant.PRIMARY,
                                fillWidth = true
                            )
                        }
                    }
                }
            }

            // KOLOM KANAN (52%): Pembaruan Aplikasi Mandiri
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
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Pembaruan Aplikasi",
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
                            text = "Pemasangan paket APK baru saat shift ditutup dan antrean sinkron bersih.",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted
                        )

                        // Indikator Prasyarat
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceAlt)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SYARAT PEMBARUAN",
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
                                    text = "Shift Kasir",
                                    style = A3Theme.typography.body,
                                    color = colors.ink
                                )
                                if (uiState.activeShiftCashierName != null) {
                                    A3StatusChip(
                                        label = "Aktif: ${uiState.activeShiftCashierName}",
                                        token = StatusToken.DANGER,
                                        icon = Icons.Outlined.Warning
                                    )
                                } else {
                                    A3StatusChip(
                                        label = "Ditutup",
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
                                    text = "Antrean Sinkron",
                                    style = A3Theme.typography.body,
                                    color = colors.ink
                                )
                                if (uiState.unsyncedOutboxCount > 0) {
                                    A3StatusChip(
                                        label = "${uiState.unsyncedOutboxCount} Menunggu",
                                        token = StatusToken.WARNING,
                                        icon = Icons.Outlined.Warning
                                    )
                                } else {
                                    A3StatusChip(
                                        label = "Bersih",
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
                                        .background(colors.status.success.background)
                                        .border(1.dp, colors.status.success.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = "Aman",
                                            tint = colors.status.success.text,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Siap Pasang Pembaruan",
                                            style = A3Theme.typography.label,
                                            color = colors.status.success.text,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "Kondisi tablet aman untuk memasang pembaruan.",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.status.success.text
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                A3Button(
                                    label = "Pasang Pembaruan APK",
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
                                        .background(colors.status.warning.background)
                                        .border(1.dp, colors.status.warning.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Warning,
                                            contentDescription = "Diblokir",
                                            tint = colors.status.warning.text,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Pembaruan Belum Diizinkan",
                                            style = A3Theme.typography.label,
                                            color = colors.status.warning.text,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    for (reason in eligibility.reasons) {
                                        Text(
                                            text = "• ${reason.description}",
                                            style = A3Theme.typography.bodySmall,
                                            color = colors.status.warning.text
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                A3Button(
                                    label = "Pembaruan Dinonaktifkan",
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
