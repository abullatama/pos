package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
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
import id.a3.core.ui.theme.A3Theme

const val A3_KEYPAD_BACKSPACE_TAG = "a3_keypad_backspace"
const val A3_KEYPAD_CLEAR_TAG = "a3_keypad_clear"

/**
 * Keypad numerik 3x4 standar untuk PIN dan input nominal uang.
 *
 * Baris 1: [ 1 ] [ 2 ] [ 3 ]
 * Baris 2: [ 4 ] [ 5 ] [ 6 ]
 * Baris 3: [ 7 ] [ 8 ] [ 9 ]
 * Baris 4: [ C / 000 ] [ 0 ] [ ⌫ ]
 *
 * @param onDigitClick dipanggil saat angka 0-9 ditekan.
 * @param onBackspace dipanggil saat tombol backspace ditekan.
 * @param onClear dipanggil saat tombol clear (C) ditekan; default null jika tidak diperlukan.
 * @param showTripleZero jika true, slot kiri bawah menampilkan "000" (untuk input kas/tunai cepat).
 * @param bottomSlot slot opsional di bawah keypad untuk tombol aksi khusus (mis. "Konfirmasi").
 */
@Composable
fun A3Keypad(
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showTripleZero: Boolean = false,
    bottomSlot: @Composable (() -> Unit)? = null
) {
    val colors = A3Theme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Baris 1: 1, 2, 3
        KeypadRow(listOf("1", "2", "3"), onDigitClick)

        // Baris 2: 4, 5, 6
        KeypadRow(listOf("4", "5", "6"), onDigitClick)

        // Baris 3: 7, 8, 9
        KeypadRow(listOf("7", "8", "9"), onDigitClick)

        // Baris 4: [ C atau 000 atau Spacer ] [ 0 ] [ ⌫ Backspace ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Kolom 1 Baris 4
            when {
                showTripleZero -> {
                    KeypadCell(
                        label = "000",
                        onClick = { onDigitClick("000") },
                        modifier = Modifier.weight(1f)
                    )
                }
                onClear != null -> {
                    KeypadCell(
                        label = "C",
                        onClick = onClear,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(A3_KEYPAD_CLEAR_TAG),
                        isMuted = true
                    )
                }
                else -> {
                    SpacerKeyCell(modifier = Modifier.weight(1f))
                }
            }

            // Kolom 2 Baris 4: "0"
            KeypadCell(
                label = "0",
                onClick = { onDigitClick("0") },
                modifier = Modifier.weight(1f)
            )

            // Kolom 3 Baris 4: Backspace (⌫)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.65f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAlt)
                    .clickable(onClick = onBackspace)
                    .testTag(A3_KEYPAD_BACKSPACE_TAG),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Hapus",
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Slot Tambahan Opsional di Bawah Keypad
        bottomSlot?.let { slot ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                slot()
            }
        }
    }
}

@Composable
private fun KeypadRow(
    digits: List<String>,
    onDigitClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        digits.forEach { digit ->
            KeypadCell(
                label = digit,
                onClick = { onDigitClick(digit) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KeypadCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false
) {
    val colors = A3Theme.colors
    val bg = colors.surfaceAlt
    val contentColor = if (isMuted) colors.inkMuted else colors.ink

    Box(
        modifier = modifier
            .aspectRatio(1.65f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = A3Theme.typography.title.copy(
                fontSize = if (label.length > 1) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpacerKeyCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.aspectRatio(1.65f),
        contentAlignment = Alignment.Center
    ) {
        // intentionally empty
    }
}
