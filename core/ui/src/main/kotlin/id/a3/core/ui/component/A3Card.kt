package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

/**
 * Kartu standar A3.
 *
 * Menggunakan warna surface, border hairline, dan bayangan lembut dari design token.
 */
@Composable
fun A3Card(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val borderColor = if (selected) colors.ink else colors.border
    val backgroundColor = if (selected) colors.surfaceAlt else colors.surface

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.cardShape,
        color = backgroundColor,
        shadowElevation = if (selected) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (selected) 1.dp else 0.5.dp,
                    color = borderColor,
                    shape = shapes.cardShape
                )
                .padding(16.dp),
            content = content
        )
    }
}
