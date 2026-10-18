package id.a3.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

@Immutable
data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector
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
                    elevation = 16.dp,
                    shape = shapes.bottomBarShape,
                    spotColor = colors.ink.copy(alpha = 0.22f)
                )
                .border(
                    width = 0.5.dp,
                    color = colors.border,
                    shape = shapes.bottomBarShape
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.take(5).forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex

                    val targetBg = if (isSelected) colors.navItemActiveBackground else colors.navBackground
                    val targetContentColor = if (isSelected) colors.navItemActiveText else colors.navItemText

                    val animatedBg by animateColorAsState(
                        targetValue = targetBg,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_item_bg"
                    )
                    val animatedContentColor by animateColorAsState(
                        targetValue = targetContentColor,
                        animationSpec = tween(durationMillis = 200),
                        label = "nav_item_content"
                    )

                    val itemShape = shapes.navItemShape

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 84.dp, minHeight = 56.dp)
                            .clip(itemShape)
                            .background(animatedBg)
                            .clickable { onItemSelected(index) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = animatedContentColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(bottom = 2.dp)
                            )
                            Text(
                                text = item.label,
                                color = animatedContentColor,
                                style = if (isSelected) {
                                    A3Theme.typography.label.copy(fontWeight = FontWeight.Bold)
                                } else {
                                    A3Theme.typography.label.copy(fontWeight = FontWeight.Medium)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
