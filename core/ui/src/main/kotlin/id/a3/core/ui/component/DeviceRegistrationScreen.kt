package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
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
 * - PENDING: Status "Menunggu Persetujuan Owner" dengan tombol periksa status.
 * - ACTIVE: Berhasil terhubung ke unit usaha.
 * - REVOKED (S17): Layar terkunci permanen "Akses Tablet Dicabut".
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
    businessUnitName: String? = null
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
                    errorMessage = errorMessage
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
                    deviceId = deviceId
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
    deviceId: String
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
            Icon(
                imageVector = Icons.Outlined.TabletAndroid,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.height(44.dp)
            )

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
                placeholder = "Contoh: REG-8834",
                modifier = Modifier.testTag(DEVICE_REG_CODE_INPUT_TAG)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(color = colors.accent)
            } else {
                A3Button(
                    label = "Daftarkan Perangkat",
                    onClick = onRegisterClick,
                    enabled = deviceName.isNotBlank() && registrationCode.isNotBlank(),
                    fillWidth = true,
                    modifier = Modifier.testTag(DEVICE_REG_SUBMIT_BTN_TAG)
                )
            }

            Text(
                text = "ID Perangkat: ${deviceId.take(8)}...",
                color = colors.inkMuted,
                style = A3Theme.typography.bodySmall,
                textAlign = TextAlign.Center
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
    errorMessage: String?
) {
    val colors = A3Theme.colors

    A3Card(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag(DEVICE_REG_CARD_TAG)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                text = "Tablet \"$deviceName\" telah terdaftar di sistem. Harap hubungi Owner untuk menyetujui tablet ini di menu Pengaturan Perangkat web portal.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            if (!errorMessage.isNullOrBlank()) {
                A3StatusChip(
                    label = errorMessage,
                    token = StatusToken.DANGER
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(color = colors.accent)
            } else {
                A3Button(
                    label = "Periksa Status Sekarang",
                    variant = ButtonVariant.SECONDARY,
                    onClick = onCheckStatusClick,
                    fillWidth = true,
                    modifier = Modifier.testTag(DEVICE_REG_CHECK_STATUS_BTN_TAG)
                )
            }

            Text(
                text = "ID: $deviceId",
                color = colors.inkMuted,
                style = A3Theme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
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
        }
    }
}

@Composable
private fun DeviceRevokedCard(
    deviceId: String,
    modifier: Modifier = Modifier
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(colors.status.danger.background)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.status.danger.text,
                    modifier = Modifier.height(36.dp)
                )
            }

            Text(
                text = "Akses Tablet Dicabut",
                color = colors.status.danger.text,
                style = A3Theme.typography.headline,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Akses perangkat ini telah dicabut oleh Owner di portal web. Seluruh kredensial dan sesi lokal telah dihapus. Hubungi Owner untuk pendaftaran ulang.",
                color = colors.inkMuted,
                style = A3Theme.typography.body,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Device ID: $deviceId",
                color = colors.inkMuted,
                style = A3Theme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
