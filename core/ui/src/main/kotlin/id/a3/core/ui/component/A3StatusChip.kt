package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import id.a3.core.ui.theme.A3Theme

/**
 * Status token semantic, bebas dari istilah teknis.
 *
 * - neutral: digunakan untuk status pasif (diambil, dibatalkan, habis, dll).
 *            Latar dan teks menyesuaikan brand agar tidak mengganggu.
 * - success/warning/danger/info: warna fungsi tetap sama di kedua brand.
 */
enum class StatusToken {
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    NEUTRAL
}

data class ChipColors(
    val background: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color
)

@Composable
private fun resolveStatusColors(token: StatusToken): ChipColors {
    val colors = A3Theme.colors
    return when (token) {
        StatusToken.SUCCESS -> ChipColors(
            background = colors.status.success.background,
            content = colors.status.success.text
        )
        StatusToken.WARNING -> ChipColors(
            background = colors.status.warning.background,
            content = colors.status.warning.text
        )
        StatusToken.DANGER -> ChipColors(
            background = colors.status.danger.background,
            content = colors.status.danger.text
        )
        StatusToken.INFO -> ChipColors(
            background = colors.status.info.background,
            content = colors.status.info.text
        )
        StatusToken.NEUTRAL -> ChipColors(
            background = colors.surfaceAlt,
            content = colors.inkMuted
        )
    }
}

/**
 * Chip status reusable. Selalu berisi ikon dan label; tidak pernah hanya warna.
 */
@Composable
fun A3StatusChip(
    label: String,
    token: StatusToken,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val chipColors = resolveStatusColors(token)
    val shape = A3Theme.shapes.cardShape

    Row(
        modifier = modifier
            .clip(shape)
            .background(chipColors.background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = chipColors.content,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = chipColors.content,
            style = A3Theme.typography.label.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
