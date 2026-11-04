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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.a3.core.ui.theme.A3Theme

data class CashierAccountItem(
    val id: String,
    val name: String,
    val role: String
)

/**
 * Layar S03: Masuk dengan PIN (Pola Dialog Penuh Fokus 60/40 Tablet 11" Landscape).
 *
 * Mengimplementasikan aturan AUT-01 s/d AUT-04:
 * - Menampilkan daftar akun staf berizin untuk unit usaha tablet ini.
 * - Pemilihan profil staf dan input 4 digit PIN via [A3Keypad].
 * - Menampilkan indikator loading dan pesan kesalahan autentikasi.
 */
@Composable
fun CashierLoginDialog(
    accounts: List<CashierAccountItem>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLoginClick: (userId: String, pin: String) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    var selectedUser by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }
    var pinInput by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accounts) {
        if (selectedUser == null && accounts.isNotEmpty()) {
            selectedUser = accounts.first()
        }
    }

    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(760.dp)
                    .height(520.dp)
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // SISI KIRI (50%): Pilih Profil Staf
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(shapes.buttonShape)
                                    .background(colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Pilih Akun Kasir",
                                    style = A3Theme.typography.title,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.ink
                                )
                                Text(
                                    text = "Pilih nama untuk bertugas di shift ini",
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                            }
                        }

                        if (accounts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Memuat daftar akun...",
                                        style = A3Theme.typography.body,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(accounts, key = { it.id }) { user ->
                                    val isSelected = selectedUser?.id == user.id
                                    val bg = if (isSelected) colors.surfaceAlt else colors.surface
                                    val borderCol = if (isSelected) colors.accent else colors.border

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shapes.cardShape)
                                            .background(bg)
                                            .border(1.5.dp, borderCol, shapes.cardShape)
                                            .clickable {
                                                selectedUser = user
                                                pinInput = ""
                                                inlineError = null
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = user.name,
                                                style = A3Theme.typography.body,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.ink
                                            )
                                            Text(
                                                text = "ID: ${user.id}",
                                                style = A3Theme.typography.bodySmall,
                                                color = colors.inkMuted
                                            )
                                        }

                                        val roleLabel = when (user.role.lowercase()) {
                                            "manager" -> "Manager"
                                            "kasir" -> "Kasir"
                                            "staf_laundry" -> "Staf Cuci"
                                            else -> user.role
                                        }
                                        A3StatusChip(
                                            label = roleLabel,
                                            token = if (user.role == "manager") StatusToken.WARNING else StatusToken.NEUTRAL
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // PEMISAH VERTIKAL
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(colors.border)
                    )

                    // SISI KANAN (50%): Keypad PIN 4 Digit
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MASUKKAN PIN",
                                style = A3Theme.typography.label,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )

                            // 6 Indikator Digit PIN Fleksibel (4-6 Digit)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                repeat(6) { idx ->
                                    val isFilled = idx < pinInput.length
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (isFilled) colors.accent else colors.canvas)
                                            .border(1.5.dp, if (isFilled) colors.accent else colors.border, CircleShape)
                                    )
                                }
                            }

                            val activeError = errorMessage ?: inlineError
                            if (!activeError.isNullOrBlank()) {
                                Text(
                                    text = activeError,
                                    color = colors.status.danger.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Keypad 3x4 dengan Tombol Masuk Fleksibel (4-6 Digit)
                        A3Keypad(
                            onDigitClick = { digit ->
                                if (!isLoading && pinInput.length < 6) {
                                    pinInput += digit
                                    inlineError = null
                                    if (pinInput.length == 6) {
                                        val user = selectedUser
                                        if (user != null) {
                                            onLoginClick(user.id, pinInput)
                                        } else {
                                            inlineError = "Pilih akun terlebih dahulu"
                                        }
                                    }
                                }
                            },
                            onBackspace = {
                                if (!isLoading && pinInput.isNotEmpty()) {
                                    pinInput = pinInput.dropLast(1)
                                    inlineError = null
                                }
                            },
                            onClear = {
                                if (!isLoading) {
                                    pinInput = ""
                                    inlineError = null
                                }
                            },
                            bottomSlot = {
                                val isPinReady = pinInput.length in 4..6 && selectedUser != null
                                A3Button(
                                    label = if (isLoading) "Memverifikasi..." else "Masuk",
                                    onClick = {
                                        val user = selectedUser
                                        if (user == null) {
                                            inlineError = "Pilih akun terlebih dahulu"
                                        } else if (pinInput.length < 4) {
                                            inlineError = "PIN minimal 4 angka"
                                        } else {
                                            onLoginClick(user.id, pinInput)
                                        }
                                    },
                                    enabled = isPinReady && !isLoading,
                                    fillWidth = true,
                                    variant = ButtonVariant.PRIMARY
                                )
                            },
                            modifier = Modifier.width(260.dp)
                        )
                    }
                }
            }
        }
    }
}
