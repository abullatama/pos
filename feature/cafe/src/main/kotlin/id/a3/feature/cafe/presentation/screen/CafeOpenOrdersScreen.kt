package id.a3.feature.cafe.presentation.screen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Dialog
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.ManagerPinApprovalDialog
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.component.CafeMoveTableDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val CAFE_OPEN_ORDERS_SCREEN_TAG = "cafe_open_orders_screen"
const val CAFE_NEW_ORDER_BTN_TAG = "cafe_new_order_btn"

enum class CafeOrdersTab(val title: String) {
    OPEN_TABLES("Meja Terbuka"),
    COMPLETED_HISTORY("Riwayat Selesai")
}

enum class CompletedShiftScope(val title: String) {
    CURRENT_SHIFT("Shift Berjalan"),
    PAST_SHIFTS("Shift Lalu (F36)")
}

/**
 * Format durasi pesanan terbuka ramah kasir (misal: 230 menit -> "3 jam 50 menit").
 */
fun formatOrderDuration(totalMinutes: Long): String {
    if (totalMinutes < 1L) return "Baru saja"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "$hours jam $minutes menit"
        hours > 0L -> "$hours jam"
        else -> "$minutes menit"
    }
}

/**
 * Layar C04 (Pesanan Terbuka) & C05 (Detail Pesanan Terbuka) — Tablet Landscape 11" A3 Coffeeshop.
 *
 * Mengakomodasi kebutuhan:
 * - C04: Memantau meja makan aktif (lama waktu, jumlah item, total).
 * - C05: Pindah meja, cetak ulang tiket dapur, dan lanjut ke pembayaran.
 */
@Composable
fun CafeOpenOrdersScreen(
    viewModel: CafeMenuViewModel,
    onNavigateToPos: () -> Unit,
    onProceedToPayment: (orderId: String, total: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    var activeTab by remember { mutableStateOf(CafeOrdersTab.OPEN_TABLES) }
    val openOrders by viewModel.openOrders.collectAsState()
    val completedOrders by viewModel.completedOrders.collectAsState()
    val activeShift by viewModel.activeShift.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var completedScope by remember { mutableStateOf(CompletedShiftScope.CURRENT_SHIFT) }

    val currentShiftCompletedOrders = remember(completedOrders, activeShift) {
        if (activeShift == null) emptyList()
        else completedOrders.filter { item ->
            (item.order.shiftId == activeShift!!.id) ||
                (item.order.createdAt >= activeShift!!.openedAt && (item.order.shiftId == null || item.order.shiftId == activeShift!!.id))
        }
    }

    val pastShiftCompletedOrders = remember(completedOrders, activeShift) {
        if (activeShift == null) completedOrders
        else completedOrders.filter { item ->
            val isCurrent = (item.order.shiftId == activeShift!!.id) ||
                (item.order.createdAt >= activeShift!!.openedAt && (item.order.shiftId == null || item.order.shiftId == activeShift!!.id))
            !isCurrent
        }
    }

    val displayedCompletedOrders = if (completedScope == CompletedShiftScope.CURRENT_SHIFT) {
        currentShiftCompletedOrders
    } else {
        pastShiftCompletedOrders
    }

    var selectedOpenOrderId by remember { mutableStateOf<String?>(null) }
    var selectedCompletedOrderId by remember { mutableStateOf<String?>(null) }
    var moveTableTargetOrder by remember { mutableStateOf<OrderWithLines?>(null) }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }

    var refundTargetOrder by remember { mutableStateOf<OrderWithLines?>(null) }
    var correctionType by remember { mutableStateOf("refund") }
    var managerPinErrorMessage by remember { mutableStateOf<String?>(null) }
    var isManagerLocked by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableStateOf(0) }
    var validationErrorDialogMessage by remember { mutableStateOf<String?>(null) }

    val selectedOpenOrder = openOrders.find { it.order.id == selectedOpenOrderId } ?: openOrders.firstOrNull()
    val selectedCompletedOrder = displayedCompletedOrders.find { it.order.id == selectedCompletedOrderId } ?: displayedCompletedOrders.firstOrNull()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(CAFE_OPEN_ORDERS_SCREEN_TAG),
        color = Color(0xFFF4F2E6)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== PANEL KIRI (38%): DAFTAR PESANAN & TAB ====================
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Baris 1: Judul Halaman & Tombol Aksi Utama "Pesanan Baru"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Daftar Pesanan",
                            color = colors.ink,
                            style = A3Theme.typography.headline.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (activeTab == CafeOrdersTab.OPEN_TABLES) {
                                "${openOrders.size} meja sedang aktif"
                            } else if (completedScope == CompletedShiftScope.CURRENT_SHIFT) {
                                "${currentShiftCompletedOrders.size} pesanan shift ini • ${formatRupiah(currentShiftCompletedOrders.sumOf { it.order.total })}"
                            } else {
                                "${pastShiftCompletedOrders.size} pesanan shift lalu • ${formatRupiah(pastShiftCompletedOrders.sumOf { it.order.total })}"
                            },
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Tombol Pesanan Baru (Tinggi standar sentuh tablet 44dp dengan touch target luas & anti-terpotong)
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = colors.accent,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .clickable(onClick = onNavigateToPos)
                            .testTag(CAFE_NEW_ORDER_BTN_TAG)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pesanan Baru",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Baris 2: Segmented Tabs Pill Penuh (Meja Terbuka vs Riwayat Selesai)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color(0xFFE5E7EB))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CafeOrdersTab.values().forEach { tab ->
                        val isSelected = activeTab == tab
                        val count = if (tab == CafeOrdersTab.OPEN_TABLES) openOrders.size else currentShiftCompletedOrders.size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9999.dp))
                                .background(if (isSelected) colors.accent else Color.Transparent)
                                .clickable {
                                    activeTab = tab
                                    printStatusMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${tab.title} ($count)",
                                color = if (isSelected) Color.White else colors.ink,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Konten Daftar: Pesanan Terbuka ATAU Riwayat Selesai
                if (activeTab == CafeOrdersTab.OPEN_TABLES) {
                    if (openOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceAlt),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = colors.inkMuted,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = "Tidak Ada Pesanan Terbuka",
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Semua meja kosong. Buat pesanan baru dari layar kasir.",
                                    color = colors.inkMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(openOrders, key = { it.order.id }) { item ->
                                val isSelected = item.order.id == selectedOpenOrder?.order?.id
                                OpenOrderCard(
                                    orderWithLines = item,
                                    isSelected = isSelected,
                                    onClick = { selectedOpenOrderId = item.order.id }
                                )
                            }
                        }
                    }
                } else {
                    // TAB: RIWAYAT SELESAI
                    // Sub-filter lingkup shift: Shift Berjalan (Default) vs Shift Lalu (F36)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE5E7EB))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompletedShiftScope.values().forEach { scope ->
                            val isScopeSelected = completedScope == scope
                            val count = if (scope == CompletedShiftScope.CURRENT_SHIFT) {
                                currentShiftCompletedOrders.size
                            } else {
                                pastShiftCompletedOrders.size
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isScopeSelected) Color.White else Color.Transparent)
                                    .clickable {
                                        completedScope = scope
                                        selectedCompletedOrderId = null
                                        printStatusMessage = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${scope.title} ($count)",
                                    color = if (isScopeSelected) colors.accent else colors.inkMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isScopeSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (displayedCompletedOrders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceAlt),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.status.success.text,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = if (completedScope == CompletedShiftScope.CURRENT_SHIFT) {
                                        "Belum Ada Transaksi Selesai"
                                    } else {
                                        "Tidak Ada Riwayat Shift Lalu"
                                    },
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (completedScope == CompletedShiftScope.CURRENT_SHIFT) {
                                        "Seluruh transaksi yang telah dilunasi selama shift ini akan tersimpan dan tampil di sini."
                                    } else {
                                        "Tidak ditemukan riwayat transaksi dari shift sebelumnya yang tersimpan di tablet ini."
                                    },
                                    color = colors.inkMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedCompletedOrders, key = { it.order.id }) { item ->
                                val isSelected = item.order.id == selectedCompletedOrder?.order?.id
                                val isOld = activeShift != null && (item.order.shiftId != null && item.order.shiftId != activeShift!!.id || item.order.createdAt < activeShift!!.openedAt)
                                CompletedOrderCard(
                                    orderWithLines = item,
                                    isSelected = isSelected,
                                    isOldTransaction = isOld,
                                    onClick = { selectedCompletedOrderId = item.order.id }
                                )
                            }
                        }
                    }
                }
            }

            // ==================== PANEL KANAN (62%): DETAIL PESANAN TERPILIH ====================
            Surface(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                if (activeTab == CafeOrdersTab.OPEN_TABLES) {
                    if (selectedOpenOrder == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pilih salah satu meja di panel kiri untuk melihat rincian pesanan.",
                                color = colors.inkMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        OpenOrderDetailView(
                            orderWithLines = selectedOpenOrder,
                            onOpenMoveTable = { moveTableTargetOrder = selectedOpenOrder },
                            onProceedToPayment = {
                                onProceedToPayment(selectedOpenOrder.order.id, selectedOpenOrder.order.total)
                            },
                            onReprintKitchenTicket = {
                                coroutineScope.launch {
                                    val res = viewModel.reprintKitchenTicket(selectedOpenOrder.order.id)
                                    printStatusMessage = if (res.isSuccess) {
                                        "Tiket dapur dikirim ke printer"
                                    } else {
                                        val err = res.exceptionOrNull()?.message ?: "Printer belum terhubung"
                                        "Gagal cetak: $err"
                                    }
                                }
                            },
                            printStatusMessage = printStatusMessage
                        )
                    }
                } else {
                    // TAB: RIWAYAT SELESAI DETAIL
                    if (selectedCompletedOrder == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pilih transaksi di panel kiri untuk melihat rincian pembayaran atau mencetak ulang struk.",
                                color = colors.inkMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val isOld = activeShift != null && (selectedCompletedOrder.order.shiftId != null && selectedCompletedOrder.order.shiftId != activeShift!!.id || selectedCompletedOrder.order.createdAt < activeShift!!.openedAt)
                        CompletedOrderDetailView(
                            orderWithLines = selectedCompletedOrder,
                            isOldTransaction = isOld,
                            onReprintReceipt = {
                                coroutineScope.launch {
                                    val res = viewModel.reprintCompletedOrderReceipt(selectedCompletedOrder)
                                    printStatusMessage = if (res.isSuccess) {
                                        "Struk salinan #${selectedCompletedOrder.order.displayNumber} berhasil dikirim ke printer"
                                    } else {
                                        val err = res.exceptionOrNull()?.message ?: "Printer belum terhubung"
                                        "Gagal cetak: $err"
                                    }
                                }
                            },
                            onRefundClick = {
                                if (isOld) {
                                    val ageMs = System.currentTimeMillis() - selectedCompletedOrder.order.createdAt
                                    val maxAgeMs = 14L * 24 * 3600 * 1000L
                                    if (selectedCompletedOrder.order.total > 100_000L) {
                                        validationErrorDialogMessage = "Refund transaksi shift lalu melebihi Rp 100.000 (total: ${formatRupiah(selectedCompletedOrder.order.total)}) tidak dapat dilakukan di kasir. Silakan hubungi Owner melalui Web Dashboard (F36, COR-05)."
                                        return@CompletedOrderDetailView
                                    }
                                    if (ageMs > maxAgeMs) {
                                        validationErrorDialogMessage = "Transaksi berumur lebih dari 14 hari tidak dapat di-refund di kasir. Silakan hubungi Owner melalui Web Dashboard (F36, COR-05)."
                                        return@CompletedOrderDetailView
                                    }
                                }
                                correctionType = "refund"
                                refundTargetOrder = selectedCompletedOrder
                                managerPinErrorMessage = null
                            },
                            onVoidClick = {
                                correctionType = "void"
                                refundTargetOrder = selectedCompletedOrder
                                managerPinErrorMessage = null
                            },
                            printStatusMessage = printStatusMessage
                        )
                    }
                }
            }
        }

        // Dialog Pindah Meja untuk Pesanan Terbuka Terpilih
        moveTableTargetOrder?.let { target ->
            CafeMoveTableDialog(
                currentTable = target.order.tableLabel ?: "Meja 01",
                onDismissRequest = { moveTableTargetOrder = null },
                onConfirmMoveTable = { newTable ->
                    viewModel.moveTable(newTable)
                    moveTableTargetOrder = null
                }
            )
        }

        // Dialog Penolakan Batas F36
        validationErrorDialogMessage?.let { errMsg ->
            A3Dialog(
                title = "Refund Ditolak di Kasir",
                bodyText = errMsg,
                confirmLabel = "Mengerti",
                onConfirm = { validationErrorDialogMessage = null },
                onDismissRequest = { validationErrorDialogMessage = null }
            )
        }

        // Dialog S14 Otorisasi PIN Manager (F35, F36)
        refundTargetOrder?.let { target ->
            val order = target.order
            val isOld = activeShift != null && (order.shiftId != null && order.shiftId != activeShift!!.id || order.createdAt < activeShift!!.openedAt)
            ManagerPinApprovalDialog(
                title = if (correctionType == "refund") "Otorisasi Refund Tunai" else "Otorisasi Void Pesanan",
                actionType = if (correctionType == "refund") "REFUND TUNAI" else "VOID PESANAN",
                amountToApprove = if (correctionType == "refund") order.paidAmount else order.total,
                isOldTransaction = isOld,
                errorMessage = managerPinErrorMessage,
                isManagerLocked = isManagerLocked,
                lockoutRemainingSeconds = lockoutSeconds,
                onDismiss = {
                    refundTargetOrder = null
                    managerPinErrorMessage = null
                },
                onSubmitApproval = { pin, reasonCode, note ->
                    coroutineScope.launch {
                        val isPinValid = viewModel.verifyManagerPin(pin)
                        if (!isPinValid) {
                            managerPinErrorMessage = "PIN Manager salah (Percobaan gagal dicatat)"
                            return@launch
                        }
                        val res = viewModel.refundOrVoidOrder(
                            orderWithLines = target,
                            type = correctionType,
                            reasonCode = reasonCode,
                            note = note,
                            approvedBy = "u-manager-1"
                        )
                        if (res.isSuccess) {
                            printStatusMessage = if (correctionType == "refund") {
                                "Refund tunai #${order.displayNumber} berhasil diproses & struk dicetak"
                            } else {
                                "Void #${order.displayNumber} berhasil dicatat"
                            }
                            refundTargetOrder = null
                            managerPinErrorMessage = null
                        } else {
                            managerPinErrorMessage = res.exceptionOrNull()?.message ?: "Gagal memproses koreksi"
                        }
                    }
                }
            )
        }
    }
}

/**
 * Kartu Ringkasan Meja Terbuka di Panel Kiri (C04)
 */
@Composable
private fun OpenOrderCard(
    orderWithLines: OrderWithLines,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val order = orderWithLines.order
    val tableLabel = order.tableLabel ?: "Meja"
    val itemCount = orderWithLines.lines.sumOf { it.quantity }

    // Hitung durasi sejak pesanan dibuat
    val totalMinutes = remember(order.createdAt) {
        ((System.currentTimeMillis() - order.createdAt) / 60000L).coerceAtLeast(0L)
    }
    val isLongWait = totalMinutes >= 45L
    val durationText = remember(totalMinutes) {
        formatOrderDuration(totalMinutes)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.surfaceAlt else Color.White)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) colors.accent else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Baris Atas: Meja & Status Chip
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TableBar,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else colors.ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = tableLabel,
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "#${order.displayNumber}",
                            color = colors.inkMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Chip Durasi Meja
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isLongWait) Color(0xFFFEE2E2) else colors.surfaceAlt)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = if (isLongWait) colors.status.danger.text else colors.inkMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = durationText,
                        color = if (isLongWait) colors.status.danger.text else colors.inkMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Baris Bawah: Total Rupiah & Jumlah Item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$itemCount item",
                    color = colors.inkMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatRupiah(order.total),
                    color = colors.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Rincian Detail Pesanan Terbuka Terpilih di Panel Kanan (C05)
 */
@Composable
private fun OpenOrderDetailView(
    orderWithLines: OrderWithLines,
    onOpenMoveTable: () -> Unit,
    onProceedToPayment: () -> Unit,
    onReprintKitchenTicket: () -> Unit,
    printStatusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val order = orderWithLines.order
    val lines = orderWithLines.lines
    val detailMinutes = remember(order.createdAt) {
        ((System.currentTimeMillis() - order.createdAt) / 60000L).coerceAtLeast(0L)
    }
    val detailDuration = remember(detailMinutes) {
        formatOrderDuration(detailMinutes)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Seksi Atas: Header Meja, Pelanggan, & Tombol Aksi
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TableBar,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = order.tableLabel ?: "Meja 01",
                            color = colors.ink,
                            style = A3Theme.typography.headline.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pesanan #${order.displayNumber} • Terbuka • $detailDuration",
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Cetak Ulang Tiket Dapur
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .clickable(onClick = onReprintKitchenTicket)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = "Cetak Tiket Dapur",
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Cetak Tiket Dapur",
                            color = colors.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tombol Pindah Meja Cepat
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .clickable(onClick = onOpenMoveTable)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TableBar,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Pindah Meja",
                            color = colors.ink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!printStatusMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = printStatusMessage,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFE5E7EB))
            )

            // Header Daftar Item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ITEM PESANAN DI DAPUR",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${lines.size} jenis menu",
                    color = colors.inkMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Seksi Tengah: Daftar Item Pesanan
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${line.quantity}x ${line.nameSnapshot}",
                                color = colors.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val lineNote = line.note
                            if (!lineNote.isNullOrBlank()) {
                                Text(
                                    text = lineNote,
                                    color = colors.inkMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = formatRupiah(line.unitPriceSnapshot * line.quantity),
                        color = colors.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Seksi Bawah: Rincian Keuangan & Tombol Bayar
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFE5E7EB))
            )

            // Rincian Subtotal & Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Tagihan Meja:",
                        color = colors.inkMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatRupiah(order.total),
                        color = colors.ink,
                        style = A3Theme.typography.headline.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Bayar
                A3Button(
                    label = "Bayar ${formatRupiah(order.total)}",
                    variant = ButtonVariant.PRIMARY,
                    onClick = onProceedToPayment,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

/**
 * Kartu Ringkasan Pesanan Selesai / Lunas di Panel Kiri
 */
@Composable
private fun CompletedOrderCard(
    orderWithLines: OrderWithLines,
    isSelected: Boolean,
    isOldTransaction: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val order = orderWithLines.order
    val tableOrType = order.tableLabel ?: if (order.orderType == "dine_in") "Dine In" else "Take Away"
    val itemCount = orderWithLines.lines.sumOf { it.quantity }
    val timeFormatted = remember(order.createdAt) {
        SimpleDateFormat("HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date(order.createdAt))
    }

    val (statusToken, statusLabel, statusIcon) = when (order.status.lowercase()) {
        "refunded" -> Triple(StatusToken.DANGER, "Refund", Icons.Outlined.Cancel)
        "void" -> Triple(StatusToken.NEUTRAL, "Void", Icons.Outlined.Cancel)
        "partial_refund" -> Triple(StatusToken.WARNING, "Refund Sebagian", Icons.Outlined.Warning)
        else -> Triple(StatusToken.SUCCESS, "Lunas", Icons.Outlined.CheckCircle)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.surfaceAlt else Color.White)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = if (isSelected) colors.accent else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (order.status.lowercase()) {
                                    "refunded" -> colors.status.danger.background
                                    "void" -> colors.surfaceAlt
                                    else -> if (isSelected) colors.accent else colors.surfaceAlt
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = when (order.status.lowercase()) {
                                "refunded" -> colors.status.danger.text
                                "void" -> colors.inkMuted
                                else -> if (isSelected) Color.White else colors.status.success.text
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "#${order.displayNumber}",
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$tableOrType • $timeFormatted",
                            color = colors.inkMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOldTransaction) {
                        A3StatusChip(
                            token = StatusToken.NEUTRAL,
                            label = "Shift Lalu",
                            icon = Icons.Outlined.History
                        )
                    }
                    A3StatusChip(
                        token = statusToken,
                        label = statusLabel,
                        icon = statusIcon
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$itemCount item",
                    color = colors.inkMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatRupiah(order.total),
                    color = colors.ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Rincian Detail Transaksi Selesai & Cetak Ulang Salinan / Refund di Panel Kanan
 */
@Composable
private fun CompletedOrderDetailView(
    orderWithLines: OrderWithLines,
    isOldTransaction: Boolean = false,
    onReprintReceipt: () -> Unit,
    onRefundClick: () -> Unit,
    onVoidClick: () -> Unit,
    printStatusMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val order = orderWithLines.order
    val lines = orderWithLines.lines
    val tableOrType = order.tableLabel ?: if (order.orderType == "dine_in") "Dine In" else "Take Away"
    val dateStr = remember(order.createdAt) {
        SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date(order.createdAt))
    }

    val (headerBg, headerTint, headerIcon) = when (order.status.lowercase()) {
        "refunded" -> Triple(colors.status.danger.background, colors.status.danger.text, Icons.Outlined.Cancel)
        "void" -> Triple(colors.surfaceAlt, colors.inkMuted, Icons.Outlined.Cancel)
        "partial_refund" -> Triple(colors.status.warning.background, colors.status.warning.text, Icons.Outlined.Warning)
        else -> Triple(colors.status.success.background, colors.status.success.text, Icons.Outlined.CheckCircle)
    }

    val isCancellable = order.status.lowercase() !in listOf("refunded", "void")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(headerBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = headerTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Struk #${order.displayNumber}",
                            color = colors.ink,
                            style = A3Theme.typography.headline.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$tableOrType • $dateStr",
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall
                        )
                    }
                }

                // Tombol Aksi Kanan Atas
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tombol Cetak Ulang (SALINAN)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .clickable(onClick = onReprintReceipt)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = "Cetak Salinan",
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cetak Salinan",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isCancellable) {
                        // Tombol Void
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceAlt)
                                .clickable(onClick = onVoidClick)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cancel,
                                contentDescription = "Void",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Void",
                                color = colors.inkMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Tombol Refund Tunai
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFEBEE))
                                .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
                                .clickable(onClick = onRefundClick)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoneyOff,
                                contentDescription = "Refund Tunai",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Refund Tunai",
                                color = Color(0xFFC62828),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (!printStatusMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = printStatusMessage,
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFE5E7EB))
            )

            // Header Daftar Item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RINCIAN ITEM PESANAN",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${lines.size} jenis menu",
                    color = colors.inkMuted,
                    fontSize = 11.sp
                )
            }

            // List item
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceAlt.copy(alpha = 0.5f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${line.quantity}x ${line.nameSnapshot}",
                                color = colors.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val lineNote = line.note
                            if (!lineNote.isNullOrBlank()) {
                                Text(
                                    text = lineNote,
                                    color = colors.inkMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = formatRupiah(line.unitPriceSnapshot * line.quantity),
                            color = colors.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Seksi Bawah: Rincian Keuangan
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFFE5E7EB))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Subtotal", color = colors.inkMuted, fontSize = 12.sp)
                Text(text = formatRupiah(order.subtotal), color = colors.ink, fontSize = 12.sp)
            }

            if (order.discount > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Diskon", color = colors.inkMuted, fontSize = 12.sp)
                    Text(text = "-${formatRupiah(order.discount)}", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (order.serviceCharge > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Biaya Layanan", color = colors.inkMuted, fontSize = 12.sp)
                    Text(text = formatRupiah(order.serviceCharge), color = colors.ink, fontSize = 12.sp)
                }
            }

            if (order.tax > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Pajak", color = colors.inkMuted, fontSize = 12.sp)
                    Text(text = formatRupiah(order.tax), color = colors.ink, fontSize = 12.sp)
                }
            }

            if (order.rounding != 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Pembulatan", color = colors.inkMuted, fontSize = 12.sp)
                    Text(text = formatRupiah(order.rounding), color = colors.ink, fontSize = 12.sp)
                }
            }

            val (bottomToken, bottomLabel, bottomIcon) = when (order.status.lowercase()) {
                "refunded" -> Triple(StatusToken.DANGER, "Telah Di-Refund (Tunai)", Icons.Outlined.Cancel)
                "void" -> Triple(StatusToken.NEUTRAL, "Pesanan Di-Void", Icons.Outlined.Cancel)
                "partial_refund" -> Triple(StatusToken.WARNING, "Refund Sebagian", Icons.Outlined.Warning)
                else -> Triple(StatusToken.SUCCESS, "Lunas", Icons.Outlined.CheckCircle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (order.status.lowercase() == "refunded") "Total Refund Tunai:" else "Total Dibayar:",
                        color = colors.inkMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatRupiah(order.total),
                        color = if (order.status.lowercase() == "refunded") Color(0xFFC62828) else colors.ink,
                        style = A3Theme.typography.headline.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                A3StatusChip(
                    token = bottomToken,
                    label = bottomLabel,
                    icon = bottomIcon
                )
            }
        }
    }
}
