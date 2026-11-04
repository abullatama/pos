package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.a3.core.ui.theme.A3Theme

@Composable
fun SyncStatusDialog(
    isOpen: Boolean,
    syncStatus: SyncStatus,
    pendingCount: Int = 0,
    failedCount: Int = 0,
    lastError: String? = null,
    isSyncing: Boolean = false,
    onDismiss: () -> Unit,
    onRetryClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onClearUnauthorizedShiftClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val statusColors = colors.status

    val isUnauthorizedShift = lastError?.contains("tidak berizin", ignoreCase = true) == true
        || lastError?.contains("UNAUTHORIZED_SHIFT", ignoreCase = true) == true

    val (headerIcon, iconColor, statusTitle) = when (syncStatus) {
        SyncStatus.SYNCED -> Triple(Icons.Outlined.CloudDone, statusColors.success.text, "Semua Data Tersinkron")
        SyncStatus.PENDING -> Triple(Icons.Outlined.CloudQueue, statusColors.warning.text, "$pendingCount Data Menunggu")
        SyncStatus.ERROR -> Triple(Icons.Outlined.CloudOff, statusColors.danger.text, "${if (failedCount > 0) failedCount else ""} Data Bermasalah".trim())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .widthIn(min = 480.dp, max = 540.dp)
                .fillMaxWidth(0.9f)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(iconColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Status Sinkronisasi",
                                style = A3Theme.typography.title,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = statusTitle,
                                style = A3Theme.typography.bodySmall,
                                color = iconColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = colors.inkMuted
                        )
                    }
                }

                // Penjelasan Status
                when (syncStatus) {
                    SyncStatus.SYNCED -> {
                        Text(
                            text = "Semua catatan transaksi, shift, dan event lokal pada tablet ini telah berhasil tersimpan aman di server cloud.",
                            style = A3Theme.typography.body,
                            color = colors.inkMuted
                        )
                    }
                    SyncStatus.PENDING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Terdapat $pendingCount transaksi/perubahan data yang sedang menunggu pengiriman ke server. Sistem akan mengirimkannya secara berkala di latar belakang.",
                                style = A3Theme.typography.body,
                                color = colors.inkMuted
                            )

                            if (!lastError.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusColors.warning.background)
                                        .border(1.dp, statusColors.warning.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Keterangan Terakhir:",
                                            style = A3Theme.typography.label,
                                            color = statusColors.warning.text,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = lastError,
                                            style = A3Theme.typography.bodySmall,
                                            color = colors.ink
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SyncStatus.ERROR -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Pengiriman transaksi ke server ditolak atau mengalami kendala. Data tetap aman di penyimpanan lokal tablet dan tidak terhapus.",
                                style = A3Theme.typography.body,
                                color = colors.ink
                            )

                            if (!lastError.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusColors.danger.background)
                                        .border(1.dp, statusColors.danger.text.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Pesan Kesalahan Server:",
                                            style = A3Theme.typography.label,
                                            color = statusColors.danger.text,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = lastError,
                                            style = A3Theme.typography.bodySmall,
                                            color = colors.ink
                                        )
                                        if (isUnauthorizedShift) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Catatan: Akun staf laundry tidak bertugas mengelola kas/shift. Operasional cucian tetap dapat berjalan. Anda dapat membersihkan data shift yang tertolak ini.",
                                                style = A3Theme.typography.label,
                                                color = colors.inkMuted
                                            )
                                            if (onClearUnauthorizedShiftClick != null) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = onClearUnauthorizedShiftClick,
                                                    colors = ButtonDefaults.buttonColors(containerColor = statusColors.danger.text),
                                                    shape = shapes.buttonShape,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Bersihkan Data Shift Ditolak",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tombol Aksi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = shapes.buttonShape
                    ) {
                        Text("Tutup", color = colors.ink)
                    }

                    if (syncStatus == SyncStatus.ERROR) {
                        Button(
                            onClick = onRetryClick,
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = shapes.buttonShape
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mengirim Ulang...", color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Coba Kirim Ulang Semua", color = Color.White)
                            }
                        }
                    } else if (syncStatus == SyncStatus.PENDING) {
                        Button(
                            onClick = onSyncNowClick,
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = shapes.buttonShape
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menyinkronkan...", color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sinkronkan Sekarang", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
