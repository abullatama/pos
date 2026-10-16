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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

/**
 * Keypad numerik reusable untuk PIN dan input tunai.
 *
 * @param onDigitClick dipanggil saat angka 0-9 ditekan.
 * @param onBackspace dipanggil saat tombol backspace ditekan.
 * @param onClear dipanggil saat tombol clear (C) ditekan; default null jika tidak diperlukan.
 * @param bottomSlot slot di bawah keypad untuk tombol aksi khusus (mis. "Bayar", "Konfirmasi PIN").
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
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        if (showTripleZero) listOf("000", "0", "C") else listOf("", "0", "C")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    when {
                        key.isEmpty() -> {
                            SpacerKeyCell(modifier = Modifier.weight(1f))
                        }
                        key == "C" && onClear != null -> {
                            KeypadCell(
                                label = key,
                                onClick = onClear,
                                modifier = Modifier.weight(1f),
                                isAccent = false
                            )
                        }
                        key == "C" && onClear == null -> {
                            SpacerKeyCell(modifier = Modifier.weight(1f))
                        }
                        else -> {
                            KeypadCell(
                                label = key,
                                onClick = { onDigitClick(key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Baris backspace + bottom slot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(A3Theme.colors.surfaceAlt)
                    .clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Hapus",
                    tint = A3Theme.colors.ink,
                    modifier = Modifier.height(24.dp)
                )
            }

            bottomSlot?.let {
                Box(modifier = Modifier.weight(2f)) {
                    it()
                }
            } ?: run {
                SpacerKeyCell(modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun KeypadCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false
) {
    val colors = A3Theme.colors
    val bg = if (isAccent) colors.accent else colors.surfaceAlt
    val contentColor = if (isAccent) colors.onAccent else colors.ink

    Box(
        modifier = modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = A3Theme.typography.title,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpacerKeyCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.aspectRatio(1.6f),
        contentAlignment = Alignment.Center
    ) {
        // intentionally empty
    }
}
