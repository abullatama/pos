package id.a3.core.ui.component

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.R
import id.a3.core.ui.theme.A3Theme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * TopBar Organik & Elegan (Menyatu Alami dengan Kanvas Krem #F4F2E6):
 * - Sisi Kiri: Logo resmi A3 transparan (tanpa kontainer kotak putih) + Tanggal dinamis hari ini.
 *   Tanpa teks nama usaha berulang dan tanpa garis pemisah/divider (sesuai contoh).
 * - Sisi Kanan: Kapsul-kapsul pil putih bersih untuk Profil Kasir, Status Sinkron, Status Printer, dan Admin.
 * - Latar Belakang: Transparan murni (menyatu dengan warna kanvas krem), bebas dari bayangan dan sekat kaku.
 */
@Composable
fun TopBar(
    businessName: String = "A3 Coffeeshop",
    monogramText: String = "A3",
    showMonogram: Boolean = true,
    cashierName: String? = null,
    shiftInfo: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    pendingSyncCount: Int = 0,
    failedSyncCount: Int = 0,
    isPrinterConnected: Boolean = false,
    isPrinterConnecting: Boolean = false,
    pendingPrintCount: Int = 0,
    onPrinterClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    onAdminClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    // Format tanggal dinamis: "Thursday, 17 Oct"
    val dateText = remember {
        try {
            val now = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID"))
            now.format(formatter)
        } catch (_: Exception) {
            "Kamis, 17 Oktober 2026"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // SISI KIRI: Logo Grafis A3 Transparan + Tanggal (Tanpa nama usaha, Tanpa divider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo grafis transparan murni (A3 Coffeeshop atau Athia Laundry House sesuai tema aktif)
                Image(
                    painter = painterResource(id = A3Theme.logoRes),
                    contentDescription = "Logo Usaha",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(38.dp)
                )

                // Tanggal hari ini bertekstur abu netral elegan
                Text(
                    text = dateText,
                    color = colors.inkMuted,
                    style = A3Theme.typography.body.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                )
            }

            // SISI KANAN: Kapsul-Kapsul Pil Putih Kontrol Operasional Kasir
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Kapsul Profil Kasir
                val staffName = cashierName ?: "Kasir"

                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = colors.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .border(0.5.dp, colors.border, RoundedCornerShape(9999.dp))
                        .then(if (onProfileClick != null) Modifier.clickable { onProfileClick() } else Modifier)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = staffName,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        if (!shiftInfo.isNullOrBlank()) {
                            Text(
                                text = shiftInfo,
                                color = colors.inkMuted,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. Kapsul Status Sinkronisasi
                SyncStatusBadge(
                    status = syncStatus,
                    pendingCount = pendingSyncCount,
                    failedCount = failedSyncCount,
                    onClick = onSyncClick
                )

                // 3. Kapsul Status Printer Termal (Interaktif: dapat diketuk untuk cek ulang koneksi)
                PrinterStatusBadge(
                    isConnected = isPrinterConnected,
                    isConnecting = isPrinterConnecting,
                    pendingCount = pendingPrintCount,
                    onClick = onPrinterClick
                )

                // 4. Kapsul Tombol Admin
                if (onAdminClick != null) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = colors.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .border(0.5.dp, colors.border, RoundedCornerShape(9999.dp))
                            .clickable(onClick = onAdminClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Admin",
                                color = colors.ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
    failedCount: Int = 0,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val statusColors = colors.status
    val (dotColor, label) = when (status) {
        SyncStatus.SYNCED -> Pair(statusColors.success.text, "Tersinkron")
        SyncStatus.PENDING -> Pair(
            statusColors.warning.text,
            if (pendingCount > 0) "$pendingCount Menunggu" else "Menunggu"
        )
        SyncStatus.ERROR -> Pair(
            statusColors.danger.text,
            if (failedCount > 0) "$failedCount Bermasalah" else "Bermasalah"
        )
    }

    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = colors.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .border(0.5.dp, colors.border, RoundedCornerShape(9999.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                color = colors.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PrinterStatusBadge(
    isConnected: Boolean,
    isConnecting: Boolean = false,
    pendingCount: Int = 0,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val statusColors = colors.status
    val (iconColor, label) = when {
        isConnecting -> Pair(statusColors.info.text, "Memeriksa...")
        isConnected -> Pair(statusColors.success.text, "Printer Siap")
        pendingCount > 0 -> Pair(statusColors.warning.text, "Printer Putus ($pendingCount antre)")
        else -> Pair(statusColors.warning.text, "Printer Putus")
    }

    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = colors.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .border(0.5.dp, colors.border, RoundedCornerShape(9999.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = iconColor
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Print,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                color = colors.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
