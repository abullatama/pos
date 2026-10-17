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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableBar
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
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.component.CafeMoveTableDialog

const val CAFE_OPEN_ORDERS_SCREEN_TAG = "cafe_open_orders_screen"
const val CAFE_NEW_ORDER_BTN_TAG = "cafe_new_order_btn"

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
    val openOrders by viewModel.openOrders.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var moveTableTargetOrder by remember { mutableStateOf<OrderWithLines?>(null) }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }

    // Jika order yang terpilih tidak ada lagi (misal sudah dibayar), reset pilihan
    val selectedOrder = openOrders.find { it.order.id == selectedOrderId } ?: openOrders.firstOrNull()

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
            // ==================== PANEL KIRI (38%): DAFTAR MEJA TERBUKA (C04) ====================
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Panel Kiri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pesanan Terbuka",
                            color = colors.ink,
                            style = A3Theme.typography.headline.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${openOrders.size} meja sedang aktif",
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall
                        )
                    }

                    // Tombol Pesanan Baru (Navigasi kembali ke POS C01)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(colors.accent)
                            .clickable(onClick = onNavigateToPos)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag(CAFE_NEW_ORDER_BTN_TAG)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Pesanan Baru",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Daftar Kartu Pesanan Terbuka
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
                            val isSelected = item.order.id == selectedOrder?.order?.id
                            OpenOrderCard(
                                orderWithLines = item,
                                isSelected = isSelected,
                                onClick = { selectedOrderId = item.order.id }
                            )
                        }
                    }
                }
            }

            // ==================== PANEL KANAN (62%): DETAIL PESANAN TERPILIH (C05) ====================
            Surface(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                if (selectedOrder == null) {
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
                        orderWithLines = selectedOrder,
                        onOpenMoveTable = { moveTableTargetOrder = selectedOrder },
                        onProceedToPayment = {
                            onProceedToPayment(selectedOrder.order.id, selectedOrder.order.total)
                        },
                        onReprintKitchenTicket = {
                            coroutineScope.launch {
                                val res = viewModel.reprintKitchenTicket(selectedOrder.order.id)
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
    val minutesAgo = ((System.currentTimeMillis() - order.createdAt) / 60000L).coerceAtLeast(1L)
    val isLongWait = minutesAgo > 45L

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
                        text = "$minutesAgo mnt",
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
                            text = "Pesanan #${order.displayNumber} • Status: Terbuka",
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
