package id.a3.feature.cafe.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.presentation.CafeMenuViewModel

const val CAFE_SOLDOUT_SCREEN_TAG = "cafe_soldout_screen"
const val CAFE_SOLDOUT_SEARCH_TAG = "cafe_soldout_search"

/**
 * Layar C07: Menu Habis (A3 Coffeeshop).
 *
 * Mengelola ketersediaan menu secara langsung:
 * - Menandai menu Habis / Tersedia secara manual.
 * - Memperbarui status di Room database dan mencatat event outbox 'menu.sold_out_changed'.
 */
@Composable
fun CafeSoldOutScreen(
    viewModel: CafeMenuViewModel,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }

    val categories = remember(uiState.products) {
        listOf("Semua") + uiState.products.map { it.category }.distinct()
    }

    val filteredProducts = uiState.products.filter { prod ->
        val matchesCategory = (selectedCategory == "Semua") || prod.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() || prod.name.contains(searchQuery.trim(), ignoreCase = true)
        matchesCategory && matchesQuery
    }

    val soldOutCount = uiState.products.count { it.soldOut }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(CAFE_SOLDOUT_SCREEN_TAG),
        color = Color(0xFFF4F2E6)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Layar: Judul, Indikator Jumlah Habis & Search Bar Pil
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Kelola Menu Habis",
                        color = colors.ink,
                        style = A3Theme.typography.headline.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (soldOutCount > 0) "$soldOutCount menu sedang ditandai Habis" else "Semua menu tersedia untuk dipesan",
                        color = if (soldOutCount > 0) colors.status.danger.text else colors.inkMuted,
                        style = A3Theme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                }

                // Search Bar Pil Kompak 44 dp
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(9999.dp))
                        .padding(horizontal = 14.dp)
                        .testTag(CAFE_SOLDOUT_SEARCH_TAG),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            cursorBrush = SolidColor(colors.accent),
                            textStyle = A3Theme.typography.body.copy(
                                color = colors.ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cari menu...",
                                        color = colors.inkMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Hapus pencarian",
                                tint = colors.inkMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }
            }

            // Banner Filter Kategori (Horizontal Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    val bg = if (isSelected) colors.accent else Color.White
                    val textCol = if (isSelected) Color.White else colors.ink
                    val borderCol = if (isSelected) colors.accent else Color(0xFFE5E7EB)

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(bg)
                            .border(1.dp, borderCol, RoundedCornerShape(9999.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            color = textCol,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Daftar Kartu Menu dengan Saklar Ketersediaan (Tersedia / Habis)
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada menu yang sesuai dengan filter",
                        color = colors.inkMuted,
                        style = A3Theme.typography.body
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        SoldOutProductRow(
                            product = product,
                            onToggleSoldOut = { soldOut ->
                                viewModel.toggleProductSoldOut(product.id, soldOut)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Baris Item Menu untuk Kelola Ketersediaan
 */
@Composable
private fun SoldOutProductRow(
    product: CafeProduct,
    onToggleSoldOut: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val isSoldOut = product.soldOut

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kolom Kiri: Nama Produk, Kategori, Harga & Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSoldOut) Color(0xFFFEE2E2) else colors.surfaceAlt
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSoldOut) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (isSoldOut) colors.status.danger.text else colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = product.name,
                            color = colors.ink,
                            style = A3Theme.typography.title.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSoldOut) colors.status.danger.background else Color(0xFFE8F5E9)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSoldOut) "Habis" else "Tersedia",
                                color = if (isSoldOut) colors.status.danger.text else colors.accent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.category,
                            color = colors.inkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "•",
                            color = colors.inkMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatRupiah(product.basePrice),
                            color = colors.ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Kolom Kanan: Segmented Pill Switch [ Tersedia ] [ Habis ]
            Row(
                modifier = Modifier
                    .width(180.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(colors.surfaceAlt)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tombol Tersedia
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(if (!isSoldOut) colors.accent else Color.Transparent)
                        .clickable { onToggleSoldOut(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tersedia",
                        color = if (!isSoldOut) Color.White else colors.inkMuted,
                        fontSize = 12.sp,
                        fontWeight = if (!isSoldOut) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // Tombol Habis
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(if (isSoldOut) colors.status.danger.text else Color.Transparent)
                        .clickable { onToggleSoldOut(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Habis",
                        color = if (isSoldOut) Color.White else colors.inkMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSoldOut) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
