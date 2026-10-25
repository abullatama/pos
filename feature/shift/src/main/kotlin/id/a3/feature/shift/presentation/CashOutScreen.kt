package id.a3.feature.shift.presentation

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3EmptyState
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.ManagerPinApprovalDialog
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    storeName: String = "AA COFFEESHOP",
    storeAddress: String = "Jl. Kopi Pilihan No. 3",
    storePhone: String = "0812-3456-7890",
    modifier: Modifier = Modifier
) {
    LaunchedEffect(storeName, storeAddress, storePhone) {
        viewModel.setStoreProfile(storeName, storeAddress, storePhone)
    }

    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val focusManager = LocalFocusManager.current
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    A3Button(
                        label = "Lihat Riwayat",
                        onClick = {
                            viewModel.resetSuccess()
                            viewModel.selectTab(CashOutTab.HISTORY)
                        },
                        variant = ButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )

                    A3Button(
                        label = "Catat Lagi",
                        onClick = {
                            viewModel.resetSuccess()
                            viewModel.selectTab(CashOutTab.INPUT)
                        },
                        variant = ButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
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
            // Header Bar (Tanpa tombol kembali redundan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Kas Keluar Operasional",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "Pengeluaran kas laci saat shift aktif · Kasir: ${uiState.cashierName}",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                // Indikator Kontekstual Sesuai Tab
                if (uiState.selectedTab == CashOutTab.INPUT) {
                    if (uiState.amount > 50_000L) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.status.warning.background)
                                .border(1.dp, colors.status.warning.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = colors.status.warning.text,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Di atas Rp 50.000 · Perlu Foto & PIN",
                                color = colors.status.warning.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (uiState.amount > 0L) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.status.success.background)
                                .border(1.dp, colors.status.success.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
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
                                text = "Hingga Rp 50.000 · Tanpa PIN",
                                color = colors.status.success.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Total Shift: ${formatRupiah(uiState.totalCashOutAmount)}",
                            color = colors.ink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Segmented Tab Control (Catat Kas Keluar vs Riwayat Shift Ini)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isInputTab = uiState.selectedTab == CashOutTab.INPUT
                val isHistoryTab = uiState.selectedTab == CashOutTab.HISTORY

                // Tab 1: Catat Kas Keluar
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isInputTab) colors.accent else Color.Transparent)
                        .clickable { viewModel.selectTab(CashOutTab.INPUT) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = if (isInputTab) colors.onAccent else colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Catat Kas Keluar",
                        color = if (isInputTab) colors.onAccent else colors.ink,
                        fontSize = 12.sp,
                        fontWeight = if (isInputTab) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Tab 2: Riwayat Shift Ini
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHistoryTab) colors.accent else Color.Transparent)
                        .clickable { viewModel.selectTab(CashOutTab.HISTORY) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = if (isHistoryTab) colors.onAccent else colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uiState.cashOutHistory.isNotEmpty()) "Riwayat Kas Keluar (${uiState.cashOutHistory.size})" else "Riwayat Kas Keluar",
                        color = if (isHistoryTab) colors.onAccent else colors.ink,
                        fontSize = 12.sp,
                        fontWeight = if (isHistoryTab) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Pesan Error Inline
            uiState.errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.status.danger.background)
                        .border(1.dp, colors.status.danger.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = err,
                        color = colors.status.danger.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState.selectedTab == CashOutTab.INPUT) {
                // Grid Split 50 / 50 Tablet Landscape (Nominal & Keypad | Form & Simpan)
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
                    border = BorderStroke(1.dp, colors.border),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "NOMINAL PENGELUARAN",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )

                            // Kartu Display Nominal Besar dengan Tombol Reset Cepat
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceAlt)
                                    .border(
                                        1.dp,
                                        if (uiState.amount > 0L) colors.accent.copy(alpha = 0.5f) else colors.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    A3MoneyText(
                                        amount = uiState.amount,
                                        style = A3Theme.typography.headline.copy(
                                            fontSize = 28.sp,
                                            color = if (uiState.amount > 0L) colors.ink else colors.inkMuted
                                        )
                                    )

                                    if (uiState.amount > 0L) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(colors.border.copy(alpha = 0.2f))
                                                .clickable { viewModel.setAmount(0L) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Reset nominal",
                                                tint = colors.ink,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Tombol Preset Cepat (000, +10k, +20k, +50k, +100k)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Tombol Tambah Ribuan 000
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceAlt)
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (uiState.amount > 0L) {
                                                val newAmount = uiState.amount * 1000L
                                                if (newAmount <= 5_000_000L) {
                                                    viewModel.setAmount(newAmount)
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "000",
                                        color = colors.ink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

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

                        // Keypad Numerik 3x4 Taktil & Proporsional Tablet
                        Box(
                            modifier = Modifier
                                .widthIn(max = 340.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
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
                                showTripleZero = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ==================== PANEL KANAN (50%): KATEGORI, ALASAN & FOTO NOTA ====================
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = shapes.cardShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Pilihan Kategori dengan FlowRow Responsif
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Kategori Pengeluaran",
                                    style = A3Theme.typography.label,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkMuted
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CASHOUT_CATEGORIES.forEach { cat ->
                                        val isSelected = cat == uiState.category
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) colors.accent else colors.surfaceAlt)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) colors.accent else colors.border,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.setCategory(cat) }
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cat,
                                                color = if (isSelected) colors.onAccent else colors.ink,
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
                                label = "Keperluan Pengeluaran",
                                placeholder = "Contoh: Beli es batu kristal 2 karung",
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
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
                                        text = if (uiState.isPhotoRequired) "Foto Nota Fisik · Wajib" else "Foto Nota Fisik · Opsional",
                                        style = A3Theme.typography.label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isPhotoRequired && uiState.receiptBitmap == null) colors.status.warning.text else colors.inkMuted
                                    )

                                    if (uiState.receiptBitmap != null) {
                                        Text(
                                            text = "Hapus Foto",
                                            color = colors.status.danger.text,
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
                                            .border(
                                                1.dp,
                                                if (uiState.isPhotoRequired) colors.status.warning.text.copy(alpha = 0.5f) else colors.border,
                                                RoundedCornerShape(10.dp)
                                            )
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
                                                text = "Gunakan kamera tablet untuk memotret bukti belanja",
                                                color = colors.inkMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tombol Aksi Simpan Berjangkar di Dasar Kartu
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            A3Button(
                                label = if (uiState.isPinRequired) "Minta Otorisasi Manager" else "Catat Kas Keluar",
                                onClick = viewModel::submitCashOut,
                                variant = if (uiState.isPinRequired) ButtonVariant.DANGER else ButtonVariant.PRIMARY,
                                enabled = uiState.isFormValid && !uiState.isSubmitting,
                                fillWidth = true
                            )
                        }
                    }
                }
            }
        } else {
                // ==================== TAB RIWAYAT KAS KELUAR SHIFT AKTIF ====================
                CashOutHistoryView(
                    uiState = uiState,
                    onNavigateToInput = { viewModel.selectTab(CashOutTab.INPUT) },
                    onReprint = viewModel::reprintCashOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // Dialog Otorisasi PIN Manager
        if (uiState.isManagerPinDialogOpen) {
            ManagerPinApprovalDialog(
                title = "Otorisasi Kas Keluar",
                actionType = uiState.category,
                amountToApprove = uiState.amount,
                isOldTransaction = false,
                showAuditReasons = false,
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

@Composable
private fun CashOutHistoryView(
    uiState: CashOutUiState,
    onNavigateToInput: () -> Unit,
    onReprint: (CashOutEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PANEL KIRI (36%): Ringkasan Kas Keluar Shift Ini
        Surface(
            modifier = Modifier
                .weight(0.36f)
                .fillMaxHeight(),
            shape = shapes.cardShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Header Ringkasan
                    Row(
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
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Total Kas Keluar",
                                style = A3Theme.typography.title,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                            Text(
                                text = "Shift berjalan · ${uiState.cashierName}",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                    }

                    // Card Total Nominal Besar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "TOTAL TERPOTONG DARI LACI",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )
                            A3MoneyText(
                                amount = uiState.totalCashOutAmount,
                                style = A3Theme.typography.headline.copy(
                                    fontSize = 28.sp,
                                    color = if (uiState.totalCashOutAmount > 0L) colors.status.danger.text else colors.ink
                                )
                            )
                        }
                    }

                    // Statistik Frekuensi
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frekuensi Pengeluaran",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                        Text(
                            text = "${uiState.cashOutHistory.size} kali",
                            style = A3Theme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                    }

                    // Rincian Akumulasi per Kategori
                    if (uiState.cashOutHistory.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "RINCIAN PER KATEGORI",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )

                            val categoryTotals = remember(uiState.cashOutHistory) {
                                uiState.cashOutHistory.groupBy { it.category }
                                    .mapValues { entry -> entry.value.sumOf { it.amount } }
                            }

                            categoryTotals.forEach { (cat, sum) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cat,
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = formatRupiah(sum),
                                        style = A3Theme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.ink
                                    )
                                }
                            }
                        }
                    }

                    // Info Callout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.status.info.background)
                            .border(1.dp, colors.status.info.text.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Seluruh pengeluaran di atas otomatis memotong uang kas laci pada perhitungan Kas Seharusnya saat tutup shift (KAS-01).",
                            style = A3Theme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.status.info.text
                        )
                    }
                }

                // Tombol Kembali ke Form Input
                A3Button(
                    label = "+ Catat Kas Keluar Baru",
                    onClick = onNavigateToInput,
                    variant = ButtonVariant.PRIMARY,
                    fillWidth = true
                )
            }
        }

        // PANEL KANAN (64%): Daftar Kartu Riwayat Pengeluaran
        Surface(
            modifier = Modifier
                .weight(0.64f)
                .fillMaxHeight(),
            shape = shapes.cardShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 2.dp
        ) {
            if (uiState.cashOutHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    A3EmptyState(
                        title = "Belum Ada Kas Keluar",
                        description = "Belum ada catatan pengeluaran kas kecil yang dicatat pada shift ini.",
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Receipt,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(56.dp)
                            )
                        },
                        action = {
                            A3Button(
                                label = "Catat Kas Keluar Sekarang",
                                onClick = onNavigateToInput,
                                variant = ButtonVariant.PRIMARY
                            )
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Riwayat Kas Keluar (${uiState.cashOutHistory.size})",
                            style = A3Theme.typography.title,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = "Urut paling baru",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.cashOutHistory, key = { it.id }) { item ->
                            CashOutHistoryItemCard(
                                item = item,
                                onReprint = { onReprint(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashOutHistoryItemCard(
    item: CashOutEntity,
    onReprint: () -> Unit
) {
    val colors = A3Theme.colors
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID")) }
    val timeStr = remember(item.createdAt) { timeFormat.format(Date(item.createdAt)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Baris 1: Kategori, Jam, dan Nominal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = A3Theme.typography.label,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "Pukul $timeStr",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                A3MoneyText(
                    amount = item.amount,
                    style = A3Theme.typography.title.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                )
            }

            // Baris 2: Keperluan / Alasan
            Text(
                text = item.reason,
                style = A3Theme.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink
            )

            // Baris 3: Status Foto, Otorisasi PIN, dan Tombol Cetak Salinan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge Otorisasi
                    if (item.approvedBy != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.status.warning.background)
                                .border(1.dp, colors.status.warning.text.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.status.warning.text,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "PIN: ${item.approvedBy}",
                                color = colors.status.warning.text,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.status.success.background)
                                .border(1.dp, colors.status.success.text.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Kasir Mandiri",
                                color = colors.status.success.text,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Badge Foto Nota
                    val hasPhoto = !item.receiptFileId.isNullOrBlank() || !item.receiptFilePath.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (hasPhoto) colors.status.info.background else colors.canvas)
                            .border(1.dp, if (hasPhoto) colors.status.info.text.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = if (hasPhoto) colors.status.info.text else colors.inkMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (hasPhoto) "Ada Foto Nota" else "Tanpa Nota",
                            color = if (hasPhoto) colors.status.info.text else colors.inkMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Tombol Cetak Salinan (Reprint)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .clickable(onClick = onReprint)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Print,
                        contentDescription = "Cetak Salinan",
                        tint = colors.ink,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Cetak Salinan",
                        color = colors.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
