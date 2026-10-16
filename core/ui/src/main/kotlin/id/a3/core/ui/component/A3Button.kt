package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

enum class ButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    DANGER
}

/**
 * Tombol standar A3.
 *
 * Tinggi tetap 56 dp agar mudah disentuh. Label menyebutkan aksi secara eksplisit.
 */
@Composable
fun A3Button(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    fillWidth: Boolean = false
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val (backgroundColor, contentColor, borderColor) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(
            colors.accent,
            colors.onAccent,
            Color.Transparent
        )
        ButtonVariant.SECONDARY -> Triple(
            colors.canvas,
            colors.ink,
            colors.ink
        )
        ButtonVariant.GHOST -> Triple(
            Color.Transparent,
            colors.ink,
            Color.Transparent
        )
        ButtonVariant.DANGER -> Triple(
            colors.status.danger.background,
            colors.status.danger.text,
            Color.Transparent
        )
    }

    val effectiveBackground = if (enabled) backgroundColor else colors.surfaceAlt
    val effectiveContent = if (enabled) contentColor else colors.inkMuted
    val effectiveBorder = if (enabled) borderColor else Color.Transparent

    Box(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .height(56.dp)
            .clip(shapes.buttonShape)
            .border(
                width = if (variant == ButtonVariant.SECONDARY && enabled) 1.5.dp else 0.dp,
                color = effectiveBorder,
                shape = shapes.buttonShape
            )
            .background(effectiveBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = effectiveContent,
            style = A3Theme.typography.button,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
