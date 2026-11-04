package id.a3.feature.laundry.presentation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import id.a3.core.ui.component.A3TextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layar L05 (Daftar Cucian) & L06 (Detail Cucian) - Athia Laundry House.
 * Pola split-screen 40/60 untuk tablet landscape 11 inci:
 * - Panel Kiri (40%): Segmented tab filter status & daftar kartu cucian.
 * - Panel Kanan (60%): Detail pesanan, rincian pakaian, status pembayaran & sisa,
 *   garis waktu status, tombol penyerahan L08 (F22, F24), cuci ulang L09 (F23),
 *   pelunasan pembayaran, dan cetak ulang tag.
 */
@Composable
fun LaundryOrdersScreen(
    viewModel: LaundryOrdersViewModel,
    hasActiveShift: Boolean = true,
    onNavigateToShift: () -> Unit = {},
    onProceedToSettlement: (orderId: String, remainingAmount: Long, displayNumber: String) -> Unit,
    onOpenScanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()
    var showShiftRequiredDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PANEL KIRI (40% — L05: Daftar Cucian)
            Column(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header & Jumlah Antrean
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Cucian",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "${uiState.orders.size} Pesanan",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                // Filter Tabs (44dp touch target)
                FilterStatusTabs(
                    activeFilter = uiState.filterStatus,
                    onFilterSelected = viewModel::setFilterStatus
                )

                // Bar Pencarian Nota & Tombol Scan QR Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        A3TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = "Cari nomor nota...",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(shapes.buttonShape)
                            .background(colors.surfaceAlt)
                            .border(1.dp, colors.border, shapes.buttonShape)
                            .clickable(onClick = onOpenScanner),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Pindai Tag QR",
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // List Kartu Cucian
                if (uiState.orders.isEmpty()) {
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
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = colors.inkMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Tidak Ada Cucian",
                                style = A3Theme.typography.title,
                                color = colors.ink
                            )
                            Text(
                                text = "Tidak ada pesanan pada kategori ${uiState.filterStatus.displayName}",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.orders, key = { it.order.id }) { orderWithLines ->
                            val isSelected = uiState.selectedOrder?.order?.id == orderWithLines.order.id
                            LaundryOrderCardItem(
                                orderWithLines = orderWithLines,
                                isSelected = isSelected,
                                onClick = { viewModel.selectOrder(orderWithLines) }
                            )
                        }
                    }
                }
            }

            // PANEL KANAN (60% — L06: Detail Cucian & Penyerahan)
            Box(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight()
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(20.dp)
            ) {
                val selected = uiState.selectedOrder
                if (selected == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pilih pesanan di sebelah kiri untuk melihat detail",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted
                        )
                    }
                } else {
                    OrderDetailPanel(
                        orderWithLines = selected,
                        uiState = uiState,
                        viewModel = viewModel,
                        hasActiveShift = hasActiveShift,
                        onRequireShift = { showShiftRequiredDialog = true },
                        onProceedToSettlement = onProceedToSettlement
                    )
                }
            }
        }

        if (showShiftRequiredDialog) {
            id.a3.core.ui.component.A3Dialog(
                onDismissRequest = { showShiftRequiredDialog = false },
                title = "Buka Shift Kasir",
                bodyText = "Penerimaan uang kas pelunasan membutuhkan shift aktif. Buka shift kasir terlebih dahulu.",
                confirmLabel = "Buka Shift Sekarang",
                dismissLabel = "Batal",
                onConfirm = {
                    showShiftRequiredDialog = false
                    onNavigateToShift()
                },
                onDismiss = { showShiftRequiredDialog = false }
            )
        }

        // Feedback Bar (Sukses / Error)
        if (uiState.successMessage != null || uiState.errorMessage != null) {
            val isSuccess = uiState.successMessage != null
            val message = uiState.successMessage ?: uiState.errorMessage.orEmpty()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(shapes.buttonShape)
                        .background(if (isSuccess) colors.status.success.background else colors.status.danger.background)
                        .border(1.dp, if (isSuccess) colors.status.success.text else colors.status.danger.text, shapes.buttonShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSuccess) colors.status.success.text else colors.status.danger.text,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        style = A3Theme.typography.body,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSuccess) colors.status.success.text else colors.status.danger.text
                    )
                    IconButton(
                        onClick = viewModel::dismissMessage,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = if (isSuccess) colors.status.success.text else colors.status.danger.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog L08: Pengambilan Pakaian (F22, F24)
    if (uiState.isPickupDialogOpen && uiState.selectedOrder != null) {
        LaundryPickupDialog(
            orderWithLines = uiState.selectedOrder!!,
            uiState = uiState,
            onDismiss = viewModel::closePickupDialog,
            onSubmit = viewModel::submitPickup,
            onSelectAllFinal = viewModel::submitAllItemsFinalPickup
        )
    }

    // Dialog L09: Cuci Ulang Komplain (F23)
    if (uiState.isReworkDialogOpen && uiState.selectedOrder != null) {
        LaundryReworkDialog(
            selectedReason = uiState.reworkReason,
            onReasonChange = viewModel::setReworkReason,
            onDismiss = viewModel::closeReworkDialog,
            onRequestPin = viewModel::requestReworkWithPin
        )
    }

    // Dialog S14: Otorisasi PIN Manager (F23, F24, F35)
    if (uiState.isManagerPinDialogOpen) {
        ManagerPinAuthDialog(
            title = when (uiState.pendingAction) {
                is PendingManagerAction.UnpaidPickup -> "Otorisasi Penyerahan Belum Lunas"
                is PendingManagerAction.Rework -> "Otorisasi Cuci Ulang Komplain"
                is PendingManagerAction.Refund -> "Otorisasi Refund Tunai"
                null -> "Otorisasi Manager"
            },
            subtitle = when (val action = uiState.pendingAction) {
                is PendingManagerAction.UnpaidPickup -> "Pesanan memiliki sisa tagihan ${formatRupiah(uiState.remainingBalance)}. Wajib PIN Manager."
                is PendingManagerAction.Rework -> "Status cucian akan kembali ke Diproses tanpa tagihan baru."
                is PendingManagerAction.Refund -> "Pengembalian dana tunai ${formatRupiah(action.amount)} dari laci kas."
                null -> ""
            },
            errorMessage = uiState.pinErrorMessage,
            onDismiss = viewModel::closeManagerPinDialog,
            onSubmitPin = viewModel::verifyManagerPinAndExecute
        )
    }
}

@Composable
private fun FilterStatusTabs(
    activeFilter: LaundryFilterStatus,
    onFilterSelected: (LaundryFilterStatus) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shapes.buttonShape)
            .background(colors.surface)
            .border(1.dp, colors.border, shapes.buttonShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LaundryFilterStatus.entries.forEach { filter ->
            val isActive = activeFilter == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shapes.buttonShape)
                    .background(if (isActive) colors.accent else Color.Transparent)
                    .clickable { onFilterSelected(filter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.displayName,
                    style = A3Theme.typography.button,
                    color = if (isActive) colors.onAccent else colors.inkMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LaundryOrderCardItem(
    orderWithLines: OrderWithLines,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val order = orderWithLines.order

    val (statusLabel, statusToken) = when (order.status.lowercase()) {
        "open", "received" -> "Diterima" to StatusToken.INFO
        "processing" -> "Diproses" to StatusToken.WARNING
        "ready" -> "Siap" to StatusToken.SUCCESS
        "picked_up" -> "Diambil" to StatusToken.NEUTRAL
        else -> order.status.uppercase() to StatusToken.INFO
    }

    val isPaid = order.paidAmount >= order.total
    val isDp = order.paidAmount > 0 && !isPaid

    val (paymentLabel, paymentToken) = when {
        isPaid -> "Lunas" to StatusToken.SUCCESS
        isDp -> "DP" to StatusToken.WARNING
        else -> "Belum Lunas" to StatusToken.DANGER
    }

    val dateFormat = remember { SimpleDateFormat("d MMM · HH.mm", Locale.forLanguageTag("id-ID")) }
    val timeStr = remember(order.createdAt) { dateFormat.format(Date(order.createdAt)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(if (isSelected) colors.surfaceAlt else colors.surface)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accent else colors.border,
                shape = shapes.cardShape
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.displayNumber,
                    style = A3Theme.typography.title,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Text(
                    text = timeStr,
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }

            // Baris Layanan Singkat
            Text(
                text = orderWithLines.lines.joinToString(", ") { "${it.quantity}x ${it.nameSnapshot}" },
                style = A3Theme.typography.bodySmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Dua Status Chips: Cucian & Pembayaran
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                A3StatusChip(label = statusLabel, token = statusToken)
                A3StatusChip(label = paymentLabel, token = paymentToken)
            }
        }
    }
}

@Composable
private fun OrderDetailPanel(
    orderWithLines: OrderWithLines,
    uiState: LaundryOrdersUiState,
    viewModel: LaundryOrdersViewModel,
    hasActiveShift: Boolean,
    onRequireShift: () -> Unit,
    onProceedToSettlement: (orderId: String, remainingAmount: Long, displayNumber: String) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val order = orderWithLines.order
    val customer = uiState.customer
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val (statusLabel, statusToken) = when (order.status.lowercase()) {
        "open", "received" -> "Diterima" to StatusToken.INFO
        "processing" -> "Diproses" to StatusToken.WARNING
        "ready" -> "Siap Diambil" to StatusToken.SUCCESS
        "picked_up" -> "Sudah Diambil" to StatusToken.NEUTRAL
        "refunded" -> "Di-Refund" to StatusToken.DANGER
        else -> order.status.uppercase() to StatusToken.INFO
    }

    val isPaid = uiState.isFullyPaid
    val remaining = uiState.remainingBalance

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Order
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.displayNumber,
                        style = A3Theme.typography.headline,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = "ID: ${order.id}",
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    A3StatusChip(label = statusLabel, token = statusToken)
                    IconButton(
                        onClick = viewModel::printLaundryTag,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Tag",
                            tint = colors.accent
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.border, thickness = 1.dp)

            // 2. Data Pelanggan
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                        color = colors.accent,
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = customer?.name ?: "Pelanggan Athia",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                        customer?.phone?.let { phone ->
                            Text(
                                text = PhoneNumberUtil.formatDisplay(phone),
                                style = A3Theme.typography.body,
                                color = colors.inkMuted
                            )
                        }
                    }
                }

            // 2b. Status Notifikasi WhatsApp & Tombol Kirim Manual (F42 & DESIGN-laundry.md)
            WhatsAppNotificationCard(
                uiState = uiState,
                customer = customer,
                onRefreshNotification = viewModel::refreshNotificationStatus,
                onSendWhatsAppManual = {
                    val waUrl = viewModel.buildWhatsAppUrl(orderWithLines, customer)
                    val waMsg = viewModel.buildWhatsAppMessage(orderWithLines, customer)
                    if (waUrl != null) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            clipboardManager.setText(AnnotatedString(waMsg))
                            Toast.makeText(context, "Pesan WA disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // 3. Ringkasan Keuangan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shapes.cardShape)
                    .background(colors.canvas)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Total Biaya", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(text = formatRupiah(order.total), style = A3Theme.typography.title, fontWeight = FontWeight.Bold, color = colors.ink)
                    }
                    Column {
                        Text(text = "Telah Dibayar", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(text = formatRupiah(order.paidAmount), style = A3Theme.typography.title, fontWeight = FontWeight.Bold, color = colors.status.success.text)
                    }
                    Column {
                        Text(text = "Sisa Tagihan", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                        Text(
                            text = formatRupiah(remaining),
                            style = A3Theme.typography.title,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining > 0) colors.status.danger.text else colors.inkMuted
                        )
                    }
                }
            }

            // 4. Garis Waktu Status (Status Timeline)
            StatusTimelineRow(currentStatus = order.status.lowercase())

            // 5. Rincian Pakaian & Pengambilan Per Baris
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Rincian Pakaian & Pengambilan:",
                    style = A3Theme.typography.label,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                orderWithLines.lines.forEach { line ->
                    val linePickups = uiState.pickups.filter { it.orderLineId == line.id }
                    val isLinePickedUp = linePickups.any { it.isFinal }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.cardShape)
                            .background(colors.canvas)
                            .border(1.dp, colors.border, shapes.cardShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.nameSnapshot,
                                    style = A3Theme.typography.body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.ink
                                )
                                Text(
                                    text = "${line.quantity} item · ${formatRupiah(line.unitPriceSnapshot)}",
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                                if (!line.note.isNullOrBlank()) {
                                    Text(
                                        text = "Catatan: ${line.note}",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.status.warning.text,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            A3StatusChip(
                                label = if (isLinePickedUp) "Sudah Diambil" else "Belum Diambil",
                                token = if (isLinePickedUp) StatusToken.SUCCESS else StatusToken.WARNING
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Tombol Aksi Operasional
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HorizontalDivider(color = colors.border, thickness = 1.dp)

            // Tombol Pelunasan Pembayaran (jika ada sisa)
            if (remaining > 0) {
                A3Button(
                    label = "Terima Pelunasan Sisa ${formatRupiah(remaining)}",
                    variant = ButtonVariant.SECONDARY,
                    onClick = {
                        if (!hasActiveShift) {
                            onRequireShift()
                        } else {
                            onProceedToSettlement(order.id, remaining, order.displayNumber)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Aksi Status Diterima -> Mulai Cuci (Diproses)
                if (order.status.lowercase() == "open" || order.status.lowercase() == "received") {
                    A3Button(
                        label = "Mulai Cuci (Tandai Diproses)",
                        variant = ButtonVariant.PRIMARY,
                        onClick = viewModel::advanceStatusToProcessing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Aksi Status Diproses -> Selesai Cuci (Siap)
                if (order.status.lowercase() == "processing") {
                    A3Button(
                        label = "Selesai Cuci (Tandai Siap Diambil)",
                        variant = ButtonVariant.PRIMARY,
                        onClick = viewModel::advanceStatusToReady,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Tombol Cuci Ulang (Hanya aktif jika status Siap)
                if (order.status.lowercase() == "ready") {
                    A3Button(
                        label = "Cuci Ulang",
                        variant = ButtonVariant.DANGER,
                        onClick = viewModel::openReworkDialog,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tombol Ambil Cucian
                if (order.status.lowercase() == "ready") {
                    A3Button(
                        label = if (isPaid) "Ambil Cucian" else "Ambil (Butuh PIN Manager)",
                        variant = ButtonVariant.PRIMARY,
                        onClick = viewModel::openPickupDialog,
                        modifier = Modifier.weight(2f)
                    )
                } else if (order.status.lowercase() == "picked_up") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(shapes.buttonShape)
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Semua Pakaian Telah Diambil Pelanggan",
                            style = A3Theme.typography.button,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tombol Refund Tunai (F35: Otorisasi PIN Manager & Potong Kas Laci Shift Aktif)
            if (order.paidAmount > 0L && order.status.lowercase() != "refunded") {
                A3Button(
                    label = "Refund Tunai ${formatRupiah(order.paidAmount)} (PIN Manager)",
                    variant = ButtonVariant.GHOST,
                    onClick = {
                        if (!hasActiveShift) {
                            onRequireShift()
                        } else {
                            viewModel.openRefundDialog()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusTimelineRow(currentStatus: String) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val steps = listOf("Diterima", "Diproses", "Siap", "Diambil")
    val currentIndex = when (currentStatus) {
        "open", "received" -> 0
        "processing" -> 1
        "ready" -> 2
        "picked_up" -> 3
        else -> 0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.canvas)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, stepName ->
            val isDone = index <= currentIndex
            val isCurrent = index == currentIndex

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> colors.accent
                                isDone -> colors.status.success.background
                                else -> colors.border
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone && !isCurrent) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.status.success.text,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = A3Theme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) colors.onAccent else colors.inkMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = stepName,
                    style = A3Theme.typography.bodySmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) colors.ink else colors.inkMuted
                )
            }
        }
    }
}

@Composable
private fun LaundryPickupDialog(
    orderWithLines: OrderWithLines,
    uiState: LaundryOrdersUiState,
    onDismiss: () -> Unit,
    onSubmit: (List<LaundryPickupItem>) -> Unit,
    onSelectAllFinal: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val order = orderWithLines.order
    val isUnpaid = !uiState.isFullyPaid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(520.dp)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Penyerahan Pakaian",
                            style = A3Theme.typography.headline,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Order ${order.displayNumber} · Pengambilan pakaian pelanggan",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = colors.inkMuted)
                    }
                }

                // Peringatan Belum Lunas (F24 / LDR-10)
                if (isUnpaid) {
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = colors.status.danger.text)
                            Column {
                                Text(
                                    text = "Belum Lunas: Butuh Otorisasi PIN Manager",
                                    style = A3Theme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.status.danger.text
                                )
                                Text(
                                    text = "Sisa tagihan ${formatRupiah(uiState.remainingBalance)}. Penyerahan akan dicatat di laporan pengecualian.",
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.status.danger.text,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Rincian Pakaian yang Diambil
                Text(
                    text = "Daftar Layanan Pakaian:",
                    style = A3Theme.typography.label,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                if (orderWithLines.lines.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.cardShape)
                            .background(colors.canvas)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Memeriksa rincian pakaian... Tekan Ambil Semua untuk pemulihan otomatis.",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(orderWithLines.lines) { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.cardShape)
                                    .background(colors.canvas)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${line.nameSnapshot} (${line.quantity} item)",
                                    style = A3Theme.typography.body,
                                    color = colors.ink
                                )
                                A3StatusChip(label = "Diambil Penuh", token = StatusToken.INFO)
                            }
                        }
                    }
                }

                // Tombol Aksi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    A3Button(
                        label = "Batal",
                        variant = ButtonVariant.GHOST,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    A3Button(
                        label = if (isUnpaid) "Ambil & Minta PIN Manager" else "Ambil Semua & Selesai",
                        variant = ButtonVariant.PRIMARY,
                        onClick = onSelectAllFinal,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LaundryReworkDialog(
    selectedReason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRequestPin: (String) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val reasons = listOf(
        "Noda belum hilang",
        "Masih apek / kurang wangi",
        "Kusut / setrika ulang",
        "Pakaian rusak / komplain lain"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cuci Ulang Komplain",
                        style = A3Theme.typography.headline,
                        color = colors.ink,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = colors.inkMuted)
                    }
                }

                Text(
                    text = "Cucian akan dikembalikan ke status Diproses tanpa biaya tambahan. Wajib memilih alasan dan otorisasi PIN Manager.",
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted
                )

                // Pilihan Alasan
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.cardShape)
                                .background(if (selectedReason == reason) colors.surfaceAlt else colors.canvas)
                                .clickable { onReasonChange(reason) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { onReasonChange(reason) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                            )
                            Text(text = reason, style = A3Theme.typography.body, color = colors.ink)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    A3Button(label = "Batal", variant = ButtonVariant.GHOST, onClick = onDismiss, modifier = Modifier.weight(1f))
                    A3Button(
                        label = "Lanjut Otorisasi PIN",
                        variant = ButtonVariant.DANGER,
                        onClick = { onRequestPin(selectedReason) },
                        modifier = Modifier.weight(1.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagerPinAuthDialog(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    var pinInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.5.dp, colors.accent, shapes.cardShape)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = A3Theme.typography.title, fontWeight = FontWeight.Bold, color = colors.ink)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = colors.inkMuted)
                    }
                }

                Text(
                    text = subtitle,
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )

                // Indikator 4 Digit PIN (● ● ● ●)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(4) { idx ->
                        val isFilled = idx < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) colors.accent else colors.canvas)
                                .border(1.5.dp, if (isFilled) colors.accent else colors.border, CircleShape)
                        )
                    }
                }

                // Pesan Error
                errorMessage?.let {
                    Text(
                        text = it,
                        style = A3Theme.typography.bodySmall,
                        color = colors.status.danger.text,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                // Keypad Numerik 3x4 Kompak
                A3Keypad(
                    onDigitClick = { digit ->
                        if (pinInput.length < 4) {
                            val newPin = pinInput + digit
                            pinInput = newPin
                            if (newPin.length == 4) {
                                onSubmitPin(newPin)
                                pinInput = ""
                            }
                        }
                    },
                    onBackspace = {
                        if (pinInput.isNotEmpty()) {
                            pinInput = pinInput.dropLast(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                )

                A3Button(
                    label = "Batal",
                    variant = ButtonVariant.GHOST,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WhatsAppNotificationCard(
    uiState: LaundryOrdersUiState,
    customer: id.a3.core.data.local.entity.CustomerEntity?,
    onRefreshNotification: () -> Unit,
    onSendWhatsAppManual: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val hasPhone = !customer?.phone.isNullOrBlank()

    val (chipLabel, chipToken) = when (uiState.notificationStatus.lowercase()) {
        "sent" -> "WA Terkirim" to StatusToken.SUCCESS
        "queued" -> "WA Antrean" to StatusToken.WARNING
        "failed" -> "WA Gagal" to StatusToken.DANGER
        else -> "Belum Dikirim" to StatusToken.NEUTRAL
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.surfaceAlt)
            .border(0.5.dp, colors.border, shapes.cardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                A3StatusChip(
                    label = chipLabel,
                    token = chipToken
                )

                if (uiState.isCheckingNotification) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent
                    )
                } else {
                    IconButton(
                        onClick = onRefreshNotification,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Cek Status WA",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (uiState.notificationStatus.lowercase() == "failed" && !uiState.notificationError.isNullOrBlank()) {
                    Text(
                        text = uiState.notificationError,
                        style = A3Theme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.status.danger.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasPhone) {
                // Tombol "Kirim via WA" Manual sesuai DESIGN-laundry.md:
                // Warna Emerald WhatsApp #25D366, teks wajib colors.ink (kontras 7.38:1, dilarang teks putih)
                Button(
                    onClick = onSendWhatsAppManual,
                    modifier = Modifier.height(38.dp),
                    shape = shapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = colors.ink
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.ink
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kirim via WA",
                        style = A3Theme.typography.button,
                        color = colors.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
