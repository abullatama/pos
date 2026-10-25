package id.a3.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.theme.A3Theme

const val A3_NAV_RAIL_TAG = "a3_navigation_rail"
const val A3_NAV_RAIL_ADMIN_TAG = "a3_nav_rail_admin"

/**
 * Bilah Navigasi Mengambang di Sisi Kiri (Floating Left Navigation Dock) — Standar Emas POS Tablet:
 * - Mengambang mandiri di sisi kiri dengan elevasi lembut, sudut melengkung, dan batas rim-light halus.
 * - Berada rapi di bawah bilah atas (TopBar), sehingga tidak pernah memotong area jam/tanggal tablet.
 * - Berdiri terpisah dari area katalog dan keranjang, 100% bebas dari tabrakan panel.
 * - Otomatis tersembunyi dengan animasi geser horizontal saat alur fokus penuh (pembayaran, shift) aktif.
 */
@Composable
fun A3NavigationRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onAdminClick: (() -> Unit)? = null,
    visible: Boolean = true
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = shapes.floatingRailShape,
                    spotColor = colors.ink.copy(alpha = 0.20f)
                )
                .border(
                    width = 0.5.dp,
                    color = colors.border,
                    shape = shapes.floatingRailShape
                )
                .testTag(A3_NAV_RAIL_TAG),
            shape = shapes.floatingRailShape,
            color = colors.navBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Daftar Item Navigasi Utama (Kasir, Pesanan, Menu Habis, Shift)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items.take(5).forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex

                        val targetBg = when {
                            !item.enabled -> colors.navBackground
                            isSelected -> colors.navItemActiveBackground
                            else -> colors.navBackground
                        }
                        val targetContent = when {
                            !item.enabled -> colors.navItemText.copy(alpha = 0.30f)
                            isSelected -> colors.navItemActiveText
                            else -> colors.navItemText
                        }

                        val animatedBg by animateColorAsState(
                            targetValue = targetBg,
                            animationSpec = tween(durationMillis = 100),
                            label = "floating_rail_item_bg"
                        )
                        val animatedContent by animateColorAsState(
                            targetValue = targetContent,
                            animationSpec = tween(durationMillis = 100),
                            label = "floating_rail_item_content"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 58.dp, height = 54.dp)
                                .clip(shapes.navItemShape)
                                .background(animatedBg)
                                .then(
                                    if (item.enabled) {
                                        Modifier.clickable { onItemSelected(index) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = animatedContent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    color = animatedContent,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Dasar Bawah: Tombol Cepat Pengaturan Admin
                if (onAdminClick != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 48.dp)
                            .clip(shapes.buttonShape)
                            .clickable(onClick = onAdminClick)
                            .testTag(A3_NAV_RAIL_ADMIN_TAG)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Admin",
                                tint = colors.navItemText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Admin",
                                color = colors.navItemText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }
    }
}
