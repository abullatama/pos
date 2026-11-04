package id.a3.core.ui.component

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.domain.device.DeviceStatus
import id.a3.core.ui.theme.A3Theme

const val DEVICE_REG_CARD_TAG = "device_reg_card"
const val DEVICE_REG_CODE_INPUT_TAG = "device_reg_code_input"
const val DEVICE_REG_NAME_INPUT_TAG = "device_reg_name_input"
const val DEVICE_REG_SUBMIT_BTN_TAG = "device_reg_submit_btn"
const val DEVICE_REG_CHECK_STATUS_BTN_TAG = "device_reg_check_status_btn"
const val DEVICE_REVOKED_BANNER_TAG = "device_revoked_banner"

/**
 * Layar S01: Daftarkan Tablet (Alur Penuh Fokus).
 *
 * Mengelola status:
 * - UNREGISTERED: Form nama tablet dan kode pendaftaran sekali pakai dari portal owner.
 * - PENDING: Status "Menunggu Persetujuan Owner" dengan indikator alur dan tombol periksa status.
 * - ACTIVE: Berhasil terhubung ke unit usaha.
 * - REVOKED (S17): Layar terkunci permanen "Akses Tablet Dicabut" dengan segel keamanan simetris.
 */
@Composable
fun DeviceRegistrationScreen(
    deviceStatus: DeviceStatus,
    deviceId: String,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    registrationCode: String,
    onRegistrationCodeChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onCheckStatusClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    businessUnitName: String? = null,
    onDebugBypassClick: (() -> Unit)? = null,
    onRegenerateDeviceId: (() -> Unit)? = null
) {
    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (deviceStatus) {
            DeviceStatus.REVOKED -> {
                // S17: Layar Terkunci Permanen
                DeviceRevokedCard(
                    deviceId = deviceId,
                    onDebugBypassClick = onDebugBypassClick,
                    modifier = Modifier.testTag(DEVICE_REVOKED_BANNER_TAG)
                )
            }
            DeviceStatus.PENDING -> {
                // Menunggu Persetujuan Owner di Web
                DevicePendingCard(
                    deviceName = deviceName,
                    deviceId = deviceId,
                    isLoading = isLoading,
                    onCheckStatusClick = onCheckStatusClick,
                    errorMessage = errorMessage,
                    onDebugBypassClick = onDebugBypassClick
                )
            }
            DeviceStatus.ACTIVE -> {
                // Perangkat Aktif
                DeviceActiveCard(
                    businessUnitName = businessUnitName ?: "Unit Usaha Aktif",
                    deviceName = deviceName,
                    deviceId = deviceId
                )
            }
            DeviceStatus.UNREGISTERED -> {
                // S01: Formulir Pendaftaran Perangkat
                DeviceRegistrationFormCard(
                    deviceName = deviceName,
                    onDeviceNameChange = onDeviceNameChange,
                    registrationCode = registrationCode,
                    onRegistrationCodeChange = onRegistrationCodeChange,
                    onRegisterClick = onRegisterClick,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    deviceId = deviceId,
                    onDebugBypassClick = onDebugBypassClick,
                    onRegenerateDeviceId = onRegenerateDeviceId
                )
            }
        }
    }
}

@Composable
private fun DeviceRegistrationFormCard(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    registrationCode: String,
    onRegistrationCodeChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    deviceId: String,
    onDebugBypassClick: (() -> Unit)? = null,
    onRegenerateDeviceId: (() -> Unit)? = null
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    A3Card(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag(DEVICE_REG_CARD_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emblem Perangkat Teriluminasi
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(shapes.cardShape)
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.accent.copy(alpha = 0.35f), shapes.cardShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.TabletAndroid,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "Daftarkan Tablet",
                color = colors.ink,
                style = A3Theme.typography.headline,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Masukkan kode pendaftaran sekali pakai yang digenerate oleh Owner di portal web.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            if (!errorMessage.isNullOrBlank()) {
                A3StatusChip(
                    label = errorMessage,
                    token = StatusToken.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            A3TextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                label = "Nama Tablet / Posisi",
                placeholder = "Misal: Tablet Kasir Depan",
                modifier = Modifier.testTag(DEVICE_REG_NAME_INPUT_TAG)
            )

            A3TextField(
                value = registrationCode,
                onValueChange = { onRegistrationCodeChange(it.uppercase()) },
                label = "Kode Pendaftaran Sekali Pakai",
                placeholder = "CONTOH: REG-8834",
                textStyle = A3Theme.typography.title.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.testTag(DEVICE_REG_CODE_INPUT_TAG)
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                A3Button(
                    label = "Daftarkan Perangkat",
                    onClick = onRegisterClick,
                    enabled = deviceName.isNotBlank() && registrationCode.isNotBlank(),
                    fillWidth = true,
                    modifier = Modifier.testTag(DEVICE_REG_SUBMIT_BTN_TAG)
                )
            }

            if (onDebugBypassClick != null) {
                A3Button(
                    label = "Masuk Mode Demo / Lewati",
                    variant = ButtonVariant.GHOST,
                    onClick = onDebugBypassClick,
                    fillWidth = true
                )
            }

            // Hardware Identity Badge (Device ID)
            DeviceTelemetryBadge(
                deviceId = deviceId,
                onRegenerateDeviceId = onRegenerateDeviceId
            )
        }
    }
}

@Composable
private fun DevicePendingCard(
    deviceName: String,
    deviceId: String,
    isLoading: Boolean,
    onCheckStatusClick: () -> Unit,
    errorMessage: String?,
    onDebugBypassClick: (() -> Unit)? = null
) {
    val colors = A3Theme.colors

    A3Card(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag(DEVICE_REG_CARD_TAG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lencana Status Konsentris Simetris
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.status.warning.background)
                    .border(1.5.dp, colors.status.warning.text.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HourglassTop,
                    contentDescription = null,
                    tint = colors.status.warning.text,
                    modifier = Modifier.size(36.dp)
                )
            }

            A3StatusChip(
                label = "Menunggu Persetujuan Owner",
                token = StatusToken.WARNING,
                icon = Icons.Outlined.HourglassTop
            )

            Text(
                text = "Pendaftaran Terkirim",
                color = colors.ink,
                style = A3Theme.typography.headline,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Tablet \"$deviceName\" telah terhubung ke sistem. Harap hubungi Owner untuk menyetujui tablet ini pada portal web.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            // Step Indicator Workflow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepItem(step = "1", label = "Kode Pendaftaran Terkirim", isDone = true)
                    StepItem(step = "2", label = "Menunggu Persetujuan di Web Portal", isCurrent = true)
                    StepItem(step = "3", label = "Tablet Aktif & Siap Transaksi Kasir")
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                A3StatusChip(
                    label = errorMessage,
                    token = StatusToken.DANGER,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                A3Button(
                    label = "Periksa Status Sekarang",
                    variant = ButtonVariant.SECONDARY,
                    onClick = onCheckStatusClick,
                    fillWidth = true,
                    modifier = Modifier.testTag(DEVICE_REG_CHECK_STATUS_BTN_TAG)
                )
            }

            if (onDebugBypassClick != null) {
                A3Button(
                    label = "Langsung Aktifkan (Mode Demo)",
                    variant = ButtonVariant.GHOST,
                    onClick = onDebugBypassClick,
                    fillWidth = true
                )
            }

            // Hardware Identity Badge (Device ID)
            DeviceTelemetryBadge(deviceId = deviceId)
        }
    }
}

@Composable
private fun StepItem(
    step: String,
    label: String,
    isDone: Boolean = false,
    isCurrent: Boolean = false
) {
    val colors = A3Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val (circleBg, circleTextColor) = when {
            isDone -> colors.status.success.background to colors.status.success.text
            isCurrent -> colors.status.warning.background to colors.status.warning.text
            else -> colors.surfaceAlt to colors.inkMuted
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = circleTextColor,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = step,
                    color = circleTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = label,
            color = if (isCurrent) colors.ink else colors.inkMuted,
            style = A3Theme.typography.bodySmall.copy(
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun DeviceActiveCard(
    businessUnitName: String,
    deviceName: String,
    deviceId: String
) {
    val colors = A3Theme.colors

    A3Card(modifier = Modifier.widthIn(max = 520.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.status.success.background)
                    .border(1.5.dp, colors.status.success.text.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = colors.status.success.text,
                    modifier = Modifier.size(36.dp)
                )
            }

            A3StatusChip(
                label = "Perangkat Aktif",
                token = StatusToken.SUCCESS,
                icon = Icons.Outlined.CheckCircle
            )

            Text(
                text = businessUnitName,
                color = colors.ink,
                style = A3Theme.typography.headline,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Tablet \"$deviceName\" siap digunakan untuk transaksi operasional.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            DeviceTelemetryBadge(deviceId = deviceId)
        }
    }
}

@Composable
private fun DeviceRevokedCard(
    deviceId: String,
    modifier: Modifier = Modifier,
    onDebugBypassClick: (() -> Unit)? = null
) {
    val colors = A3Theme.colors

    A3Card(
        modifier = modifier.widthIn(max = 520.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lencana Segel Keamanan Simetris 1:1 Konsentris
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.status.danger.background)
                    .border(2.dp, colors.status.danger.text.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.status.danger.text),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.surface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Text(
                text = "Akses Tablet Dicabut",
                color = colors.status.danger.text,
                style = A3Theme.typography.headline,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Akses perangkat ini telah dinonaktifkan oleh Administrator di portal web. Seluruh kredensial dan sesi lokal telah dihapus demi keamanan. Hubungi Owner untuk pendaftaran ulang.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            if (onDebugBypassClick != null) {
                A3Button(
                    label = "Reset Status Perangkat (Demo)",
                    variant = ButtonVariant.GHOST,
                    onClick = onDebugBypassClick,
                    fillWidth = true
                )
            }

            // Hardware Telemetry Badge (Device ID)
            DeviceTelemetryBadge(deviceId = deviceId)
        }
    }
}

@Composable
private fun DeviceTelemetryBadge(
    deviceId: String,
    onRegenerateDeviceId: (() -> Unit)? = null
) {
    val colors = A3Theme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .border(0.5.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Device ID: $deviceId",
                color = colors.inkMuted,
                style = A3Theme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum",
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            if (onRegenerateDeviceId != null) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Ganti Device ID",
                    tint = colors.accent,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRegenerateDeviceId)
                )
            }
        }
    }
}
