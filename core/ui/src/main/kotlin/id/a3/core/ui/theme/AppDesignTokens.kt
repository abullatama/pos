package id.a3.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class StatusColor(
    val text: Color,
    val background: Color
)

@Immutable
data class AppStatusColors(
    val success: StatusColor = StatusColor(text = Color(0xFF15683A), background = Color(0xFFE3F4EA)),
    val warning: StatusColor = StatusColor(text = Color(0xFF8A5300), background = Color(0xFFFFF4DD)),
    val danger: StatusColor = StatusColor(text = Color(0xFFB3261E), background = Color(0xFFFBE7E5)),
    val info: StatusColor = StatusColor(text = Color(0xFF2F4F9D), background = Color(0xFFE8EEFB))
)

@Immutable
data class AppColors(
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val border: Color,
    val navBackground: Color,
    val navItemText: Color,
    val navItemActiveBackground: Color,
    val navItemActiveText: Color,
    val status: AppStatusColors = AppStatusColors()
)

@Immutable
data class AppShapes(
    val cardShape: Shape,
    val buttonShape: Shape,
    val inputShape: Shape,
    val bottomBarShape: Shape
)

val CafeColors = AppColors(
    canvas = Color(0xFFFBF9F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFE5E0D8),
    ink = Color(0xFF111111),
    inkMuted = Color(0xFF4A4A4A),
    accent = Color(0xFF8E5836),
    onAccent = Color(0xFFFBF9F5),
    border = Color(0x14111111),
    navBackground = Color(0xFF111111),
    navItemText = Color(0xFFFBF9F5),
    navItemActiveBackground = Color(0xFFE5E0D8),
    navItemActiveText = Color(0xFF111111)
)

val CafeShapes = AppShapes(
    cardShape = RoundedCornerShape(4.dp),
    buttonShape = RoundedCornerShape(4.dp),
    inputShape = RoundedCornerShape(4.dp),
    bottomBarShape = RoundedCornerShape(8.dp)
)

val LaundryColors = AppColors(
    canvas = Color(0xFFFAFAFB),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFCE4EC),
    ink = Color(0xFF6A0934),
    inkMuted = Color(0xFF64748B),
    accent = Color(0xFFD81B60),
    onAccent = Color(0xFFFFFFFF),
    border = Color(0x1AD81B60),
    navBackground = Color(0xFFFFFFFF),
    navItemText = Color(0xFF64748B),
    navItemActiveBackground = Color(0xFFD81B60),
    navItemActiveText = Color(0xFFFFFFFF)
)

val LaundryShapes = AppShapes(
    cardShape = RoundedCornerShape(20.dp),
    buttonShape = RoundedCornerShape(9999.dp),
    inputShape = RoundedCornerShape(16.dp),
    bottomBarShape = RoundedCornerShape(9999.dp)
)

val LocalAppColors = staticCompositionLocalOf { CafeColors }
val LocalAppShapes = staticCompositionLocalOf { CafeShapes }
val LocalAppTypography = staticCompositionLocalOf { CafeTypography }

object A3Theme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current

    val shapes: AppShapes
        @Composable
        get() = LocalAppShapes.current

    val typography: AppTypography
        @Composable
        get() = LocalAppTypography.current
}

@Composable
fun CafeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides CafeColors,
        LocalAppShapes provides CafeShapes,
        LocalAppTypography provides CafeTypography,
        content = content
    )
}

@Composable
fun LaundryTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides LaundryColors,
        LocalAppShapes provides LaundryShapes,
        LocalAppTypography provides LaundryTypography,
        content = content
    )
}
