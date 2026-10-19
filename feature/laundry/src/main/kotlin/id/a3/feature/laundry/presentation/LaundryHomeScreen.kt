package id.a3.feature.laundry.presentation

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3EmptyState
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme

/**
 * Layar L01: Beranda Laundry (Athia Laundry House).
 * Menampilkan ringkasan metriks operasional hari ini, tombol pintasan aksi cepat,
 * serta daftar cucian berstatus Siap Diambil untuk penyerahan cepat kepada pelanggan.
 */
@Composable
fun LaundryHomeScreen(
    viewModel: LaundryHomeViewModel,
    onNavigateToOrder: () -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToCucian: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Layar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Beranda Laundry",
                    style = A3Theme.typography.title,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ringkasan operasional dan antrean cucian hari ini",
                    style = A3Theme.typography.body,
                    color = colors.inkMuted
                )
            }

            // 1. Tiga Kartu Ringkasan Metriks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryMetricCard(
                    title = "Order Hari Ini",
                    count = uiState.todayOrdersCount,
                    icon = Icons.Outlined.AddCircle,
                    accentColor = colors.accent,
                    modifier = Modifier.weight(1f)
                )

                SummaryMetricCard(
                    title = "Siap Diambil",
                    count = uiState.readyOrdersCount,
                    icon = Icons.Outlined.CheckCircle,
                    accentColor = colors.status.success.text,
                    modifier = Modifier.weight(1f)
                )

                SummaryMetricCard(
                    title = "Belum Lunas",
                    count = uiState.unpaidOrdersCount,
                    icon = Icons.Outlined.Warning,
                    accentColor = colors.status.warning.text,
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. Dua Tombol Pintasan Aksi Cepat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                A3Button(
                    label = "Order Baru",
                    variant = ButtonVariant.PRIMARY,
                    onClick = onNavigateToOrder,
                    modifier = Modifier.weight(1f)
                )

                A3Button(
                    label = "Scan Tag Cucian",
                    variant = ButtonVariant.SECONDARY,
                    onClick = onNavigateToScan,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Daftar Cucian Siap Diambil
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Text(
                            text = "Siap Diambil",
                            style = A3Theme.typography.title,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                        A3StatusChip(
                            label = "${uiState.readyOrders.size} cucian",
                            token = StatusToken.SUCCESS
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(shapes.buttonShape)
                            .clickable(onClick = onNavigateToCucian)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Buka Daftar Cucian",
                            style = A3Theme.typography.button,
                            color = colors.accent,
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (uiState.readyOrders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        A3EmptyState(
                            title = "Belum Ada Cucian Siap Diambil",
                            description = "Cucian yang telah selesai dicuci dan siap diserahkan akan tampil di sini.",
                            action = {
                                A3Button(
                                    label = "Lihat Semua Cucian",
                                    variant = ButtonVariant.SECONDARY,
                                    onClick = onNavigateToCucian
                                )
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.readyOrders, key = { it.orderWithLines.order.id }) { item ->
                            ReadyOrderRow(
                                item = item,
                                onClick = onNavigateToCucian
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Box(
        modifier = modifier
            .clip(shapes.cardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, shapes.cardShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = A3Theme.typography.label,
                    color = colors.inkMuted
                )
                Text(
                    text = count.toString(),
                    style = A3Theme.typography.headline,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadyOrderRow(
    item: ReadyOrderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val order = item.orderWithLines.order
    val isFullyPaid = item.remainingBalance == 0L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.buttonShape)
            .background(colors.canvas)
            .border(0.5.dp, colors.border, shapes.buttonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = order.displayNumber,
                    style = A3Theme.typography.body,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )

                Text(
                    text = item.customer?.name ?: "Pelanggan Athia",
                    style = A3Theme.typography.body,
                    color = colors.ink
                )

                A3StatusChip(
                    label = "Siap",
                    token = StatusToken.SUCCESS
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isFullyPaid) {
                    A3StatusChip(
                        label = "Lunas",
                        token = StatusToken.SUCCESS
                    )
                } else {
                    A3StatusChip(
                        label = "Sisa ${formatRupiah(item.remainingBalance)}",
                        token = StatusToken.WARNING
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
