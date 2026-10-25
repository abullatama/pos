package id.a3.feature.shift.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Card
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.shift.model.CashDenomination
import id.a3.feature.shift.model.DenominationType
import id.a3.feature.shift.model.StandardDenominations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar Alur Tutup Shift Terpadu (S09, S10, S11 - Tablet 11" Landscape).
 *
 * Mengimplementasikan aturan TUP-01 s/d TUP-07 (F31, F32, F34):
 * - Tahap 1 (S09): Hitung kas buta per pecahan (angka sistem disembunyikan 100%).
 * - Tahap 2 (S10): Ceklis QRIS Statis shift ini & evaluasi selisih kas (Test Vector K1, toleransi OQ-07).
 * - Tahap 3 (S11): Catatan alokasi kas (laci + setor/owner) & pratinjau Laporan Z sebelum cetak.
 */
@Composable
fun CloseShiftScreen(
    viewModel: CloseShiftViewModel,
    shiftId: String,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    onCloseCompleted: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(shiftId) {
        viewModel.initializeWithShift(
            shiftId = shiftId,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar Wizard: Tombol Batal & Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val onBackAction = when (uiState.currentStep) {
                        CloseShiftStep.STEP_1_BLIND_COUNT -> {
                            {
                                viewModel.resetState()
                                onCancel()
                            }
                        }
                        CloseShiftStep.STEP_2_DIFF_AND_QRIS -> viewModel::openRecountPinDialog
                        CloseShiftStep.STEP_3_ALLOCATION_AND_Z -> viewModel::backToStep2
                    }

                    IconButton(
                        onClick = onBackAction,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = colors.ink
                        )
                    }

                    Column {
                        Text(
                            text = "Penutupan Shift Kasir",
                            style = A3Theme.typography.title,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = "Shift #${shiftId.take(8)} · ${uiState.summary?.shift?.cashierName ?: "Kasir"}",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }

                // Step Tracker 3-Tahap
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StepBadge(stepNumber = 1, label = "Hitung Kas", isActive = uiState.currentStep == CloseShiftStep.STEP_1_BLIND_COUNT, isCompleted = uiState.currentStep > CloseShiftStep.STEP_1_BLIND_COUNT)
                    Text(text = "→", color = colors.inkMuted, fontSize = 12.sp)
                    StepBadge(stepNumber = 2, label = "Selisih & QRIS", isActive = uiState.currentStep == CloseShiftStep.STEP_2_DIFF_AND_QRIS, isCompleted = uiState.currentStep > CloseShiftStep.STEP_2_DIFF_AND_QRIS)
                    Text(text = "→", color = colors.inkMuted, fontSize = 12.sp)
                    StepBadge(stepNumber = 3, label = "Alokasi & Laporan Z", isActive = uiState.currentStep == CloseShiftStep.STEP_3_ALLOCATION_AND_Z, isCompleted = uiState.isSuccess)
                }
            }

            // Banner Serah Terima Pesanan Terbuka
            val openOrdersCount = uiState.summary?.openOrderIds?.size ?: 0
            if (openOrdersCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.cardShape)
                        .background(colors.status.warning.background)
                        .border(1.dp, colors.status.warning.text.copy(alpha = 0.5f), shapes.cardShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.status.warning.text,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Terdapat $openOrdersCount pesanan aktif masih terbuka. Pesanan ini otomatis dialihkan ke shift kasir berikutnya.",
                            style = A3Theme.typography.bodySmall,
                            color = colors.status.warning.text,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pesan Error jika Ada
            if (uiState.error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.cardShape)
                        .background(colors.status.danger.background)
                        .border(1.dp, colors.status.danger.text, shapes.cardShape)
                        .padding(12.dp)
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
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = colors.status.danger.text,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.error.orEmpty(),
                                style = A3Theme.typography.bodySmall,
                                color = colors.status.danger.text,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = viewModel::dismissError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = colors.status.danger.text, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Konten Sesuai Step Aktif
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState.currentStep) {
                    CloseShiftStep.STEP_1_BLIND_COUNT -> {
                        Step1BlindCountContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    CloseShiftStep.STEP_2_DIFF_AND_QRIS -> {
                        Step2DiffAndQrisContent(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    CloseShiftStep.STEP_3_ALLOCATION_AND_Z -> {
                        Step3AllocationAndZContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            onCloseCompleted = onCloseCompleted
                        )
                    }
                }
            }

            // Dialog Proteksi Hitung Ulang Kasir (TUP-03)
            if (uiState.isRecountPinDialogOpen) {
                id.a3.core.ui.component.ManagerPinApprovalDialog(
                    title = "Otorisasi Hitung Ulang Kas",
                    actionType = "Hitung Ulang Kasir",
                    amountToApprove = uiState.countedCash,
                    isOldTransaction = false,
                    showAuditReasons = false,
                    errorMessage = uiState.recountPinError,
                    isManagerLocked = uiState.isRecountLocked,
                    lockoutRemainingSeconds = uiState.recountLockoutSeconds,
                    onDismiss = viewModel::closeRecountPinDialog,
                    onSubmitApproval = { pin, _, _ ->
                        viewModel.verifyManagerPinForRecount(pin)
                    }
                )
            }
        }
    }
}

// =========================================================================
// TAHAP 1 (S09): HITUNG KAS BUTA (TUP-02)
// =========================================================================

@Composable
private fun Step1BlindCountContent(
    uiState: CloseShiftUiState,
    viewModel: CloseShiftViewModel
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Kiri (60%): Tabel Pecahan
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hitung Kas Fisik Laci (Hitung Buta)",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "Isi jumlah lembar dan keping uang yang ada di laci kasir",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(shapes.buttonShape)
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.border, shapes.buttonShape)
                        .clickable(onClick = viewModel::clearAllDenominations)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Kosongkan", style = A3Theme.typography.button, color = colors.inkMuted, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(StandardDenominations.ALL, key = { it.value }) { denom ->
                    val count = uiState.denominations[denom.value] ?: 0
                    val subtotal = denom.value * count

                    CloseDenominationRow(
                        denomination = denom,
                        count = count,
                        subtotal = subtotal,
                        onIncrement = { viewModel.adjustDenominationCount(denom.value, 1) },
                        onDecrement = { viewModel.adjustDenominationCount(denom.value, -1) }
                    )
                }
            }
        }

        // Kanan (40%): Ringkasan Hitungan & Peringatan Buta
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Total Hitungan Kasir",
                    style = A3Theme.typography.headline,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.cardShape)
                        .background(colors.canvas)
                        .border(1.dp, colors.border, shapes.cardShape)
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Uang Fisik Dihitung:",
                            style = A3Theme.typography.label,
                            color = colors.inkMuted
                        )
                        Text(
                            text = formatRupiah(uiState.countedCash),
                            style = A3Theme.typography.headline,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                    }
                }

                // Kotak Peringatan Hitung Buta
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.cardShape)
                        .background(colors.status.info.background)
                        .border(1.dp, colors.status.info.text.copy(alpha = 0.3f), shapes.cardShape)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.status.info.text,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Prosedur Hitung Kas Fisik",
                                style = A3Theme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.status.info.text
                            )
                            Text(
                                text = "Sesuai standar audit, angka penjualan sistem disembunyikan sampai Anda selesai menghitung fisik dan menekan tombol lanjut.",
                                style = A3Theme.typography.bodySmall,
                                color = colors.status.info.text,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            A3Button(
                label = "Lanjut ke Evaluasi Selisih",
                variant = ButtonVariant.PRIMARY,
                enabled = uiState.countedCash > 0L,
                onClick = viewModel::proceedToStep2,
                fillWidth = true
            )
        }
    }
}

// =========================================================================
// TAHAP 2 (S10): SELISIH & CEK QRIS STATIS (TUP-03, TUP-04)
// =========================================================================

@Composable
private fun Step2DiffAndQrisContent(
    uiState: CloseShiftUiState,
    viewModel: CloseShiftViewModel
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val qrisList = uiState.summary?.qrisPayments ?: emptyList()

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Kiri (60%): Daftar Cek QRIS Statis (TUP-04)
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Verifikasi Transaksi QRIS",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "Cocokkan transaksi QRIS di tablet dengan notifikasi aplikasi merchant",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                if (qrisList.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(shapes.buttonShape)
                            .background(colors.accent.copy(alpha = 0.12f))
                            .clickable(onClick = viewModel::checkAllQris)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Tandai Semua Selesai",
                            style = A3Theme.typography.button,
                            color = colors.accent,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (qrisList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shapes.cardShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, shapes.cardShape)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Tidak Ada Transaksi QRIS",
                            style = A3Theme.typography.body,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                        Text(
                            text = "Shift ini tidak memiliki transaksi pembayaran menggunakan QRIS Statis.",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shapes.cardShape)
                        .background(colors.surface)
                        .border(1.dp, colors.border, shapes.cardShape)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(qrisList, key = { it.id }) { payment ->
                        val isChecked = uiState.checkedQrisPaymentIds.contains(payment.id)
                        QrisCheckRow(
                            payment = payment,
                            isChecked = isChecked,
                            onToggle = { viewModel.toggleQrisChecked(payment.id) }
                        )
                    }
                }
            }
        }

        // Kanan (40%): Perbandingan Kas Seharusnya vs Dihitung & Selisih
        Column(
            modifier = Modifier
                .weight(0.40f)
                .fillMaxHeight()
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Evaluasi Selisih Kas",
                style = A3Theme.typography.headline,
                color = colors.ink,
                fontWeight = FontWeight.Bold
            )

            // Rincian Arus Kas Sistem & Evaluasi Selisih
            val openingCash = uiState.summary?.shift?.openingCash ?: 0L
            val cashSales = uiState.summary?.cashSales ?: 0L
            val cashDp = uiState.summary?.cashDpAndSettlement ?: 0L
            val cashOuts = uiState.summary?.cashOuts ?: 0L
            val cashRefunds = uiState.summary?.totalRefund ?: 0L

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.cardShape)
                    .background(colors.canvas)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Alur Mutasi Kas Sistem (Laci):",
                        style = A3Theme.typography.label,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "(+) Kas Awal Modal", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(text = formatRupiah(openingCash), style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.ink)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "(+) Penjualan Tunai", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(text = formatRupiah(cashSales), style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.ink)
                    }

                    if (cashDp > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "(+) DP & Pelunasan Tunai", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                            Text(text = formatRupiah(cashDp), style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.ink)
                        }
                    }

                    if (cashOuts > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "(-) Kas Keluar (Petty Cash)", style = A3Theme.typography.bodySmall, color = colors.status.danger.text)
                            Text(text = "-${formatRupiah(cashOuts)}", style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.status.danger.text)
                        }
                    }

                    if (cashRefunds > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "(-) Refund Tunai", style = A3Theme.typography.bodySmall, color = colors.status.danger.text)
                            Text(text = "-${formatRupiah(cashRefunds)}", style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Medium, color = colors.status.danger.text)
                        }
                    }

                    HorizontalDivider(color = colors.border)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "(=) Kas Seharusnya (Sistem):", style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.ink)
                        Text(text = formatRupiah(uiState.expectedCash), style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = colors.ink)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Kas Dihitung (Fisik):", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(text = formatRupiah(uiState.countedCash), style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = colors.accent)
                    }

                    HorizontalDivider(color = colors.border)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Selisih Kas:", style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = colors.ink)
                        val diff = uiState.cashDifference
                        val diffFormatted = when {
                            diff == 0L -> "Rp 0 (Pas)"
                            diff > 0 -> "+${formatRupiah(diff)}"
                            else -> "-${formatRupiah(-diff)}"
                        }
                        Text(
                            text = diffFormatted,
                            style = A3Theme.typography.body,
                            fontWeight = FontWeight.Bold,
                            color = if (diff == 0L) colors.status.success.text else if (uiState.isDifferenceOverTolerance) colors.status.danger.text else colors.status.warning.text
                        )
                    }
                }
            }

            // Toleransi Status & Alasan Selisih
            if (uiState.isDifferenceOverTolerance) {
                A3StatusChip(
                    label = "Selisih di atas toleransi Rp 2.000",
                    token = StatusToken.DANGER
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Alasan Selisih Kas (Wajib diisi):",
                        style = A3Theme.typography.label,
                        color = colors.status.danger.text,
                        fontWeight = FontWeight.Bold
                    )
                    A3TextField(
                        value = uiState.differenceReason,
                        onValueChange = viewModel::setDifferenceReason,
                        placeholder = "Cth: Selisih kembalian / salah hitung fisik...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (uiState.cashDifference != 0L) {
                A3StatusChip(
                    label = "Dalam batas toleransi wajar (≤ Rp 2.000)",
                    token = StatusToken.WARNING
                )
            } else {
                A3StatusChip(
                    label = "Kas Pas Tanpa Selisih",
                    token = StatusToken.SUCCESS
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                A3Button(
                    label = "Lanjut ke Alokasi Kas",
                    variant = ButtonVariant.PRIMARY,
                    enabled = uiState.isStep2Valid,
                    onClick = viewModel::proceedToStep3,
                    fillWidth = true
                )

                A3Button(
                    label = "Hitung Ulang Kas (Butuh PIN Manager)",
                    variant = ButtonVariant.GHOST,
                    onClick = viewModel::openRecountPinDialog,
                    fillWidth = true
                )
            }
        }
    }
}

// =========================================================================
// TAHAP 3 (S11): ALOKASI KAS & PRATINJAU LAPORAN Z (TUP-05, TUP-06)
// =========================================================================

@Composable
private fun Step3AllocationAndZContent(
    uiState: CloseShiftUiState,
    viewModel: CloseShiftViewModel,
    onCloseCompleted: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val summary = uiState.summary

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Kiri (50%): Form Alokasi Kas
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight()
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Catatan Alokasi Kas",
                style = A3Theme.typography.headline,
                color = colors.ink,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tentukan pembagian uang fisik yang ada di laci saat ini:",
                style = A3Theme.typography.bodySmall,
                color = colors.inkMuted
            )

            // Input Kas Ditinggal di Laci
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "1. Ditinggal di Laci (Modal Shift Berikutnya):",
                    style = A3Theme.typography.label,
                    color = colors.ink
                )
                A3TextField(
                    value = if (uiState.cashLeft == 0L) "" else uiState.cashLeft.toString(),
                    onValueChange = { str ->
                        val num = str.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        viewModel.setCashLeft(num)
                    },
                    placeholder = "Rp 200.000",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Input Kas Disetor ke Owner / Bank
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "2. Disetor ke Owner / Bank:",
                    style = A3Theme.typography.label,
                    color = colors.ink
                )
                A3TextField(
                    value = if (uiState.cashDeposited == 0L) "" else uiState.cashDeposited.toString(),
                    onValueChange = { str ->
                        val num = str.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        viewModel.setCashDeposited(num)
                    },
                    placeholder = "Rp 0",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Nama Penerima jika Uang Dibawa
            if (uiState.cashDeposited > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "3. Nama Penerima / Yang Membawa Uang (Wajib):",
                        style = A3Theme.typography.label,
                        color = colors.ink
                    )
                    A3TextField(
                        value = uiState.cashTakenBy,
                        onValueChange = viewModel::setCashTakenBy,
                        placeholder = "Cth: Pak Owner / Bu Dewi / Disetor Bank...",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Ringkasan Keseimbangan Alokasi Kas
            val isBalanced = uiState.isAllocationBalanced
            val statusColor = if (isBalanced) colors.status.success else colors.status.danger
            val diffAllocation = uiState.countedCash - uiState.totalAllocated

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.cardShape)
                    .background(statusColor.background)
                    .border(1.dp, statusColor.text.copy(alpha = 0.35f), shapes.cardShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isBalanced) "Alokasi Sesuai Kas Fisik" else "Alokasi Belum Seimbang",
                            style = A3Theme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor.text
                        )
                        Text(
                            text = if (isBalanced) {
                                "Total alokasi pas ${formatRupiah(uiState.totalAllocated)}"
                            } else if (diffAllocation > 0) {
                                "Kurang ${formatRupiah(diffAllocation)} dari total fisik (${formatRupiah(uiState.countedCash)})"
                            } else {
                                "Lebih ${formatRupiah(-diffAllocation)} dari total fisik (${formatRupiah(uiState.countedCash)})"
                            },
                            style = A3Theme.typography.bodySmall,
                            color = statusColor.text,
                            fontSize = 11.sp
                        )
                    }

                    A3StatusChip(
                        label = if (isBalanced) "Seimbang" else "Belum Pas",
                        token = if (isBalanced) StatusToken.SUCCESS else StatusToken.DANGER
                    )
                }
            }

            // Catatan Penyimpanan Offline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Data shift dan Laporan Z tersimpan lokal dan disinkronkan otomatis saat online.",
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                A3Button(
                    label = if (uiState.isClosing) "Menutup Shift & Mencetak..." else "Tutup Shift & Cetak Laporan Z",
                    variant = ButtonVariant.PRIMARY,
                    enabled = uiState.canProceedToClose,
                    onClick = {
                        viewModel.submitCloseShift(onSuccess = onCloseCompleted)
                    },
                    fillWidth = true
                )

                A3Button(
                    label = "Kembali ke Selisih",
                    variant = ButtonVariant.GHOST,
                    onClick = viewModel::backToStep2,
                    fillWidth = true
                )
            }
        }

        // Kanan (50%): Pratinjau Thermal Paper Laporan Z (TUP-05)
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight()
                .clip(shapes.cardShape)
                .background(Color(0xFFFCFCFD))
                .border(1.5.dp, colors.border, shapes.cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Kertas Struk 58mm Pratinjau
                Text(text = uiState.storeName, style = A3Theme.typography.title, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = uiState.storeAddress, style = A3Theme.typography.bodySmall, color = Color.Gray, fontSize = 11.sp)
                Text(text = "================================", color = Color.LightGray)
                Text(text = "LAPORAN Z (PENUTUPAN SHIFT)", style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "================================", color = Color.LightGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Kasir:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = summary?.shift?.cashierName ?: "-", style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Buka:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = summary?.shift?.openedAt?.take(16) ?: "-", style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Text(text = "--------------------------------", color = Color.LightGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Penjualan Kotor:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = formatRupiah(summary?.grossSales ?: 0L), style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Penjualan Bersih:", style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = formatRupiah(summary?.netSales ?: 0L), style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Total Transaksi:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = "${summary?.transactionCount ?: 0} nota", style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Text(text = "--------------------------------", color = Color.LightGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Kas Seharusnya:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = formatRupiah(uiState.expectedCash), style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Kas Dihitung Fisik:", style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(text = formatRupiah(uiState.countedCash), style = A3Theme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Selisih:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = formatRupiah(uiState.cashDifference), style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Text(text = "--------------------------------", color = Color.LightGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Ditinggal di Laci:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = formatRupiah(uiState.cashLeft), style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Disetor ke Owner:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                    Text(text = formatRupiah(uiState.cashDeposited), style = A3Theme.typography.bodySmall, color = Color.Black)
                }
                if (uiState.cashTakenBy.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Uang Dibawa:", style = A3Theme.typography.bodySmall, color = Color.Gray)
                        Text(text = uiState.cashTakenBy, style = A3Theme.typography.bodySmall, color = Color.Black)
                    }
                }
                Text(text = "================================", color = Color.LightGray)
                Text(text = "*** LAPORAN Z SELESAI ***", style = A3Theme.typography.bodySmall, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

// =========================================================================
// KOMPONEN PENDUKUNG
// =========================================================================

@Composable
private fun StepBadge(
    stepNumber: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val bg = when {
        isCompleted -> colors.status.success.background
        isActive -> colors.accent
        else -> colors.surfaceAlt
    }

    val textColor = when {
        isCompleted -> colors.status.success.text
        isActive -> colors.onAccent
        else -> colors.inkMuted
    }

    Row(
        modifier = Modifier
            .clip(shapes.buttonShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = "$stepNumber",
                style = A3Theme.typography.label,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            )
        }
        Text(
            text = label,
            style = A3Theme.typography.bodySmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CloseDenominationRow(
    denomination: CashDenomination,
    count: Int,
    subtotal: Long,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val unitLabel = if (denomination.type == DenominationType.BILL) "lbr" else "kpg"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.buttonShape)
            .background(if (count > 0) colors.surfaceAlt else colors.surface)
            .border(
                width = if (count > 0) 1.dp else 0.5.dp,
                color = if (count > 0) colors.accent else colors.border,
                shape = shapes.buttonShape
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (denomination.type == DenominationType.BILL) colors.accent.copy(alpha = 0.1f) else colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (denomination.type == DenominationType.BILL) colors.accent else colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = denomination.label,
                        style = A3Theme.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = if (denomination.type == DenominationType.BILL) "Uang Kertas" else "Uang Logam",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (count > 0) colors.surface else colors.surfaceAlt)
                        .border(1.dp, if (count > 0) colors.border else Color.Transparent, CircleShape)
                        .clickable(enabled = count > 0, onClick = onDecrement),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Kurang 1",
                        tint = if (count > 0) colors.ink else colors.inkMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "$count $unitLabel",
                    style = A3Theme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = if (count > 0) colors.accent else colors.inkMuted,
                    fontSize = 15.sp
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .clickable(onClick = onIncrement),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah 1",
                        tint = colors.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = formatRupiah(subtotal),
                    style = A3Theme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (subtotal > 0) colors.ink else colors.inkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.width(95.dp)
                )
            }
        }
    }
}

@Composable
private fun QrisCheckRow(
    payment: PaymentEntity,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val dateFormat = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
    val timeStr = dateFormat.format(Date(payment.createdAt))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.buttonShape)
            .background(if (isChecked) colors.surfaceAlt else colors.surface)
            .border(1.dp, if (isChecked) colors.status.success.text else colors.border, shapes.buttonShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.status.success.text,
                        uncheckedColor = colors.inkMuted
                    ),
                    modifier = Modifier.size(44.dp)
                )

                Column {
                    Text(
                        text = "QRIS Statis · $timeStr",
                        style = A3Theme.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "ID: ${payment.id.take(8)}",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = formatRupiah(payment.amount),
                style = A3Theme.typography.body,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }
    }
}
