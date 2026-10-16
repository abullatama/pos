package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.a3.core.ui.theme.A3Theme

/**
 * Dialog standar A3.
 *
 * Semua dialog harus menggunakan ini agar bentuk, spacing, dan tombol konsisten.
 * Tombol aksi utama selalu menyebutkan aksinya ("Terima pembayaran", bukan "OK").
 *
 * @param onDismissRequest dipanggil saat dialog ditutup lewat backdrop atau tombol batal.
 * @param title judul dialog. Wajib.
 * @param body isi dialog, bisa String atau custom composable.
 * @param confirmLabel label tombol aksi utama. Wajib jika [onConfirm] ada.
 * @param onConfirm callback tombol aksi utama.
 * @param dismissLabel label tombol batal; null berarti tombol batal disembunyikan.
 * @param onDismiss callback tombol batal; jika null, fallback ke [onDismissRequest].
 * @param danger true untuk aksi destruktif (warna Bahaya); false untuk aksi utama (warna aksen).
 */
@Composable
fun A3Dialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    body: @Composable (() -> Unit)? = null,
    bodyText: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    danger: Boolean = false
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shapes.cardShape)
                .background(colors.surface)
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    color = colors.ink,
                    style = A3Theme.typography.headline
                )

                body?.invoke() ?: bodyText?.let {
                    Text(
                        text = it,
                        color = colors.inkMuted,
                        style = A3Theme.typography.body,
                        textAlign = TextAlign.Start
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissLabel?.let { label ->
                        A3Button(
                            label = label,
                            variant = ButtonVariant.GHOST,
                            onClick = onDismiss ?: onDismissRequest
                        )
                    }

                    confirmLabel?.let { label ->
                        A3Button(
                            label = label,
                            variant = if (danger) ButtonVariant.DANGER else ButtonVariant.PRIMARY,
                            onClick = onConfirm ?: onDismissRequest
                        )
                    }
                }
            }
        }
    }
}
