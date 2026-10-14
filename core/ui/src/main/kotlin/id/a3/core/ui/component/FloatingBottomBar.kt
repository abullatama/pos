package id.a3.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.theme.A3Theme

data class NavItem(
    val id: String,
    val label: String,
    val iconEmoji: String
)

@Composable
fun FloatingBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = shapes.bottomBarShape,
            color = colors.navBackground,
            shadowElevation = 8.dp,
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = shapes.bottomBarShape,
                    spotColor = colors.ink.copy(alpha = 0.25f)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.take(5).forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex

                    val itemBg = if (isSelected) colors.navItemActiveBackground else colors.navBackground
                    val itemContentColor = if (isSelected) colors.navItemActiveText else colors.navItemText
                    val itemShape = shapes.cardShape

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 84.dp, minHeight = 56.dp)
                            .clip(itemShape)
                            .background(itemBg)
                            .clickable { onItemSelected(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = item.iconEmoji,
                                fontSize = 18.sp
                            )
                            Text(
                                text = item.label,
                                color = itemContentColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
