package id.a3.feature.laundry.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.A3StatusChip
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.StatusToken
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import kotlinx.coroutines.launch

/**
 * Layar Order Baru Laundry (L03) - Athia Laundry House.
 * Pola split-screen 60/40 untuk tablet landscape 11 inci:
 * - Kiri (60%): Kartu Pelanggan (L02), Katalog Layanan, Form Konfigurasi (kg/pcs, express, catatan, estimasi).
 * - Kanan (40%): Panel Struk Pesanan, breakdown rincian, pilihan bayar (LDR-05), dan tombol aksi utama.
 */
@Composable
fun LaundryOrderScreen(
    uiState: LaundryOrderUiState,
    viewModel: LaundryOrderViewModel,
    onProceedToPayment: (orderId: String, totalAmount: Long, displayNumber: String) -> Unit,
    onOrderSavedSuccessfully: (displayNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SISI KIRI (60%): Input Pelanggan & Layanan
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Kartu Pelanggan (L02)
                CustomerCardHeader(
                    customer = uiState.selectedCustomer,
                    isNewCustomer = uiState.isNewCustomer,
                    onOpenCustomerDialog = { viewModel.setCustomerDialogOpen(true) },
                    onClearCustomer = { viewModel.clearCustomer() }
                )

                // 2. Kategori Filter Layanan (Segmented Tab 44dp)
                CategoryFilterTabs(
                    activeFilter = uiState.categoryFilter,
                    onFilterSelected = viewModel::setCategoryFilter
                )

                // 3. Carousel Kartu Layanan Tersedia
                ServiceCatalogRow(
                    services = uiState.filteredServices,
                    selectedService = uiState.selectedServiceForInput,
                    onServiceSelected = viewModel::selectServiceForInput
                )

                // 4. Form Konfigurasi Layanan Terpilih
                val currentService = uiState.selectedServiceForInput
                if (currentService != null) {
                    ServiceConfigurationCard(
                        service = currentService,
                        inputWeightKg = uiState.inputWeightKg,
                        inputPieces = uiState.inputPieces,
                        isExpress = uiState.inputIsExpress,
                        note = uiState.inputNote,
                        estimatedEtaText = uiState.estimatedCompletionText,
                        onWeightChange = viewModel::setInputWeight,
                        onWeightAdjust = viewModel::adjustInputWeight,
                        onPiecesAdjust = viewModel::adjustInputPieces,
                        onToggleExpress = viewModel::toggleInputExpress,
                        onNoteChange = viewModel::setInputNote,
                        onAddLine = viewModel::addConfiguredServiceToLines
                    )
                }
            }

            // SISI KANAN (40%): Panel Struk & Aksi Pesanan
            Box(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight()
                    .clip(shapes.cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, shapes.cardShape)
                    .padding(20.dp)
            ) {
                OrderReceiptPanel(
                    uiState = uiState,
                    viewModel = viewModel,
                    onProceedToPayment = onProceedToPayment,
                    onOrderSavedSuccessfully = onOrderSavedSuccessfully
                )
            }
        }
    }

    // Dialog L02: Pemilihan / Pembuatan Pelanggan
    CustomerSelectionDialog(
        isOpen = uiState.isCustomerDialogOpen,
        onDismissRequest = { viewModel.setCustomerDialogOpen(false) },
        searchQuery = uiState.customerSearchQuery,
        onSearchQueryChange = viewModel::onCustomerSearchQueryChange,
        searchResults = uiState.customerSearchResults,
        onSelectCustomer = viewModel::selectCustomer,
        onCreateNewCustomer = viewModel::createAndSelectNewCustomer
    )
}

@Composable
private fun CustomerCardHeader(
    customer: CustomerEntity?,
    isNewCustomer: Boolean,
    onOpenCustomerDialog: () -> Unit,
    onClearCustomer: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, shapes.cardShape)
            .padding(16.dp)
    ) {
        if (customer == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.canvas),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pilih Pelanggan",
                            style = A3Theme.typography.title,
                            color = colors.ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wajib menyertakan nama & HP pelanggan (LDR-01)",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }

                A3Button(
                    label = "+ Pilih / Buat Pelanggan",
                    variant = ButtonVariant.PRIMARY,
                    onClick = onOpenCustomerDialog
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                            color = colors.accent,
                            style = A3Theme.typography.title,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = customer.name,
                                style = A3Theme.typography.title,
                                color = colors.ink,
                                fontWeight = FontWeight.Bold
                            )
                            A3StatusChip(
                                label = if (isNewCustomer) "Pelanggan Baru" else "Terdaftar",
                                token = if (isNewCustomer) StatusToken.WARNING else StatusToken.SUCCESS
                            )
                        }
                        Text(
                            text = PhoneNumberUtil.formatDisplay(customer.phone),
                            style = A3Theme.typography.body,
                            color = colors.inkMuted
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    A3Button(
                        label = "Ganti",
                        variant = ButtonVariant.SECONDARY,
                        onClick = onOpenCustomerDialog,
                        modifier = Modifier.height(44.dp)
                    )
                    IconButton(
                        onClick = onClearCustomer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus Pelanggan",
                            tint = colors.inkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterTabs(
    activeFilter: LaundryCategoryFilter,
    onFilterSelected: (LaundryCategoryFilter) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shapes.buttonShape)
            .background(colors.surface)
            .border(1.dp, colors.border, shapes.buttonShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val filters = listOf(
            LaundryCategoryFilter.ALL to "Semua Layanan",
            LaundryCategoryFilter.KILOAN to "Kiloan (Kg)",
            LaundryCategoryFilter.SATUAN to "Satuan (Pcs)"
        )

        filters.forEach { (filter, label) ->
            val isActive = activeFilter == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shapes.buttonShape)
                    .background(if (isActive) colors.accent else Color.Transparent)
                    .clickable { onFilterSelected(filter) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = A3Theme.typography.button,
                    color = if (isActive) colors.onAccent else colors.inkMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ServiceCatalogRow(
    services: List<LaundryServiceEntity>,
    selectedService: LaundryServiceEntity?,
    onServiceSelected: (LaundryServiceEntity) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(services, key = { it.id }) { service ->
            val isSelected = selectedService?.id == service.id
            val isKiloan = service.unit.lowercase() == "kg"

            Box(
                modifier = Modifier
                    .width(180.dp)
                    .clip(shapes.cardShape)
                    .background(if (isSelected) colors.surfaceAlt else colors.surface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) colors.accent else colors.border,
                        shape = shapes.cardShape
                    )
                    .clickable { onServiceSelected(service) }
                    .padding(14.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isKiloan) "KILOAN" else "SATUAN",
                            style = A3Theme.typography.label,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = service.name,
                        style = A3Theme.typography.body,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${formatRupiah(service.rate)} / ${service.unit}",
                        style = A3Theme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted
                    )

                    if (service.expressRate > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = colors.status.warning.text,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Express ${formatRupiah(service.expressRate)}",
                                style = A3Theme.typography.bodySmall,
                                color = colors.status.warning.text,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceConfigurationCard(
    service: LaundryServiceEntity,
    inputWeightKg: Double,
    inputPieces: Int,
    isExpress: Boolean,
    note: String,
    estimatedEtaText: String,
    onWeightChange: (Double) -> Unit,
    onWeightAdjust: (Double) -> Unit,
    onPiecesAdjust: (Int) -> Unit,
    onToggleExpress: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddLine: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val isKiloan = service.unit.lowercase() == "kg"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.cardShape)
            .background(colors.surface)
            .border(1.dp, colors.border, shapes.cardShape)
            .padding(18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Konfigurasi: ${service.name}",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = if (isKiloan) {
                            "Min. ${service.minWeight} kg · Kelipatan ${service.weightStep} kg"
                        } else {
                            "Dihitung bersama pelanggan per potong pakaian (LDR-03)"
                        },
                        style = A3Theme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }

                // Estimasi Selesai Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(shapes.buttonShape)
                        .background(colors.canvas)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = estimatedEtaText,
                        style = A3Theme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                }
            }

            HorizontalDivider(color = colors.border, thickness = 1.dp)

            if (isKiloan) {
                // Stepper Berat (Kg)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Berat Cucian (Kg)",
                        style = A3Theme.typography.label,
                        color = colors.ink
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tombol Kurang 0.5 kg (44dp touch target)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { onWeightAdjust(-0.5) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Kurang 0.5 kg", tint = colors.ink)
                        }

                        // Display Berat Besar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(shapes.inputShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, shapes.inputShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", inputWeightKg)} kg",
                                style = A3Theme.typography.headline,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                        }

                        // Tombol Tambah 0.5 kg (44dp touch target)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { onWeightAdjust(0.5) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah 0.5 kg", tint = colors.ink)
                        }
                    }

                    // Quick Chips Berat
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1.0, 2.0, 3.0, 5.0, 7.0).forEach { weight ->
                            Box(
                                modifier = Modifier
                                    .clip(shapes.buttonShape)
                                    .background(if (inputWeightKg == weight) colors.accent else colors.canvas)
                                    .border(1.dp, colors.border, shapes.buttonShape)
                                    .clickable { onWeightChange(weight) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${weight.toInt()} kg",
                                    style = A3Theme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (inputWeightKg == weight) colors.onAccent else colors.ink
                                )
                            }
                        }
                    }
                }
            } else {
                // Stepper Jumlah Pcs (LDR-03)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Jumlah Potong / Pcs",
                        style = A3Theme.typography.label,
                        color = colors.ink
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { onPiecesAdjust(-1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Kurang 1 pcs", tint = colors.ink)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(shapes.inputShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, shapes.inputShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$inputPieces pcs",
                                style = A3Theme.typography.headline,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable { onPiecesAdjust(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah 1 pcs", tint = colors.ink)
                        }
                    }
                }
            }

            // Saklar Express Toggle (LDR-04)
            if (service.expressRate > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shapes.cardShape)
                        .background(if (isExpress) colors.surfaceAlt else colors.canvas)
                        .border(1.dp, if (isExpress) colors.accent else colors.border, shapes.cardShape)
                        .clickable { onToggleExpress() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isExpress) colors.accent else colors.inkMuted
                        )
                        Column {
                            Text(
                                text = "Layanan Express (6 Jam)",
                                style = A3Theme.typography.body,
                                fontWeight = FontWeight.Bold,
                                color = colors.ink
                            )
                            Text(
                                text = "Tarif express: ${formatRupiah(service.expressRate)} / ${service.unit}",
                                style = A3Theme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Switch(
                        checked = isExpress,
                        onCheckedChange = { onToggleExpress() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent
                        )
                    )
                }
            }

            // Kolom Catatan Khusus (Noda / Barang Rapuh LDR-03)
            A3TextField(
                value = note,
                onValueChange = onNoteChange,
                label = "Catatan Khusus (Noda / Kerusakan / Instruksi)",
                placeholder = "cth. Noda minyak di kerah, kancing rapuh..."
            )

            // Tombol Tambah ke Pesanan
            A3Button(
                label = "+ Tambahkan ke Pesanan",
                variant = ButtonVariant.PRIMARY,
                onClick = onAddLine,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OrderReceiptPanel(
    uiState: LaundryOrderUiState,
    viewModel: LaundryOrderViewModel,
    onProceedToPayment: (orderId: String, totalAmount: Long, displayNumber: String) -> Unit,
    onOrderSavedSuccessfully: (displayNumber: String) -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Struk
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalLaundryService,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Struk Pesanan",
                        style = A3Theme.typography.title,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                }

                Text(
                    text = "${uiState.lines.size} Item",
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }

            HorizontalDivider(color = colors.border, thickness = 1.dp)

            // Daftar Baris Pesanan
            if (uiState.lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Keranjang Masih Kosong",
                            style = A3Theme.typography.title,
                            color = colors.inkMuted
                        )
                        Text(
                            text = "Pilih layanan di sebelah kiri untuk menambahkan item",
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.lines) { index, line ->
                        val billedWeight = uiState.calculation.billedWeights.getOrNull(index) ?: line.weightKg
                        val lineTotal = uiState.calculation.lineTotals.getOrNull(index) ?: 0L
                        val isPcs = line.service.unit.lowercase() == "pcs"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.cardShape)
                                .background(colors.canvas)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = line.service.name,
                                        style = A3Theme.typography.body,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.ink
                                    )
                                    if (line.isExpress) {
                                        A3StatusChip(label = "Express", token = StatusToken.WARNING)
                                    }
                                }

                                Text(
                                    text = if (isPcs) {
                                        "${line.pieces} pcs @ ${formatRupiah(line.service.rate)}"
                                    } else {
                                        "${String.format(java.util.Locale.US, "%.1f", line.weightKg)} kg → dibulatkan ${String.format(java.util.Locale.US, "%.1f", billedWeight)} kg"
                                    },
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.inkMuted
                                )

                                if (line.note.isNotBlank()) {
                                    Text(
                                        text = "Catatan: ${line.note}",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.ink,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatRupiah(lineTotal),
                                    style = A3Theme.typography.body,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.ink
                                )
                                IconButton(
                                    onClick = { viewModel.removeLine(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus item",
                                        tint = colors.status.danger.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bagian Bawah: Kalkulasi & Pembayaran
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HorizontalDivider(color = colors.border, thickness = 1.dp)

            // Total Biaya
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Biaya",
                    style = A3Theme.typography.title,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                A3MoneyText(
                    amount = uiState.calculation.total,
                    style = A3Theme.typography.headline.copy(fontSize = 28.sp)
                )
            }

            // Segmented Pills Opsi Pembayaran (LDR-05)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Opsi Pembayaran (LDR-05)",
                    style = A3Theme.typography.label,
                    color = colors.ink
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(shapes.buttonShape)
                        .background(colors.canvas)
                        .border(1.dp, colors.border, shapes.buttonShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = listOf(
                        LaundryPaymentOption.PAY_ON_PICKUP to "Bayar saat Ambil",
                        LaundryPaymentOption.PAID_UPFRONT to "Lunas di Muka",
                        LaundryPaymentOption.DP to "Uang Muka (DP)"
                    )

                    options.forEach { (option, label) ->
                        val isSelected = uiState.paymentOption == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(shapes.buttonShape)
                                .background(if (isSelected) colors.accent else Color.Transparent)
                                .clickable { viewModel.setPaymentOption(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = A3Theme.typography.button,
                                color = if (isSelected) colors.onAccent else colors.inkMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Tombol Aksi Utama
            val isReady = uiState.isFormReadyToSubmit
            val isPayLater = uiState.paymentOption == LaundryPaymentOption.PAY_ON_PICKUP

            A3Button(
                label = when {
                    uiState.selectedCustomer == null -> "Pilih Pelanggan Terlebih Dahulu"
                    uiState.lines.isEmpty() -> "Tambahkan Layanan Terlebih Dahulu"
                    isPayLater -> "Simpan Order (Bayar saat Ambil)"
                    uiState.paymentOption == LaundryPaymentOption.PAID_UPFRONT -> "Lanjut ke Pembayaran Lunas"
                    else -> "Lanjut ke Pembayaran DP"
                },
                variant = ButtonVariant.PRIMARY,
                enabled = isReady,
                onClick = {
                    val displayNumber = "L1-0001" // Standar format nomor laundry offline
                    coroutineScope.launch {
                        val result = viewModel.submitOrder(displayNumber)
                        if (result.isSuccess) {
                            val order = result.getOrNull()!!
                            if (isPayLater) {
                                onOrderSavedSuccessfully(order.displayNumber)
                            } else {
                                onProceedToPayment(order.id, order.total, order.displayNumber)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
