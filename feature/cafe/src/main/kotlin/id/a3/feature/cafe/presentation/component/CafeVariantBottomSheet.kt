package id.a3.feature.cafe.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.model.CafeCartLine
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.model.MenuOptionItem

/**
 * Layar C02: Modal Bottom Sheet untuk memilih Varian, Add-on, Catatan, dan Kuantitas (Cafe).
 * Mematuhi panduan desain Quiet Luxury Industrial:
 * - Sudut tegas 4 dp (bukan pil).
 * - Opsi terpilih ditandai dengan latar Concrete Greige dan batas garis Obsidian Black.
 * - Tombol aksi utama Espresso Amber 56 dp dengan perhitungan harga langsung real-time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CafeVariantBottomSheet(
    product: CafeProduct,
    onDismissRequest: () -> Unit,
    onAddToCart: (CafeCartLine) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // State varian (default varian pertama jika ada)
    var selectedVariant by remember { mutableStateOf(product.variants.firstOrNull()) }

    // State add-ons (multi-selection)
    val selectedAddons = remember { mutableStateOf(setOf<MenuOptionItem>()) }

    // State catatan dan kuantitas
    var note by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }

    // Kalkulasi harga satuan real-time
    val unitPrice = product.basePrice +
        (selectedVariant?.priceDelta ?: 0L) +
        selectedAddons.value.sumOf { it.priceDelta }

    val lineTotal = unitPrice * quantity.toLong()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surface,
        contentColor = colors.ink,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Judul Produk, Kategori, Harga Dasar, dan Tombol Tutup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.category.uppercase(),
                        color = colors.inkMuted,
                        style = A3Theme.typography.label,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = product.name,
                        color = colors.ink,
                        style = A3Theme.typography.headline,
                        fontWeight = FontWeight.Bold
                    )
                    A3MoneyText(
                        amount = product.basePrice,
                        style = A3Theme.typography.title.copy(color = colors.accent)
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(shapes.buttonShape)
                        .background(colors.surfaceAlt)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Tutup",
                        tint = colors.ink
                    )
                }
            }

            // Bagian 1: Pilihan Varian (Tunggal / Single Choice)
            if (product.variants.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PILIHAN VARIAN",
                        color = colors.ink,
                        style = A3Theme.typography.label.copy(fontWeight = FontWeight.Bold)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        product.variants.forEach { variant ->
                            val isSelected = selectedVariant == variant
                            OptionChoiceCard(
                                title = variant.name,
                                priceDelta = variant.priceDelta,
                                isSelected = isSelected,
                                onClick = { selectedVariant = variant }
                            )
                        }
                    }
                }
            }

            // Bagian 2: Pilihan Tambahan / Add-on (Bisa Lebih dari Satu / Multi Choice)
            if (product.addons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TAMBAHAN / ADD-ON",
                        color = colors.ink,
                        style = A3Theme.typography.label.copy(fontWeight = FontWeight.Bold)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        product.addons.forEach { addon ->
                            val isSelected = selectedAddons.value.contains(addon)
                            OptionChoiceCard(
                                title = addon.name,
                                priceDelta = addon.priceDelta,
                                isSelected = isSelected,
                                onClick = {
                                    val current = selectedAddons.value.toMutableSet()
                                    if (isSelected) {
                                        current.remove(addon)
                                    } else {
                                        current.add(addon)
                                    }
                                    selectedAddons.value = current
                                }
                            )
                        }
                    }
                }
            }

            // Bagian 3: Catatan Khusus
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                A3TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Catatan Khusus Barista / Dapur",
                    placeholder = "Misal: Sedikit es (less ice), pisah gula",
                    singleLine = true
                )
            }

            // Bagian 4: Pengatur Jumlah (Stepper) & Tombol Tambah ke Keranjang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stepper Kuantitas
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(shapes.buttonShape)
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.border, shapes.buttonShape)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Remove,
                            contentDescription = "Kurangi",
                            tint = if (quantity > 1) colors.ink else colors.inkMuted
                        )
                    }

                    Text(
                        text = quantity.toString(),
                        color = colors.ink,
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 28.dp),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = { quantity++ },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Tambah",
                            tint = colors.ink
                        )
                    }
                }

                // Tombol Aksi Utama: Tambah ke Keranjang dengan Total Harga
                A3Button(
                    label = "Tambah ke Pesanan — ${id.a3.core.ui.component.formatRupiah(lineTotal)}",
                    onClick = {
                        val cartLine = CafeCartLine(
                            product = product,
                            selectedVariant = selectedVariant,
                            selectedAddons = selectedAddons.value.toList(),
                            note = note.trim(),
                            quantity = quantity
                        )
                        onAddToCart(cartLine)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OptionChoiceCard(
    title: String,
    priceDelta: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val bg = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surface
    val borderCol = if (isSelected) colors.accent else Color(0xFFE5E7EB)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val textCol = if (isSelected) colors.accent else colors.ink
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Box(
        modifier = Modifier
            .clip(shapes.buttonShape)
            .background(bg)
            .border(borderWidth, borderCol, shapes.buttonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = textCol,
                style = A3Theme.typography.bodySmall.copy(fontWeight = fontWeight)
            )

            if (priceDelta > 0L) {
                Text(
                    text = "+${id.a3.core.ui.component.formatRupiah(priceDelta)}",
                    color = if (isSelected) colors.accent else colors.inkMuted,
                    style = A3Theme.typography.label.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
