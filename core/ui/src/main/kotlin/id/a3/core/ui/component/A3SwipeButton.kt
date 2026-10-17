package id.a3.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.theme.A3Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val A3_SWIPE_BUTTON_TAG = "a3_swipe_button"
const val A3_SWIPE_THUMB_TAG = "a3_swipe_thumb"

/**
 * Tombol Geser Taktil (Swipe-to-Confirm / Slide-to-Action) — Persis seperti pada contoh POS modern:
 * - Trek Kapsul Pil Penuh (`rounded-full`) dengan warna Obsidian Black (#141414).
 * - Tombol Thumb Lingkaran Putih di sebelah kiri dengan ikon panah tebal ->.
 * - Indikator arah geser `>>>` di sebelah kanan.
 * - Ketika kasir menggeser lingkaran putih melewati ambang batas 70%, aksi terpicu dengan efek pegas halus.
 */
@Composable
fun A3SwipeButton(
    text: String,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = A3Theme.colors.ink,
    thumbColor: Color = Color.White,
    iconColor: Color = Color(0xFF141414),
    textColor: Color = Color.White
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var trackWidthPx by remember { mutableIntStateOf(0) }
    val thumbSizeDp = 44.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val paddingDp = 4.dp
    val paddingPx = with(density) { paddingDp.toPx() }

    val offsetX = remember { Animatable(0f) }

    val maxDragPx = remember(trackWidthPx, thumbSizePx, paddingPx) {
        (trackWidthPx - thumbSizePx - paddingPx * 2).coerceAtLeast(0f)
    }

    val dragProgress = if (maxDragPx > 0) (offsetX.value / maxDragPx).coerceIn(0f, 1f) else 0f
    val textAlpha = (1f - dragProgress * 1.4f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f))
            .border(
                width = 0.5.dp,
                color = if (enabled) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(9999.dp)
            )
            .onSizeChanged { trackWidthPx = it.width }
            .testTag(A3_SWIPE_BUTTON_TAG)
            .then(
                if (enabled && maxDragPx > 0) {
                    Modifier.pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value >= maxDragPx * 0.70f) {
                                        // Animasikan sampai ke ujung dan picu aksi
                                        offsetX.animateTo(
                                            targetValue = maxDragPx,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        )
                                        onSwipeComplete()
                                        // Reset dengan pegas halus
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                                        )
                                    } else {
                                        // Membal kembali ke titik awal
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                coroutineScope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Lapisan Teks di Tengah & Indikator Arah Chevron di Kanan
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = thumbSizeDp + 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = textColor,
                style = A3Theme.typography.body.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.3.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .alpha(textAlpha)
            )

            // Tiga Chevron Penunjuk Arah Geser (>>>)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (enabled) 0.75f else 0.25f)
            ) {
                Text(
                    text = ">>>",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Thumb Bulat Geser (Thumb Circle with Arrow ->)
        Box(
            modifier = Modifier
                .padding(start = paddingDp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
                .testTag(A3_SWIPE_THUMB_TAG),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Geser",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
