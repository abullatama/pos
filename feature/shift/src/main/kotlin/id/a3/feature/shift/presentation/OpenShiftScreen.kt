package id.a3.feature.shift.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.shift.model.CashDenomination
import id.a3.feature.shift.model.DenominationType
import id.a3.feature.shift.model.StandardDenominations
import id.a3.feature.shift.repository.ActiveShift

/**
 * Layar S04: Buka Shift (Pola Penuh Fokus Tablet 11" Landscape).
 *
 * Mengimplementasikan aturan SHF-01 s/d SHF-04 (F30):
 * - Kasir menghitung uang fisik di laci kas per pecahan uang kertas & koin.
 * - Menampilkan kas awal standar (Rp200.000 bawaan OQ-06).
 * - Kas awal berbeda dari standar mewajibkan pengisian alasan selisih.
 * - Tombol pintasan "Isi Standar (Rp 200.000)" untuk efisiensi operasional kasir.
 */
@Composable
fun OpenShiftScreen(
    viewModel: ShiftViewModel,
    cashierName: String,
    cashierId: String,
    businessUnitId: String,
    deviceId: String,
    onShiftOpened: (ActiveShift) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // KOLOM KIRI (60%): Tabel Pecahan Uang Fisik
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header & Tombol Preset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Buka Shift Baru",
                            style = A3Theme.typography.title,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hitung uang fisik di laci kasir per pecahan",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(shapes.buttonShape)
                                .background(colors.surfaceAlt)
                                .border(1.dp, colors.border, shapes.buttonShape)
                                .clickable(onClick = viewModel::clearAllDenominations)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kosongkan",
                                style = A3Theme.typography.button,
                                color = colors.inkMuted,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(shapes.buttonShape)
                                .background(colors.accent.copy(alpha = 0.12f))
                                .border(1.dp, colors.accent, shapes.buttonShape)
                                .clickable(onClick = viewModel::applyStandardPreset)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Isi Standar (Rp 200.000)",
                                style = A3Theme.typography.button,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Tabel Daftar Pecahan Uang
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

                        DenominationRow(
                            denomination = denom,
                            count = count,
                            subtotal = subtotal,
                            onIncrement = { viewModel.adjustDenominationCount(denom.value, 1) },
                            onDecrement = { viewModel.adjustDenominationCount(denom.value, -1) }
                        )
                    }
                }
            }

            // KOLOM KANAN (40%): Panel Ringkasan Kas Awal & Konfirmasi
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Info Kasir & Target
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Ringkasan Kas Awal",
                            style = A3Theme.typography.headline,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kasir Bertugas:",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                            Text(
                                text = cashierName,
                                style = A3Theme.typography.bodySmall,
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Standar Kas Awal:",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                            Text(
                                text = formatRupiah(uiState.standardOpeningCash),
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Total Kas Awal Dihitung
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.cardShape)
                            .background(colors.canvas)
                            .border(1.dp, colors.border, shapes.cardShape)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Total Kas Awal Dihitung:",
                                style = A3Theme.typography.label,
                                color = colors.inkMuted
                            )
                            Text(
                                text = formatRupiah(uiState.openingCashTotal),
                                style = A3Theme.typography.headline,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )

                            // Status Chip Kesesuaian Standar
                            if (uiState.isDifferenceFromStandard) {
                                val diff = uiState.differenceAmount
                                val diffText = if (diff > 0) "+${formatRupiah(diff)}" else formatRupiah(diff)
                                A3StatusChip(
                                    label = "Selisih dari Standar: $diffText",
                                    token = StatusToken.WARNING
                                )
                            } else {
                                A3StatusChip(
                                    label = "Sesuai Standar (Rp 200.000)",
                                    token = StatusToken.SUCCESS
                                )
                            }
                        }
                    }

                    // Field Alasan Selisih (Wajib jika Berbeda dari Standar SHF-04)
                    if (uiState.isDifferenceFromStandard) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Alasan Selisih Kas Awal (Wajib):",
                                style = A3Theme.typography.label,
                                color = colors.status.warning.text,
                                fontWeight = FontWeight.Bold
                            )
                            A3TextField(
                                value = uiState.differenceReason,
                                onValueChange = viewModel::setDifferenceReason,
                                placeholder = "Cth: Uang kembalian kurang / modal toko baru...",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Aturan SHF-04: Kas awal yang berbeda dari standar wajib menyertakan alasan untuk audit log owner.",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted,
                                fontSize = 11.sp
                            )
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
                        }
                    }
                }

                // Tombol Eksekusi Buka Shift
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    A3Button(
                        label = if (uiState.isOpeningShift) "Membuka Shift..." else "Buka Shift Sekarang",
                        variant = ButtonVariant.PRIMARY,
                        enabled = uiState.canSubmitOpening,
                        onClick = {
                            viewModel.openShift(
                                cashierId = cashierId,
                                cashierName = cashierName,
                                businessUnitId = businessUnitId,
                                deviceId = deviceId,
                                onSuccess = onShiftOpened
                            )
                        },
                        fillWidth = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DenominationRow(
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
            // Label Nominal & Jenis
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

            // Stepper Jumlah Lembar (Touch Target 48x48 dp) & Subtotal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tombol [-]
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

                // Angka Lembar
                Text(
                    text = "$count $unitLabel",
                    style = A3Theme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = if (count > 0) colors.accent else colors.inkMuted,
                    fontSize = 15.sp
                )

                // Tombol [+]
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

                // Subtotal Per Pecahan (Rata Kanan)
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
