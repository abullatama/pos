package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.a3.core.ui.theme.A3Theme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class AuditReasonOption(
    val code: String,
    val label: String
)

val STANDARD_AUDIT_REASONS = listOf(
    AuditReasonOption("wrong_input", "Salah input"),
    AuditReasonOption("customer_cancelled", "Pelanggan batal"),
    AuditReasonOption("quality_complaint", "Komplain kualitas"),
    AuditReasonOption("wrong_made", "Salah buat"),
    AuditReasonOption("promo", "Promo"),
    AuditReasonOption("other", "Lainnya")
)

/**
 * Dialog Otorisasi PIN Manager (S14).
 * Sesuai aturan COR-01 s/d COR-08, F35, F36, dan antislop design guidelines:
 * - Menampilkan nominal dana yang disetujui secara presisi.
 * - Banner peringatan informatif bila transaksi berasal dari shift sebelumnya (F36).
 * - Pilihan alasan audit tetap wajib dari daftar resmi (COR-01).
 * - Keypad numerik 3x4 taktil dan indikator 4-digit PIN.
 * - Penegakan batas salah 3 kali terkunci (COR-07).
 */
@Composable
fun ManagerPinApprovalDialog(
    title: String,
    actionType: String = "REFUND TUNAI",
    amountToApprove: Long,
    isOldTransaction: Boolean = false,
    errorMessage: String? = null,
    isManagerLocked: Boolean = false,
    lockoutRemainingSeconds: Int = 0,
    onDismiss: () -> Unit,
    onSubmitApproval: (pin: String, reasonCode: String, note: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    var selectedReasonCode by remember { mutableStateOf(STANDARD_AUDIT_REASONS.first().code) }
    var noteInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .width(680.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                style = A3Theme.typography.title,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                            Text(
                                text = "Otorisasi PIN Manager diperlukan untuk tindakan ini",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = colors.inkMuted
                        )
                    }
                }

                // Banner Transaksi Shift Lalu (F36)
                if (isOldTransaction) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Refund Transaksi Shift Lalu — Batas tablet: maks Rp 100.000 & umur transaksi <= 14 hari.",
                            color = Color(0xFFE65100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Grid 2-Kolom: Kiri (Rincian & Alasan) | Kanan (PIN & Keypad)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // SISI KIRI (55%): Ringkasan Nilai & Pilihan Alasan
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Kartu Nilai yang Disetujui
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceAlt)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Nilai yang Disetujui ($actionType):",
                                    color = colors.inkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatRupiah(amountToApprove),
                                    color = colors.ink,
                                    style = A3Theme.typography.headline.copy(fontSize = 22.sp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Pilihan Alasan Audit (COR-01)
                        Text(
                            text = "Alasan Pembatalan / Refund (Wajib):",
                            color = colors.ink,
                            style = A3Theme.typography.label,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            STANDARD_AUDIT_REASONS.forEach { opt ->
                                val isSelected = opt.code == selectedReasonCode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.08f) else colors.surface)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) colors.accent else colors.border,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedReasonCode = opt.code
                                            inlineError = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) colors.accent else Color.Transparent)
                                            .border(1.5.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                                    )
                                    Text(
                                        text = opt.label,
                                        color = if (isSelected) colors.accent else colors.ink,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Input Catatan jika 'other' dipilih
                        if (selectedReasonCode == "other") {
                            A3TextField(
                                value = noteInput,
                                onValueChange = {
                                    noteInput = it
                                    inlineError = null
                                },
                                label = "Catatan Alasan Tambahan",
                                placeholder = "Tuliskan keterangan detail alasan...",
                                error = if (selectedReasonCode == "other" && noteInput.isBlank()) inlineError else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Divider Pemisah Vertikal
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(340.dp)
                            .background(colors.border)
                    )

                    // SISI KANAN (45%): Indikator PIN & Keypad 3x4
                    Column(
                        modifier = Modifier.weight(0.9f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "PIN MANAGER (4 Digit)",
                            style = A3Theme.typography.label,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )

                        // 4 Indikator Digit PIN Bulat
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            repeat(4) { idx ->
                                val isFilled = idx < pinInput.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (isFilled) colors.accent else colors.canvas)
                                        .border(1.5.dp, if (isFilled) colors.accent else colors.border, CircleShape)
                                )
                            }
                        }

                        // Pesan Error / Lockout
                        val activeError = errorMessage ?: inlineError
                        if (isManagerLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = colors.status.danger.text,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Akun terkunci ($lockoutRemainingSeconds dtk)",
                                    color = colors.status.danger.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!activeError.isNullOrBlank()) {
                            Text(
                                text = activeError,
                                color = colors.status.danger.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // Keypad Numerik 3x4
                        A3Keypad(
                            onDigitClick = { digit ->
                                if (!isManagerLocked && pinInput.length < 4) {
                                    pinInput += digit
                                    inlineError = null
                                }
                            },
                            onBackspace = {
                                if (pinInput.isNotEmpty()) {
                                    pinInput = pinInput.dropLast(1)
                                    inlineError = null
                                }
                            },
                            onClear = {
                                pinInput = ""
                                inlineError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Tombol Aksi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            A3Button(
                                label = "Batal",
                                onClick = onDismiss,
                                variant = ButtonVariant.GHOST,
                                modifier = Modifier.weight(1f)
                            )

                            A3Button(
                                label = "Setujui",
                                onClick = {
                                    if (isManagerLocked) return@A3Button
                                    if (pinInput.length < 4) {
                                        inlineError = "Masukkan 4 digit PIN lengkap"
                                        return@A3Button
                                    }
                                    if (selectedReasonCode == "other" && noteInput.trim().isBlank()) {
                                        inlineError = "Alasan 'Lainnya' wajib mengisi catatan"
                                        return@A3Button
                                    }
                                    onSubmitApproval(
                                        pinInput,
                                        selectedReasonCode,
                                        if (selectedReasonCode == "other") noteInput.trim() else null
                                    )
                                },
                                variant = ButtonVariant.DANGER,
                                enabled = !isManagerLocked && pinInput.length == 4,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

