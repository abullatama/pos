package id.a3.feature.shift.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Close
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
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.theme.A3Theme

/**
 * Dialog Tutup Paksa Shift oleh Manager (F38, ShiftForceClosed).
 * Sesuai aturan spek v2.2, D-01, dan antislop design guidelines:
 * - Digunakan saat kasir berhalangan hadir atau tablet tertinggal shift terbuka.
 * - Mewajibkan input catatan alasan (note).
 * - Mewajibkan otorisasi PIN Manager lokal (PIN "4444" & proteksi 3x salah terkunci 5 menit COR-07).
 * - Menutup shift seketika dan membebaskan tablet agar kasir berikutnya bisa membuka shift baru.
 */
@Composable
fun ForceCloseShiftDialog(
    shiftId: String,
    cashierName: String,
    errorMessage: String? = null,
    isManagerLocked: Boolean = false,
    lockoutRemainingSeconds: Int = 0,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmitForceClose: (pin: String, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    var noteInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .width(640.dp)
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Tutup Paksa Shift (Manager)",
                                style = A3Theme.typography.title,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                            Text(
                                text = "Shift: ${shiftId.take(8)}... • Kasir: $cashierName",
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

                // Banner Peringatan Tindakan Khusus
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
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tutup paksa hanya boleh dilakukan jika kasir berhalangan hadir. Shift akan ditutup dan dicatat ke audit log.",
                        color = Color(0xFFE65100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Grid 2-Kolom: Kiri (Alasan) | Kanan (PIN & Keypad)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // SISI KIRI (52%): Catatan Alasan
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ALASAN PENUTUPAN PAKSA (Wajib)",
                            style = A3Theme.typography.label,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted
                        )

                        A3TextField(
                            value = noteInput,
                            onValueChange = {
                                noteInput = it
                                inlineError = null
                            },
                            label = "Catatan Audit Manager",
                            placeholder = "Contoh: Kasir sakit mendadak, shift ditutup paksa",
                            error = if (noteInput.isBlank()) inlineError else null,
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Catatan ini akan tersimpan permanen di event outbox shift.force_closed untuk ditinjau oleh Owner.",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Divider Pemisah
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(300.dp)
                            .background(colors.border)
                    )

                    // SISI KANAN (48%): PIN Manager & Keypad
                    Column(
                        modifier = Modifier.weight(0.9f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PIN MANAGER (4 Digit)",
                            style = A3Theme.typography.label,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )

                        // 4 Indikator Digit PIN
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
                                        .background(if (isFilled) Color(0xFFC62828) else colors.canvas)
                                        .border(1.5.dp, if (isFilled) Color(0xFFC62828) else colors.border, CircleShape)
                                )
                            }
                        }

                        // Pesan Error / Lockout
                        val activeError = errorMessage ?: inlineError
                        if (isManagerLocked) {
                            Text(
                                text = "Akun terkunci ($lockoutRemainingSeconds dtk)",
                                color = colors.status.danger.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (!activeError.isNullOrBlank()) {
                            Text(
                                text = activeError,
                                color = colors.status.danger.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            A3Button(
                                label = "Batal",
                                onClick = onDismiss,
                                variant = ButtonVariant.GHOST,
                                modifier = Modifier.weight(1f)
                            )

                            A3Button(
                                label = if (isSubmitting) "Menutup..." else "Tutup Paksa",
                                onClick = {
                                    if (isManagerLocked || isSubmitting) return@A3Button
                                    if (noteInput.trim().isBlank()) {
                                        inlineError = "Catatan alasan wajib diisi"
                                        return@A3Button
                                    }
                                    if (pinInput.length < 4) {
                                        inlineError = "Masukkan 4 digit PIN lengkap"
                                        return@A3Button
                                    }
                                    onSubmitForceClose(pinInput, noteInput.trim())
                                },
                                variant = ButtonVariant.DANGER,
                                enabled = !isManagerLocked && !isSubmitting && pinInput.length == 4 && noteInput.trim().isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
