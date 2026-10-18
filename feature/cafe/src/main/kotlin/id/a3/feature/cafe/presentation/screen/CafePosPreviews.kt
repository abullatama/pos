package id.a3.feature.cafe.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.a3.core.domain.cafe.CafeCalculationResult
import id.a3.core.ui.theme.CafeTheme
import id.a3.feature.cafe.model.CafeCartLine
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.model.MenuOptionItem
import id.a3.feature.cafe.model.OptionKind
import id.a3.feature.cafe.presentation.CafeOrderHeader
import id.a3.feature.cafe.presentation.component.CafeVariantBottomSheet
import id.a3.feature.cafe.repository.DefaultCafeMenuRepository

@Preview(name = "C01 - Layar Kasir POS Tablet 11\" (65/35 Split)", widthDp = 1138, heightDp = 711)
@Composable
fun CafePosScreenPreview() {
    val sampleProducts = DefaultCafeMenuRepository.INITIAL_MENU_CATALOG
    val sampleCart = listOf(
        CafeCartLine(
            product = sampleProducts[0], // Latte
            selectedVariant = sampleProducts[0].variants.find { it.name == "Dingin" },
            selectedAddons = listOf(sampleProducts[0].addons.first()),
            quantity = 1
        ),
        CafeCartLine(
            product = sampleProducts[8], // Croissant
            quantity = 2
        )
    )

    val sampleCalc = CafeCalculationResult(
        subtotal = 96000L,
        discount = 0L,
        serviceCharge = 0L,
        tax = 0L,
        rounding = 0L,
        total = 96000L
    )

    CafeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(id.a3.core.ui.theme.A3Theme.colors.canvas)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(0.64f)) {
                    MenuCatalogPanel(
                        allProducts = sampleProducts,
                        displayedProducts = sampleProducts,
                        categories = listOf("Semua", "Kopi", "Non-kopi", "Roti & Makanan"),
                        selectedCategory = "Semua",
                        searchQuery = "",
                        cartLines = sampleCart,
                        calculationTotal = sampleCalc.total,
                        isCartExpanded = true,
                        onExpandCartClick = {},
                        onCategorySelected = {},
                        onSearchQueryChanged = {},
                        onProductClick = {}
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    PurchaseReceiptCard(
                        orderHeader = CafeOrderHeader(
                            orderType = id.a3.feature.cafe.model.CafeOrderType.DINE_IN,
                            customerName = "Muadz",
                            tableOrQueueLabel = "B12 - Indoor",
                            receiptId = "27362"
                        ),
                        cartLines = sampleCart,
                        calculation = sampleCalc,
                        onCollapseClick = {},
                        onOrderTypeSelected = {},
                        onCustomerNameChanged = {},
                        onTableSelected = {},
                        onQuantityChange = { _, _ -> },
                        onRemoveLine = {},
                        onClearCart = {},
                        onCheckoutClick = {},
                        onSendToKitchenClick = {}
                    )
                }
            }
        }
    }
}

@Preview(name = "C02 - Bottom Sheet Varian & Add-on", widthDp = 1138, heightDp = 711)
@Composable
fun CafeVariantBottomSheetPreview() {
    val sampleProduct = CafeProduct(
        id = "menu-latte",
        name = "Caffe Latte",
        category = "Kopi",
        basePrice = 34000L,
        options = listOf(
            MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
            MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
            MenuOptionItem(kind = OptionKind.ADDON, name = "Shot Tambahan", priceDelta = 6000L),
            MenuOptionItem(kind = OptionKind.ADDON, name = "Susu Oat (Oat Milk)", priceDelta = 8000L)
        )
    )

    CafeTheme {
        CafeVariantBottomSheet(
            product = sampleProduct,
            onDismissRequest = {},
            onAddToCart = {}
        )
    }
}
