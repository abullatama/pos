package id.a3.feature.cafe.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.domain.cafe.CafeCalculationResult
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3SwipeButton
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.model.AppliedDiscount
import id.a3.feature.cafe.model.CafeCartLine
import id.a3.feature.cafe.model.CafeOrderType
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.CafeOrderHeader
import id.a3.feature.cafe.presentation.component.CafeDiscountDialog
import id.a3.feature.cafe.presentation.component.CafeMoveTableDialog
import id.a3.feature.cafe.presentation.component.CafeVariantBottomSheet

/**
 * Layar Kasir POS Tablet Utama (C01) — Berdasarkan Konsep Desain Modern Organik & Clean Utility (design2.md / contoh.jpeg):
 * - Area Kiri: Search bar pil penuh, Banner Kategori dinamis slidable dengan watermark artistik, Grid produk adaptif (5 kolom saat struk tertutup / 4 kolom saat struk terbuka).
 * - Area Kanan: Struk Pesanan Melayang Mandiri (Purchase Receipt Drawer 24dp tanpa border hitam kaku) dengan 3 Segmented Control
 *   [Dine In] [Take Away] [Order Online], input nama & meja, daftar item struk terurai (tidak terpotong), dan
 *   Tombol Geser Adaptif A3SwipeButton (Solusi B) di dasar struk.
 * - UX Auto-Expand / Collapse: Panel struk default tertutup saat keranjang kosong dan otomatis meluncur terbuka saat kasir menambah menu baru.
 * - Warna: Opsi C Monokrom dengan aksen Deep Forest Artisan Green (#0A3D24) pada titik-titik interaksi fokus.
 */
@Composable
fun CafePosScreen(
    viewModel: CafeMenuViewModel,
    onCheckoutClick: (CafeCalculationResult) -> Unit,
    onSendToKitchenClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCheckingOut by viewModel.isCheckingOut.collectAsState()
    val colors = A3Theme.colors
    val coroutineScope = rememberCoroutineScope()

    // Panel struk default tertutup saat kosong, dan otomatis terbuka saat item ditambahkan
    var isCartExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.cartLines.size) {
        if (uiState.cartLines.isNotEmpty()) {
            isCartExpanded = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // SISI KIRI: Katalog Menu & Kategori (Lebar adaptif sesuai status panel struk)
            Box(
                modifier = Modifier
                    .weight(if (isCartExpanded) 0.64f else 1f)
                    .fillMaxHeight()
            ) {
                MenuCatalogPanel(
                    allProducts = uiState.allProducts.ifEmpty { uiState.products },
                    displayedProducts = uiState.products,
                    categories = uiState.categories,
                    selectedCategory = uiState.selectedCategory,
                    searchQuery = uiState.searchQuery,
                    cartLines = uiState.cartLines,
                    calculationTotal = uiState.calculation.total,
                    isCartExpanded = isCartExpanded,
                    onExpandCartClick = { isCartExpanded = true },
                    onCategorySelected = { viewModel.selectCategory(it) },
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    onProductClick = { viewModel.onProductClicked(it) }
                )
            }

            // SISI KANAN: Panel Struk Pesanan Melayang Mandiri (Purchase Receipt Drawer 24dp)
            AnimatedVisibility(
                visible = isCartExpanded,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    PurchaseReceiptCard(
                        orderHeader = uiState.orderHeader,
                        cartLines = uiState.cartLines,
                        calculation = uiState.calculation,
                        appliedDiscount = uiState.appliedDiscount,
                        isCheckingOut = isCheckingOut,
                        onOpenDiscountDialog = { viewModel.openDiscountDialog() },
                        onRemoveDiscount = { viewModel.removeDiscount() },
                        onOpenMoveTableDialog = { viewModel.openMoveTableDialog() },
                        onCollapseClick = { isCartExpanded = false },
                        onOrderTypeSelected = { viewModel.setOrderType(it) },
                        onCustomerNameChanged = { viewModel.setCustomerName(it) },
                        onTableSelected = { viewModel.setTableOrQueueLabel(it) },
                        onQuantityChange = { id, delta -> viewModel.updateQuantity(id, delta) },
                        onRemoveLine = { viewModel.removeCartLine(it) },
                        onClearCart = { viewModel.clearCart() },
                        onCheckoutClick = { onCheckoutClick(uiState.calculation) },
                        onSendToKitchenClick = {
                            coroutineScope.launch {
                                viewModel.sendOrderToKitchen()
                                onSendToKitchenClick()
                            }
                        }
                    )
                }
            }
        }

        // C02: Modal Bottom Sheet untuk Varian & Add-on
        uiState.sheetProduct?.let { product ->
            CafeVariantBottomSheet(
                product = product,
                onDismissRequest = { viewModel.dismissSheet() },
                onAddToCart = { cartLine ->
                    viewModel.addCartLine(cartLine)
                    isCartExpanded = true
                }
            )
        }

        // C06: Modal Dialog Diskon & Potongan (Preset & Manual PIN Manager)
        if (uiState.isDiscountDialogOpen) {
            CafeDiscountDialog(
                subtotal = uiState.calculation.subtotal,
                currentDiscount = uiState.appliedDiscount,
                presets = uiState.discountPresets,
                onDismissRequest = { viewModel.dismissDiscountDialog() },
                onApplyDiscount = { viewModel.applyAppliedDiscount(it) },
                onRemoveDiscount = { viewModel.removeDiscount() },
                onVerifyManagerPin = { pin -> viewModel.verifyManagerPin(pin) }
            )
        }

        // C05: Modal Dialog Pindah Meja (Dine-In)
        if (uiState.isMoveTableDialogOpen) {
            CafeMoveTableDialog(
                currentTable = uiState.orderHeader.tableOrQueueLabel,
                onDismissRequest = { viewModel.dismissMoveTableDialog() },
                onConfirmMoveTable = { viewModel.moveTable(it) }
            )
        }
    }
}

/**
 * Panel Sisi Kiri: Search Bar, Banner Kategori Dinamis Slidable, dan Grid Produk Adaptif
 */
@Composable
internal fun MenuCatalogPanel(
    allProducts: List<CafeProduct>,
    displayedProducts: List<CafeProduct>,
    categories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    cartLines: List<CafeCartLine>,
    calculationTotal: Long,
    isCartExpanded: Boolean,
    onExpandCartClick: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onProductClick: (CafeProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    // Grid 4 kolom saat struk terbuka; 5 kolom saat struk tertutup (sesuai design2.md 5.4)
    val gridColumns = if (isCartExpanded) 4 else 5

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. BARIS SEARCH BAR & TOMBOL PINTASAN KERANJANG (saat struk tertutup)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillSearchBar(
                query = searchQuery,
                onQueryChanged = onSearchQueryChanged,
                modifier = Modifier.weight(1f)
            )

            // Tombol buka struk saat panel kanan sedang tertutup (collapsed)
            if (!isCartExpanded) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = if (cartLines.isNotEmpty()) colors.accent else Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .clickable(onClick = onExpandCartClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShoppingBag,
                            contentDescription = "Buka Struk",
                            tint = if (cartLines.isNotEmpty()) Color.White else colors.ink,
                            modifier = Modifier.size(20.dp)
                        )
                        val totalQty = cartLines.sumOf { it.quantity }
                        Text(
                            text = if (totalQty > 0) "$totalQty item · ${formatRupiah(calculationTotal)}" else "Lihat Pesanan",
                            color = if (cartLines.isNotEmpty()) Color.White else colors.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = if (cartLines.isNotEmpty()) Color.White else colors.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. BANNER KATEGORI DINAMIS & SLIDABLE (Bisa Digeser Horizontal)
        DynamicCategoryBannersRow(
            allProducts = allProducts,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. GRID PRODUK ADAPTIF (4 atau 5 Kolom)
        if (displayedProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalCafe,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Tidak Ada Menu Ditemukan",
                        color = colors.ink,
                        style = A3Theme.typography.title,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Coba kata kunci lain atau pilih kategori yang berbeda.",
                        color = colors.inkMuted,
                        style = A3Theme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedProducts, key = { it.id }) { product ->
                    ProductGridItem(
                        product = product,
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}

/**
 * Bilah Pencarian Kapsul Pil Penuh (Search Bar 48dp)
 */
@Composable
private fun PillSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(9999.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = colors.ink,
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                cursorBrush = SolidColor(colors.ink),
                textStyle = A3Theme.typography.body.copy(
                    fontSize = 14.sp,
                    color = colors.ink,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search menu kopi, minuman, roti...",
                            color = colors.inkMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceAlt)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⌘K",
                        color = colors.ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Banner Kategori Dinamis & Slidable (Bisa Digeser Horizontal — Bebas Border Hitam Kaku)
 */
@Composable
private fun DynamicCategoryBannersRow(
    allProducts: List<CafeProduct>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Memoize perhitungan jumlah dan status ketersediaan kategori agar tidak dihitung ulang setiap recomposition
    val categoryStats = remember(allProducts, categories) {
        categories.associateWith { category ->
            val count = if (category.equals("Semua", ignoreCase = true)) {
                allProducts.size
            } else {
                allProducts.count { it.category.equals(category, ignoreCase = true) }
            }
            val hasAvailable = if (category.equals("Semua", ignoreCase = true)) {
                allProducts.any { !it.soldOut }
            } else {
                allProducts.any { it.category.equals(category, ignoreCase = true) && !it.soldOut }
            }
            val icon: ImageVector = when {
                category.contains("Kopi", ignoreCase = true) || category.contains("Coffee", ignoreCase = true) -> Icons.Outlined.LocalCafe
                category.contains("Teh", ignoreCase = true) || category.contains("Tea", ignoreCase = true) || category.contains("Non-kopi", ignoreCase = true) -> Icons.Outlined.EmojiFoodBeverage
                category.contains("Roti", ignoreCase = true) || category.contains("Pastry", ignoreCase = true) || category.contains("Makanan", ignoreCase = true) || category.contains("Snack", ignoreCase = true) -> Icons.Outlined.BakeryDining
                else -> Icons.Outlined.Restaurant
            }
            Triple(count, hasAvailable, icon)
        }
    }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            val stats = categoryStats[category] ?: Triple(0, true, Icons.Outlined.Restaurant)
            val (count, hasAvailable, icon) = stats
            val statusLabel = if (hasAvailable) "Available" else "Habis"

            CategoryBannerCard(
                title = category,
                subtitle = "$count items",
                icon = icon,
                statusLabel = statusLabel,
                isAvailable = hasAvailable,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.width(172.dp)
            )
        }
    }
}

/**
 * Kartu Banner Kategori Satuan: Bersih, Mewah, Bebas Border Hitam Kaku
 */
@Composable
private fun CategoryBannerCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusLabel: String,
    isAvailable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    // Ketika aktif: Latar Deep Forest Artisan Green (#0A3D24) dengan teks dan watermark putih murni
    val targetBg = if (isSelected) colors.accent else Color.White
    val targetContent = if (isSelected) Color.White else colors.ink
    val targetSubtext = if (isSelected) Color(0xFFE5E7EB) else colors.inkMuted

    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 180),
        label = "cat_card_bg"
    )

    Surface(
        modifier = modifier
            .height(115.dp)
            .shadow(
                elevation = if (isSelected) 6.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = colors.ink.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = animatedBg
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            // Watermark Ikon Artistik di Kanan Belakang (Clipped & Semi-transparan)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White.copy(alpha = 0.16f) else colors.ink.copy(alpha = 0.06f),
                modifier = Modifier
                    .size(68.dp)
                    .align(Alignment.BottomEnd)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Kiri Atas: Badge Status Pil (Available)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(if (isSelected) Color.White.copy(alpha = 0.15f) else colors.surfaceAlt)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = targetContent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Kiri Bawah: Judul Kategori & Jumlah Item
                Column {
                    Text(
                        text = title,
                        color = targetContent,
                        style = A3Theme.typography.title.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = targetSubtext,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Kartu Produk Menu: Nama Mandiri 2 Baris (Anti-Potong), Harga & Tombol (+) di Bawah
 */
@Composable
private fun ProductGridItem(
    product: CafeProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val isSoldOut = product.soldOut

    val categoryIcon: ImageVector = remember(product.category) {
        when {
            product.category.contains("Kopi", ignoreCase = true) -> Icons.Outlined.LocalCafe
            product.category.contains("Roti", ignoreCase = true) || product.category.contains("Makanan", ignoreCase = true) -> Icons.Outlined.BakeryDining
            else -> Icons.Outlined.EmojiFoodBeverage
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(186.dp)
            .alpha(if (isSoldOut) 0.45f else 1.0f)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = colors.ink.copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, colors.ink, RoundedCornerShape(16.dp))
                .clickable(enabled = !isSoldOut, onClick = onClick)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Area Gambar Bersih Terisolasi (Top Area)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(38.dp)
                    )

                    if (isSoldOut) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .border(0.5.dp, colors.ink, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Habis",
                                color = colors.ink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (product.options.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Kustom",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Area Bawah: Nama Produk di Baris Mandiri (Lebar Penuh 100%), Harga & Tombol (+) di Bawahnya
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = product.name,
                        color = colors.ink,
                        style = A3Theme.typography.body.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 17.sp
                        ),
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        A3MoneyText(
                            amount = product.basePrice,
                            style = A3Theme.typography.bodySmall.copy(
                                color = colors.inkMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Start
                        )

                        // Tombol Bulat Tambah (+) 34dp x 34dp
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, if (isSoldOut) colors.inkMuted.copy(alpha = 0.5f) else colors.accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = if (isSoldOut) "Menu Habis" else "Tambah",
                                tint = if (isSoldOut) colors.inkMuted.copy(alpha = 0.5f) else colors.accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel Sisi Kanan: Struk Pesanan Melayang Mandiri (Purchase Receipt Drawer 24dp — Bebas Border Hitam Kaku)
 */
@Composable
internal fun PurchaseReceiptCard(
    orderHeader: CafeOrderHeader,
    cartLines: List<CafeCartLine>,
    calculation: CafeCalculationResult,
    onCollapseClick: () -> Unit,
    onOrderTypeSelected: (CafeOrderType) -> Unit,
    onCustomerNameChanged: (String) -> Unit,
    onTableSelected: (String) -> Unit,
    onQuantityChange: (lineId: String, delta: Int) -> Unit,
    onRemoveLine: (lineId: String) -> Unit,
    onClearCart: () -> Unit,
    onCheckoutClick: () -> Unit,
    onSendToKitchenClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCheckingOut: Boolean = false,
    appliedDiscount: AppliedDiscount? = null,
    onOpenDiscountDialog: () -> Unit = {},
    onRemoveDiscount: () -> Unit = {},
    onOpenMoveTableDialog: () -> Unit = {}
) {
    val colors = A3Theme.colors

    // Kartu Struk Melayang Bebas Border Hitam Kaku dengan Elevasi 8dp
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==================== SEKSI ATAS: HEADER & TIPE PESANAN ====================
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. Header Struk: Tombol Bulat Tutup (>), Purchase Receipt & ID, Tombol Hapus/Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Bulat Tutup / Collapse (>)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .clickable(onClick = onCollapseClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Tutup Struk",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Purchase Receipt",
                            color = colors.ink,
                            style = A3Theme.typography.title.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "#${orderHeader.receiptId}",
                            color = colors.inkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (cartLines.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(0.5.dp, Color(0xFFD1D5DB), CircleShape)
                                .clickable(onClick = onClearCart),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Clear",
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(38.dp))
                    }
                }

                // 2. Segmented Pill Control 3 Opsi: [Dine In] [Take Away] [Order Online]
                OrderTypeSegmentedPills(
                    selectedType = orderHeader.orderType,
                    onTypeSelected = onOrderTypeSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Input Dua Kolom: Customer Name & Table Dropdown
                CustomerAndTableInputs(
                    customerName = orderHeader.customerName,
                    onCustomerNameChanged = onCustomerNameChanged,
                    tableLabel = orderHeader.tableOrQueueLabel,
                    onTableSelected = onTableSelected,
                    onOpenMoveTableDialog = onOpenMoveTableDialog,
                    isDineIn = orderHeader.isDineIn,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Order list",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // ==================== SEKSI TENGAH: DAFTAR PESANAN BERBINGKAI ====================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, colors.ink, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                if (cartLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sentuh menu di kiri untuk menambahkan item ke struk ini.",
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cartLines, key = { it.id }) { line ->
                            ReceiptItemRow(
                                line = line,
                                onQuantityChange = { delta -> onQuantityChange(line.id, delta) }
                            )
                        }
                    }
                }
            }

            // ==================== SEKSI BAWAH: KARTU DISKON & RINCIAN PEMBAYARAN ====================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Kartu Diskon Interaktif Taktil (C06 Dialog Trigger)
                if (calculation.discount > 0L) {
                    AppliedDiscountCard(
                        appliedDiscount = appliedDiscount,
                        discountAmount = calculation.discount,
                        onOpenDiscountDialog = onOpenDiscountDialog,
                        onRemoveDiscount = onRemoveDiscount
                    )
                } else if (cartLines.isNotEmpty()) {
                    AddDiscountActionCard(
                        onOpenDiscountDialog = onOpenDiscountDialog
                    )
                }

                Text(
                    text = "Payment Details",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PaymentRow(label = "Subtotal", amount = calculation.subtotal)

                    if (calculation.discount > 0L) {
                        PaymentRow(label = "Diskon", amount = -calculation.discount, isNegative = true)
                    }
                    if (calculation.serviceCharge > 0L) {
                        PaymentRow(label = "Biaya Layanan", amount = calculation.serviceCharge)
                    }
                    if (calculation.tax > 0L) {
                        PaymentRow(label = "Pajak", amount = calculation.tax)
                    }
                    if (calculation.rounding != 0L) {
                        PaymentRow(label = "Pembulatan", amount = calculation.rounding)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color(0xFFE5E7EB))
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // BARIS TOTAL BESAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total",
                            color = colors.ink,
                            style = A3Theme.typography.title.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        A3MoneyText(
                            amount = calculation.total,
                            style = A3Theme.typography.title.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                        )
                    }
                }

                // SOLUSI B: TOMBOL GESER ADAPTIF (A3SwipeButton)
                val swipeActionText = when (orderHeader.orderType) {
                    CafeOrderType.DINE_IN -> "Geser: Kirim ke Dapur"
                    CafeOrderType.TAKE_AWAY -> "Geser: Bayar ${formatRupiah(calculation.total)}"
                    CafeOrderType.ORDER_ONLINE -> "Geser: Proses Pesanan Ojol"
                }

                A3SwipeButton(
                    text = swipeActionText,
                    onSwipeComplete = {
                        if (orderHeader.orderType == CafeOrderType.DINE_IN) {
                            onSendToKitchenClick()
                        } else {
                            onCheckoutClick()
                        }
                    },
                    enabled = cartLines.isNotEmpty(),
                    backgroundColor = colors.accent,
                    iconColor = colors.accent,
                    modifier = Modifier.fillMaxWidth()
                )

                // Pintasan untuk tamu Dine-in yang ingin langsung melunasi di awal
                if (orderHeader.orderType == CafeOrderType.DINE_IN && cartLines.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .clickable(enabled = !isCheckingOut, onClick = onCheckoutClick)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isCheckingOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Menyiapkan Pembayaran...",
                                    color = colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Langsung Bayar di Kasir",
                                    color = colors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Segmented Control 3 Opsi Pil Penuh: [Dine In] [Take Away] [Order Online] (sesuai contoh.jpeg)
 */
@Composable
private fun OrderTypeSegmentedPills(
    selectedType: CafeOrderType,
    onTypeSelected: (CafeOrderType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(9999.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(9999.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CafeOrderType.values().forEach { type ->
                val isSelected = selectedType == type

                val targetBg = if (isSelected) colors.accent else Color.Transparent
                val targetText = if (isSelected) Color.White else colors.ink

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(targetBg)
                        .clickable { onTypeSelected(type) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type.label,
                        color = targetText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Input Dua Kolom Pil: Customer Name & Table Dropdown (sesuai contoh.jpeg)
 */
@Composable
private fun CustomerAndTableInputs(
    customerName: String,
    onCustomerNameChanged: (String) -> Unit,
    tableLabel: String,
    onTableSelected: (String) -> Unit,
    onOpenMoveTableDialog: () -> Unit,
    isDineIn: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kolom 1: Customer Name
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = "Customer name",
                color = colors.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(9999.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = customerName,
                    onValueChange = onCustomerNameChanged,
                    singleLine = true,
                    textStyle = A3Theme.typography.bodySmall.copy(
                        color = colors.ink,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (customerName.isEmpty()) {
                            Text(
                                text = "Nama pelanggan",
                                color = colors.inkMuted,
                                fontSize = 12.sp
                            )
                        }
                        inner()
                    }
                )
            }
        }

        // Kolom 2: Table / Keterangan
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isDineIn) "Table (Pindah Meja)" else "Keterangan",
                color = colors.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(9999.dp))
                    .clickable {
                        if (isDineIn) {
                            onOpenMoveTableDialog()
                        }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tableLabel,
                        color = colors.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isDineIn) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = "Pindah Meja",
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Baris Item Struk: Rincian Opsi Varian, Add-on, dan Catatan Terurai Rapi (Tidak Terpotong!)
 */
@Composable
private fun ReceiptItemRow(
    line: CafeCartLine,
    onQuantityChange: (delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    val categoryIcon: ImageVector = when {
        line.product.category.contains("Kopi", ignoreCase = true) -> Icons.Outlined.LocalCafe
        line.product.category.contains("Roti", ignoreCase = true) || line.product.category.contains("Makanan", ignoreCase = true) -> Icons.Outlined.BakeryDining
        else -> Icons.Outlined.EmojiFoodBeverage
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceAlt.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail Mini Bulat
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Detail Menu, Varian, Addon, & Stepper
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Baris 1: Nama Menu & Total Baris
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line.product.name,
                        color = colors.ink,
                        style = A3Theme.typography.body.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    A3MoneyText(
                        amount = line.lineTotal,
                        style = A3Theme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            fontSize = 13.5.sp
                        )
                    )
                }

                // Baris 2: Harga Satuan x Qty & Stepper Kuantitas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@ ${formatRupiah(line.unitPrice)} × ${line.quantity}",
                        color = colors.inkMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Stepper Kuantitas Berbentuk Kapsul Pil
                    Row(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { onQuantityChange(-1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = "Kurang",
                                tint = colors.ink,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Text(
                            text = line.quantity.toString(),
                            color = colors.ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { onQuantityChange(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "Tambah",
                                tint = colors.ink,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Baris 3: Rincian Opsi Varian & Addon (Terurai Rapi, Tidak Terpotong!)
                if (line.selectedVariant != null) {
                    Text(
                        text = "• Varian: ${line.selectedVariant.name}",
                        color = colors.inkMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                line.selectedAddons.forEach { addon ->
                    val deltaStr = if (addon.priceDelta > 0) " (+${formatRupiah(addon.priceDelta)})" else ""
                    Text(
                        text = "+ ${addon.name}$deltaStr",
                        color = colors.accent,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Baris 4: Catatan Khusus Barista
                if (line.note.isNotBlank()) {
                    Text(
                        text = "📄 ${line.note}",
                        color = colors.ink,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Baris Rincian Pembayaran
 */
@Composable
private fun PaymentRow(
    label: String,
    amount: Long,
    isNegative: Boolean = false
) {
    val colors = A3Theme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.inkMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )

        val formatted = if (isNegative) "-${formatRupiah(Math.abs(amount))}" else formatRupiah(amount)
        Text(
            text = formatted,
            color = colors.ink,
            style = A3Theme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

/**
 * Kartu Ajakan Tambah Diskon (Saat keranjang terisi dan belum ada diskon terpasang)
 */
@Composable
private fun AddDiscountActionCard(
    onOpenDiscountDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.6f))
            .border(0.8.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenDiscountDialog)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Percent,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = "Diskon / Kupon",
                    color = colors.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .border(0.8.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Tambah",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Kartu Voucher / Diskon Aktif (Menampilkan nama, potongan, tombol ubah, dan tombol cepat hapus ✕)
 */
@Composable
private fun AppliedDiscountCard(
    appliedDiscount: AppliedDiscount?,
    discountAmount: Long,
    onOpenDiscountDialog: () -> Unit,
    onRemoveDiscount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFEF2F2))
            .border(0.8.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenDiscountDialog)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Percent,
                        contentDescription = null,
                        tint = colors.status.danger.text,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = appliedDiscount?.label ?: "Diskon",
                            color = colors.status.danger.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .border(0.5.dp, Color(0xFFFECACA), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Ubah",
                                color = colors.status.danger.text,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!appliedDiscount?.reason.isNullOrBlank()) {
                        Text(
                            text = appliedDiscount?.reason ?: "",
                            color = colors.inkMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "-${formatRupiah(discountAmount)}",
                    color = colors.status.danger.text,
                    style = A3Theme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    )
                )

                // Tombol Hapus Cepat (✕)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(0.8.dp, Color(0xFFFCA5A5), CircleShape)
                        .clickable(onClick = onRemoveDiscount),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Hapus Diskon",
                        tint = colors.status.danger.text,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
