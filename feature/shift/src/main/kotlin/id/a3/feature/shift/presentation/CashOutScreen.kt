package id.a3.feature.shift.presentation

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.ManagerPinApprovalDialog
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme

const val CASH_OUT_SCREEN_TAG = "cash_out_screen_tag"

/**
 * Layar S12 Kas Keluar Operasional / Petty Cash Laci.
 * Sesuai aturan KAS-01, KAS-02, OQ-08, dan antislop design guidelines:
 * - Layout split 50/50 tablet landscape 11" responsif.
 * - Input nominal uang taktil via Keypad 3x4 atau Preset Cepat (Rp 10k, 20k, 50k, 100k).
 * - Segmented chips pilihan kategori pengeluaran (Bahan baku, Operasional, Perawatan, Kemasan, Lain-lain).
 * - Input alasan dan pengambilan foto nota via kamera tablet.
 * - Penegakan batas Rp 50.000: di bawah batas bebas PIN, di atas batas wajib PIN Manager & foto nota.
 */
@Composable
fun CashOutScreen(
    viewModel: CashOutViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()

    // Activity launcher untuk ambil foto bukti nota dari kamera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.setReceiptPhoto(bitmap)
        }
    }

    if (uiState.isSuccess) {
        // Tampilan Sukses Kas Keluar
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.status.success.background),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.status.success.text,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Kas Keluar Berhasil Dicatat",
                    style = A3Theme.typography.title,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                Text(
                    text = "Nominal ${formatRupiah(uiState.amount)} telah dicatat keluar dari laci kas dan struk bukti pengeluaran telah dikirim ke printer.",
                    style = A3Theme.typography.body,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                A3Button(
                    label = "Selesai",
                    onClick = onBack,
                    variant = ButtonVariant.PRIMARY,
                    fillWidth = true
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(CASH_OUT_SCREEN_TAG),
        color = colors.canvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = colors.ink
                        )
                    }

                    Column {
                        Text(
                            text = "Kas Keluar Operasional (Peti Kas)",
                            style = A3Theme.typography.title,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = "Pengeluaran uang tunai dari laci kas saat shift aktif • Kasir: ${uiState.cashierName}",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }

                // Status Ambang Batas
                if (uiState.amount > 50_000L) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "> Rp 50.000 (Wajib Foto & PIN Manager)",
                            color = Color(0xFFE65100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (uiState.amount > 0L) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.status.success.background)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.status.success.text,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "≤ Rp 50.000 (Bebas PIN Manager)",
                            color = colors.status.success.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Pesan Error Inline
            uiState.errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFEBEE))
                        .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = err,
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Grid Split 50 / 50 Tablet Landscape
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==================== PANEL KIRI (50%): NOMINAL & KEYPAD ====================
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = shapes.cardShape,
                    color = colors.surface,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "NOMINAL PENGELUARAN",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )

                            // Kartu Display Nominal Besar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceAlt)
                                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                    .padding(vertical = 16.dp, horizontal = 18.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                A3MoneyText(
                                    amount = uiState.amount,
                                    style = A3Theme.typography.headline.copy(
                                        fontSize = 28.sp,
                                        color = if (uiState.amount > 0L) colors.ink else colors.inkMuted
                                    )
                                )
                            }

                            // Tombol Preset Cepat (+10k, +20k, +50k, +100k, Pas 50k)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10_000L, 20_000L, 50_000L, 100_000L).forEach { preset ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.canvas)
                                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                            .clickable { viewModel.addAmountPreset(preset) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${preset / 1000}rb",
                                            color = colors.ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Keypad Numerik 3x4 Taktil
                        A3Keypad(
                            onDigitClick = { digit ->
                                val currentStr = uiState.amount.toString()
                                val newStr = if (uiState.amount == 0L) digit else currentStr + digit
                                val newAmount = newStr.toLongOrNull() ?: uiState.amount
                                if (newAmount <= 5_000_000L) {
                                    viewModel.setAmount(newAmount)
                                }
                            },
                            onBackspace = {
                                val currentStr = uiState.amount.toString()
                                if (currentStr.length > 1) {
                                    val newStr = currentStr.dropLast(1)
                                    viewModel.setAmount(newStr.toLongOrNull() ?: 0L)
                                } else {
                                    viewModel.setAmount(0L)
                                }
                            },
                            onClear = {
                                viewModel.setAmount(0L)
                            },
                            showTripleZero = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ==================== PANEL KANAN (50%): KATEGORI, ALASAN & FOTO NOTA ====================
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = shapes.cardShape,
                    color = colors.surface,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Pilihan Kategori
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "KATEGORI PENGELUARAN (Wajib)",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CASHOUT_CATEGORIES.forEach { cat ->
                                    val isSelected = cat == uiState.category
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) colors.accent else colors.surfaceAlt)
                                            .clickable { viewModel.setCategory(cat) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else colors.ink,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Alasan / Deskripsi Pengeluaran
                        A3TextField(
                            value = uiState.reason,
                            onValueChange = viewModel::setReason,
                            label = "Alasan / Keperluan Pengeluaran",
                            placeholder = "Contoh: Beli es batu kristal 2 karung",
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. Bukti Foto Nota Fisik
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.isPhotoRequired) "FOTO NOTA FISIK (Wajib > Rp 50.000)" else "FOTO NOTA FISIK (Opsional)",
                                    style = A3Theme.typography.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isPhotoRequired && uiState.receiptBitmap == null) Color(0xFFE65100) else colors.inkMuted
                                )

                                if (uiState.receiptBitmap != null) {
                                    Text(
                                        text = "Hapus Foto",
                                        color = Color(0xFFC62828),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { viewModel.clearReceiptPhoto() }
                                    )
                                }
                            }

                            if (uiState.receiptBitmap != null) {
                                // Pratinjau Foto Nota
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = uiState.receiptBitmap!!.asImageBitmap(),
                                        contentDescription = "Nota Fisik",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                // Tombol Buka Kamera Ambil Nota
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceAlt)
                                        .border(1.dp, if (uiState.isPhotoRequired) Color(0xFFFFB74D) else colors.border, RoundedCornerShape(10.dp))
                                        .clickable { cameraLauncher.launch(null) }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CameraAlt,
                                            contentDescription = "Kamera",
                                            tint = colors.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Ambil Foto Nota Fisik",
                                            color = colors.ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Gunakan kamera tablet untuk memotret bukti nota belanja",
                                            color = colors.inkMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f, fill = false))

                        // Tombol Aksi Simpan
                        A3Button(
                            label = if (uiState.isPinRequired) "Lanjutkan (Otorisasi PIN Manager)" else "Catat Kas Keluar",
                            onClick = viewModel::submitCashOut,
                            variant = if (uiState.isPinRequired) ButtonVariant.DANGER else ButtonVariant.PRIMARY,
                            enabled = uiState.isFormValid && !uiState.isSubmitting,
                            fillWidth = true
                        )
                    }
                }
            }
        }

        // Dialog S14 PIN Manager (KAS-02)
        if (uiState.isManagerPinDialogOpen) {
            ManagerPinApprovalDialog(
                title = "Otorisasi Kas Keluar > Rp 50.000",
                actionType = "KAS KELUAR (${uiState.category.uppercase()})",
                amountToApprove = uiState.amount,
                isOldTransaction = false,
                errorMessage = uiState.managerPinErrorMessage,
                isManagerLocked = uiState.isManagerLocked,
                lockoutRemainingSeconds = uiState.lockoutRemainingSeconds,
                onDismiss = viewModel::closeManagerPinDialog,
                onSubmitApproval = { pin, _, _ ->
                    viewModel.verifyManagerPinAndSubmit(pin)
                }
            )
        }
    }
}
