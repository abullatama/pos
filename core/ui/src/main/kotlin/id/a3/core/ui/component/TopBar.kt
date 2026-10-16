package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

@Composable
fun TopBar(
    businessName: String,
    monogramText: String = businessName.take(2).uppercase(),
    cashierName: String? = null,
    shiftInfo: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    pendingSyncCount: Int = 0,
    isPrinterConnected: Boolean = false,
    onAdminClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = colors.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sisi Kiri: Monogram badge + Nama Usaha
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monogramText,
                        color = colors.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = A3Theme.typography.label.fontSize
                    )
                }

                Text(
                    text = businessName,
                    color = colors.ink,
                    style = A3Theme.typography.title
                )
            }

            // Sisi Kanan: Status Kasir & Shift | Sinkron | Printer | Admin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Kasir & Shift
                val staffText = when {
                    cashierName != null && shiftInfo != null -> "$cashierName · $shiftInfo"
                    cashierName != null -> cashierName
                    else -> "Shift Belum Dibuka"
                }
                Text(
                    text = staffText,
                    color = colors.inkMuted,
                    style = A3Theme.typography.bodySmall
                )

                // Indikator Sinkronisasi (3 Status: Hijau, Kuning, Merah)
                SyncStatusBadge(
                    status = syncStatus,
                    pendingCount = pendingSyncCount
                )

                // Indikator Printer
                PrinterStatusBadge(isConnected = isPrinterConnected)

                // Tombol Menu Admin (PIN Owner)
                if (onAdminClick != null) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .clickable(onClick = onAdminClick)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Admin",
                                color = colors.ink,
                                style = A3Theme.typography.label
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusBadge(
    status: SyncStatus,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val statusColors = A3Theme.colors.status
    val (bgColor, textColor, dotColor, label) = when (status) {
        SyncStatus.SYNCED -> Quad(statusColors.success.background, statusColors.success.text, statusColors.success.text, "Tersinkron")
        SyncStatus.PENDING -> Quad(
            statusColors.warning.background,
            statusColors.warning.text,
            statusColors.warning.text,
            if (pendingCount > 0) "$pendingCount Menunggu" else "Menunggu"
        )
        SyncStatus.ERROR -> Quad(statusColors.danger.background, statusColors.danger.text, statusColors.danger.text, "Bermasalah")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = textColor,
                style = A3Theme.typography.label
            )
        }
    }
}

@Composable
fun PrinterStatusBadge(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColors = A3Theme.colors.status
    val (bgColor, textColor, label) = if (isConnected) {
        Triple(statusColors.success.background, statusColors.success.text, "Printer Siap")
    } else {
        Triple(statusColors.warning.background, statusColors.warning.text, "Printer Putus")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Print,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = textColor,
                style = A3Theme.typography.label
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
