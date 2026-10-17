package id.a3.feature.cafe.presentation.component

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.TableBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.theme.A3Theme

const val CAFE_MOVE_TABLE_DIALOG_TAG = "cafe_move_table_dialog"
const val CAFE_MOVE_TABLE_CONFIRM_BTN_TAG = "cafe_move_table_confirm_btn"

/**
 * Dialog Pindah Meja (Dine-In) — C05 POS A3 Coffeeshop.
 *
 * Memungkinkan kasir memindahkan pesanan dari meja asal ke meja tujuan.
 * Menghasilkan event outbox 'order.table_moved' untuk disinkronkan ke server.
 */
@Composable
fun CafeMoveTableDialog(
    currentTable: String,
    onDismissRequest: () -> Unit,
    onConfirmMoveTable: (newTable: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    val defaultTables = remember {
        (1..16).map { "Meja %02d".format(it) }
    }

    var selectedTable by remember { mutableStateOf(currentTable) }
    var customTableInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .widthIn(min = 480.dp, max = 540.dp)
                .testTag(CAFE_MOVE_TABLE_DIALOG_TAG),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Dialog: Ikon Meja, Judul, & Tombol Tutup
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
                                .background(colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TableBar,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Pindah Meja",
                                color = colors.ink,
                                style = A3Theme.typography.headline.copy(fontSize = 19.sp),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Meja saat ini: $currentTable",
                                color = colors.inkMuted,
                                style = A3Theme.typography.bodySmall
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = colors.ink,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Grid Pilihan Meja Cepat (Meja 01 s/d Meja 16)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PILIH MEJA TUJUAN",
                        color = colors.ink,
                        style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(defaultTables) { tableLabel ->
                            val isSelected = (customTableInput.isBlank() && selectedTable == tableLabel)
                            val isCurrent = tableLabel == currentTable

                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> colors.accent
                                            isCurrent -> colors.surfaceAlt.copy(alpha = 0.4f)
                                            else -> colors.surfaceAlt
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.accent else Color(0xFFE5E7EB),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedTable = tableLabel
                                        customTableInput = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tableLabel,
                                    color = if (isSelected) Color.White else colors.ink,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Input Meja Kustom / Area Tertentu (Outdoor / VIP)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Atau Ketik Nomor / Nama Meja Kustom:",
                        color = colors.inkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    A3TextField(
                        value = customTableInput,
                        onValueChange = {
                            customTableInput = it
                            if (it.isNotBlank()) {
                                selectedTable = it.trim()
                            }
                        },
                        placeholder = "Contoh: VIP-01, Outdoor-03, Bar-02",
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Footer Tombol: Batal & Konfirmasi Pindah Meja
                val targetTable = if (customTableInput.isNotBlank()) customTableInput.trim() else selectedTable
                val isSameTable = targetTable.equals(currentTable, ignoreCase = true)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    A3Button(
                        label = "Batal",
                        variant = ButtonVariant.GHOST,
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(0.8f)
                    )

                    A3Button(
                        label = "Pindahkan ke $targetTable",
                        variant = ButtonVariant.PRIMARY,
                        enabled = targetTable.isNotBlank() && !isSameTable,
                        onClick = {
                            onConfirmMoveTable(targetTable)
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag(CAFE_MOVE_TABLE_CONFIRM_BTN_TAG)
                    )
                }
            }
        }
    }
}
