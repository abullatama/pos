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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.theme.A3Theme

enum class CustomerDialogTab {
    SEARCH,
    CREATE
}

/**
 * Dialog L02: Pelanggan (Athia Laundry House).
 * Memungkinkan kasir mencari pelanggan terdaftar berdasarkan nama/nomor HP
 * atau mendaftarkan pelanggan baru untuk disertakan dalam bundel order (LDR-01).
 */
@Composable
fun CustomerSelectionDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<CustomerEntity>,
    onSelectCustomer: (customer: CustomerEntity, isNew: Boolean) -> Unit,
    onCreateNewCustomer: (name: String, phone: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Mencari pelanggan terdaftar dengan nomor ini; dipakai agar nomor yang sama tidak didaftarkan dua kali. */
    findCustomerByPhone: suspend (String) -> CustomerEntity? = { null }
) {
    if (!isOpen) return

    var activeTab by remember { mutableStateOf(CustomerDialogTab.SEARCH) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var phoneTouched by remember { mutableStateOf(false) }

    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val isPhoneValid = PhoneNumberUtil.isValid(newPhone)
    val isFormValid = newName.trim().isNotBlank() && isPhoneValid

    var existingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(newPhone, isPhoneValid) {
        existingCustomer = if (isPhoneValid) findCustomerByPhone(newPhone) else null
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .width(560.dp)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (activeTab == CustomerDialogTab.SEARCH) "Pilih Pelanggan" else "Pelanggan Baru",
                            style = A3Theme.typography.headline,
                            color = colors.ink
                        )
                        Text(
                            text = if (activeTab == CustomerDialogTab.SEARCH) {
                                "Cari nomor HP atau buat pelanggan baru untuk pesanan"
                            } else {
                                "Masukkan nama dan nomor HP pelanggan"
                            },
                            style = A3Theme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = colors.inkMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented Tab Switcher (44dp touch target)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(shapes.buttonShape)
                        .background(colors.canvas)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab Cari
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(shapes.buttonShape)
                            .background(if (activeTab == CustomerDialogTab.SEARCH) colors.accent else Color.Transparent)
                            .clickable { activeTab = CustomerDialogTab.SEARCH },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (activeTab == CustomerDialogTab.SEARCH) colors.onAccent else colors.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Cari Pelanggan",
                                style = A3Theme.typography.button,
                                color = if (activeTab == CustomerDialogTab.SEARCH) colors.onAccent else colors.inkMuted,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Tab Pelanggan Baru
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(shapes.buttonShape)
                            .background(if (activeTab == CustomerDialogTab.CREATE) colors.accent else Color.Transparent)
                            .clickable { activeTab = CustomerDialogTab.CREATE },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = if (activeTab == CustomerDialogTab.CREATE) colors.onAccent else colors.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pelanggan Baru",
                                style = A3Theme.typography.button,
                                color = if (activeTab == CustomerDialogTab.CREATE) colors.onAccent else colors.inkMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Konten Tab
                when (activeTab) {
                    CustomerDialogTab.SEARCH -> {
                        // Input Pencarian
                        A3TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = "Cari nama atau nomor HP (cth. 0812)...",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Daftar Hasil Pencarian / Pelanggan Terakhir
                        if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(shapes.cardShape)
                                    .background(colors.canvas),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = colors.inkMuted,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = if (searchQuery.isBlank()) "Belum ada riwayat pelanggan" else "Pelanggan tidak ditemukan",
                                        style = A3Theme.typography.title,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Tambahkan sebagai pelanggan baru",
                                        style = A3Theme.typography.bodySmall,
                                        color = colors.inkMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    A3Button(
                                        label = "+ Buat Pelanggan Baru",
                                        variant = ButtonVariant.SECONDARY,
                                        onClick = {
                                            if (searchQuery.isNotBlank() && searchQuery.all { it.isDigit() }) {
                                                newPhone = searchQuery
                                            }
                                            activeTab = CustomerDialogTab.CREATE
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults, key = { it.id }) { customer ->
                                    CustomerListItem(
                                        customer = customer,
                                        onSelect = { onSelectCustomer(customer, false) }
                                    )
                                }
                            }
                        }
                    }

                    CustomerDialogTab.CREATE -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            A3TextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = "Nama Pelanggan",
                                placeholder = "cth. Budi Santoso",
                                singleLine = true
                            )

                            A3TextField(
                                value = newPhone,
                                onValueChange = {
                                    newPhone = it
                                    phoneTouched = true
                                },
                                label = "Nomor HP / WhatsApp",
                                placeholder = "cth. 081234567890",
                                error = if (phoneTouched && newPhone.isNotBlank() && !isPhoneValid) {
                                    "Nomor HP harus diawali 08 (10–14 digit)"
                                } else null,
                                helper = if (!phoneTouched || newPhone.isBlank()) {
                                    "Nomor digunakan untuk nota dan notifikasi WhatsApp"
                                } else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )

                            existingCustomer?.let { found ->
                                Text(
                                    text = "Nomor ini sudah terdaftar atas nama ${found.name}. " +
                                        "Pakai pelanggan ini agar riwayat cuciannya tetap satu.",
                                    style = A3Theme.typography.bodySmall,
                                    color = colors.ink
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                A3Button(
                                    label = "Batal",
                                    variant = ButtonVariant.GHOST,
                                    onClick = { activeTab = CustomerDialogTab.SEARCH },
                                    modifier = Modifier.weight(1f)
                                )

                                A3Button(
                                    label = if (existingCustomer != null) "Pakai Pelanggan Ini" else "Gunakan Pelanggan Ini",
                                    variant = ButtonVariant.PRIMARY,
                                    enabled = isFormValid,
                                    onClick = {
                                        val found = existingCustomer
                                        if (found != null) {
                                            onSelectCustomer(found, false)
                                        } else {
                                            onCreateNewCustomer(newName, newPhone)
                                        }
                                    },
                                    modifier = Modifier.weight(2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerListItem(
    customer: CustomerEntity,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    val initial = customer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shapes.cardShape)
            .background(colors.canvas)
            .border(1.dp, colors.border, shapes.cardShape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Avatar inisial dengan Powder Rose (#FCE4EC) & Velvet Rose (#D81B60)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = colors.accent,
                    style = A3Theme.typography.title,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = customer.name,
                    style = A3Theme.typography.body,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink
                )
                Text(
                    text = PhoneNumberUtil.formatDisplay(customer.phone),
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted
                )
            }
        }

        A3Button(
            label = "Pilih",
            variant = ButtonVariant.PRIMARY,
            onClick = onSelect,
            modifier = Modifier.height(40.dp)
        )
    }
}
