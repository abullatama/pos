package id.a3.feature.cafe.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.security.ApprovalResult
import id.a3.core.domain.security.failureMessage
import kotlinx.coroutines.launch
import id.a3.core.domain.money.MoneyMath
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3TextField
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.model.AppliedDiscount
import id.a3.feature.cafe.model.DiscountPreset
import id.a3.feature.cafe.model.DiscountReason
import kotlin.math.min

const val CAFE_DISCOUNT_DIALOG_TAG = "cafe_discount_dialog"
const val CAFE_DISCOUNT_APPLY_BTN_TAG = "cafe_discount_apply_btn"
const val CAFE_DISCOUNT_REMOVE_BTN_TAG = "cafe_discount_remove_btn"
const val CAFE_DISCOUNT_PIN_INPUT_TAG = "cafe_discount_pin_input"

private enum class DiscountDialogTab {
    PRESET,
    MANUAL
}

/**
 * Layar C06: Dialog Diskon & Potongan Harga (AA / A3 Coffeeshop).
 *
 * Desain Opsi A: Split-View 2-Kolom Kompak (Optimal untuk Tablet Landscape 11").
 * - Tab 1 (Preset): Diskon preset resmi (5%, 10%, Rp5.000) tanpa PIN (COR-01).
 * - Tab 2 (Manual): Input % atau Rp dengan alasan audit wajib dan otorisasi PIN Manager (COR-01, COR-07).
 * - Bebas istilah teknis internal (tanpa kebocoran kode spec seperti 'S14').
 */
@Composable
fun CafeDiscountDialog(
    subtotal: Long,
    currentDiscount: AppliedDiscount?,
    presets: List<DiscountPreset> = emptyList(),
    onDismissRequest: () -> Unit,
    onApplyDiscount: (AppliedDiscount) -> Unit,
    onRemoveDiscount: () -> Unit,
    onVerifyManagerPin: suspend (pin: String) -> ApprovalResult,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors
    val verifyScope = rememberCoroutineScope()

    var activeTab by remember(presets) {
        mutableStateOf(
            if (currentDiscount != null && !currentDiscount.isPreset) {
                DiscountDialogTab.MANUAL
            } else if (presets.isEmpty()) {
                DiscountDialogTab.MANUAL
            } else {
                DiscountDialogTab.PRESET
            }
        )
    }

    // State Tab Preset
    var selectedPreset by remember(presets, currentDiscount) {
        mutableStateOf(
            if (currentDiscount != null && currentDiscount.isPreset) {
                presets.find {
                    it.type == currentDiscount.discount.type && it.value == currentDiscount.discount.value
                } ?: presets.firstOrNull()
            } else {
                presets.firstOrNull()
            }
        )
    }

    // State Tab Manual
    var manualType by remember { mutableStateOf(currentDiscount?.discount?.type ?: DiscountType.PERCENT) }
    var manualValueInput by remember {
        mutableStateOf(
            if (currentDiscount != null && !currentDiscount.isPreset) currentDiscount.discount.value.toString() else "15"
        )
    }
    var selectedReason by remember {
        mutableStateOf(
            currentDiscount?.reason?.let { r -> DiscountReason.values().find { it.label == r } } ?: DiscountReason.SPECIAL_PROMO
        )
    }
    var customNote by remember { mutableStateOf(currentDiscount?.note ?: "") }
    var managerPinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifyingPin by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .widthIn(min = 640.dp, max = 700.dp)
                .testTag(CAFE_DISCOUNT_DIALOG_TAG),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Dialog: Judul, Subtotal & Tombol Tutup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Diskon & Potongan",
                            color = colors.ink,
                            style = A3Theme.typography.headline.copy(fontSize = 19.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Subtotal pesanan: ${formatRupiah(subtotal)}",
                            color = colors.inkMuted,
                            style = A3Theme.typography.bodySmall
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup",
                            tint = colors.ink,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Segmented Pill Tab: [ Diskon Preset (Kasir) ] vs [ Diskon Manual (PIN Manager) ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(colors.surfaceAlt)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        DiscountDialogTab.PRESET to "Diskon Preset (Kasir)",
                        DiscountDialogTab.MANUAL to "Diskon Manual (PIN Manager)"
                    )

                    tabs.forEach { (tab, label) ->
                        val isSelected = activeTab == tab
                        val bg = if (isSelected) Color.White else Color.Transparent
                        val textCol = if (isSelected) colors.ink else colors.inkMuted

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9999.dp))
                                .background(bg)
                                .clickable {
                                    activeTab = tab
                                    pinErrorMessage = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = textCol,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // BODY SPLIT-VIEW 2-KOLOM
                when (activeTab) {
                    DiscountDialogTab.PRESET -> {
                        PresetSplitView(
                            subtotal = subtotal,
                            presets = presets,
                            selectedPreset = selectedPreset,
                            onSelectPreset = { selectedPreset = it },
                            onSwitchToManual = { activeTab = DiscountDialogTab.MANUAL }
                        )
                    }
                    DiscountDialogTab.MANUAL -> {
                        ManualSplitView(
                            subtotal = subtotal,
                            manualType = manualType,
                            onTypeChanged = { manualType = it },
                            valueInput = manualValueInput,
                            onValueChanged = { manualValueInput = it.filter { ch -> ch.isDigit() } },
                            selectedReason = selectedReason,
                            onReasonSelected = { selectedReason = it },
                            customNote = customNote,
                            onNoteChanged = { customNote = it },
                            managerPin = managerPinInput,
                            onPinDigitClick = { if (managerPinInput.length < 4) managerPinInput += it },
                            onPinBackspace = { if (managerPinInput.isNotEmpty()) managerPinInput = managerPinInput.dropLast(1) },
                            onPinClear = { managerPinInput = "" },
                            errorMessage = pinErrorMessage
                        )
                    }
                }

                // FOOTER AKSI: Batal / Hapus Diskon & Terapkan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentDiscount != null) {
                        A3Button(
                            label = "Hapus Diskon",
                            variant = ButtonVariant.DANGER,
                            onClick = {
                                onRemoveDiscount()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(CAFE_DISCOUNT_REMOVE_BTN_TAG)
                        )
                    } else {
                        A3Button(
                            label = "Batal",
                            variant = ButtonVariant.GHOST,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    val canApply = if (activeTab == DiscountDialogTab.PRESET) selectedPreset != null else true

                    A3Button(
                        label = if (activeTab == DiscountDialogTab.PRESET) "Terapkan Diskon" else "Verifikasi & Terapkan",
                        variant = ButtonVariant.PRIMARY,
                        enabled = canApply,
                        onClick = {
                            if (activeTab == DiscountDialogTab.PRESET) {
                                val preset = selectedPreset ?: return@A3Button
                                val discount = OrderDiscount(
                                    type = preset.type,
                                    value = preset.value
                                )
                                onApplyDiscount(
                                    AppliedDiscount(
                                        discount = discount,
                                        label = preset.name,
                                        isPreset = true
                                    )
                                )
                                onDismissRequest()
                            } else {
                                val numVal = manualValueInput.toLongOrNull() ?: 0L
                                if (numVal <= 0L) {
                                    pinErrorMessage = "Masukkan nilai diskon yang valid"
                                    return@A3Button
                                }
                                if (manualType == DiscountType.PERCENT && numVal > 100L) {
                                    pinErrorMessage = "Diskon persen maksimal 100%"
                                    return@A3Button
                                }
                                if (managerPinInput.length < 4) {
                                    pinErrorMessage = "Masukkan 4 digit PIN Manager"
                                    return@A3Button
                                }

                                if (isVerifyingPin) return@A3Button
                                isVerifyingPin = true
                                val pinToCheck = managerPinInput
                                // Verifikasi PBKDF2 sengaja lambat: jangan di thread UI.
                                verifyScope.launch {
                                    val result = try {
                                        onVerifyManagerPin(pinToCheck)
                                    } finally {
                                        isVerifyingPin = false
                                    }
                                    val approval = (result as? ApprovalResult.Approved)?.approval
                                    if (approval != null) {
                                        val discount = OrderDiscount(type = manualType, value = numVal)
                                        val labelText = if (manualType == DiscountType.PERCENT) "Diskon Manual $numVal%" else "Potongan Manual ${formatRupiah(numVal)}"
                                        onApplyDiscount(
                                            AppliedDiscount(
                                                discount = discount,
                                                label = labelText,
                                                isPreset = false,
                                                approval = approval,
                                                reason = selectedReason.label,
                                                reasonCode = selectedReason.code,
                                                note = customNote.ifBlank { null }
                                            )
                                        )
                                        onDismissRequest()
                                    } else {
                                        pinErrorMessage = result.failureMessage()
                                        managerPinInput = ""
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag(CAFE_DISCOUNT_APPLY_BTN_TAG)
                    )
                }
            }
        }
    }
}

/**
 * Tab Preset: Split-View 2-Kolom (Kiri: Kartu Preset, Kanan: Rincian Perhitungan)
 */
@Composable
private fun PresetSplitView(
    subtotal: Long,
    presets: List<DiscountPreset>,
    selectedPreset: DiscountPreset?,
    onSelectPreset: (DiscountPreset) -> Unit,
    onSwitchToManual: () -> Unit
) {
    val colors = A3Theme.colors

    if (presets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceAlt.copy(alpha = 0.5f))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Belum Ada Preset Diskon Resmi",
                    style = A3Theme.typography.title,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Owner belum menambahkan preset diskon di Web Dashboard. Anda dapat menggunakan tab 'Diskon Manual' dengan PIN Manager.",
                    style = A3Theme.typography.bodySmall,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )
                A3Button(
                    label = "Buka Diskon Manual",
                    variant = ButtonVariant.SECONDARY,
                    onClick = onSwitchToManual
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sisi Kiri: Daftar Kartu Preset Resmi
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PILIHAN PRESET RESMI",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )

                presets.forEach { preset ->
                    val isSelected = selectedPreset?.id == preset.id
                    val calcValue = when (preset.type) {
                        DiscountType.PERCENT -> MoneyMath.pct(subtotal, preset.value.toInt())
                        DiscountType.AMOUNT -> min(preset.value, subtotal)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.surfaceAlt else Color.White)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) colors.accent else Color(0xFFE5E7EB),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.accent else colors.surfaceAlt),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = preset.name,
                                color = colors.ink,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        Text(
                            text = "-${formatRupiah(calcValue)}",
                            color = if (isSelected) colors.status.danger.text else colors.inkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sisi Kanan: Live Calculation Summary Card
            val activePreset = selectedPreset ?: presets.first()
            val discountVal = when (activePreset.type) {
                DiscountType.PERCENT -> MoneyMath.pct(subtotal, activePreset.value.toInt())
                DiscountType.AMOUNT -> min(activePreset.value, subtotal)
            }
            val dpp = (subtotal - discountVal).coerceAtLeast(0L)

            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.6f))
                    .padding(14.dp)
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "RINCIAN PERHITUNGAN",
                    color = colors.ink,
                    style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal Awal:", color = colors.inkMuted, fontSize = 12.sp)
                    Text(formatRupiah(subtotal), color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Potongan Diskon:", color = colors.inkMuted, fontSize = 12.sp)
                    Text("-${formatRupiah(discountVal)}", color = colors.status.danger.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color(0xFFD1D5DB))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal Bersih (DPP):", color = colors.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatRupiah(dpp), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Catatan Kebijakan Kasir
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Diskon preset dapat langsung diterapkan kasir tanpa PIN manager.",
                        color = colors.accent,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
}

/**
 * Tab Manual: Split-View 2-Kolom
 * - Sisi Kiri: Form Input Nilai, Tipe (%/Rp), Alasan Audit Wajib, & Live Preview.
 * - Sisi Kanan: Otorisasi PIN Manager & Keypad 3x4 Kompak (Bebas 'S14').
 */
@Composable
private fun ManualSplitView(
    subtotal: Long,
    manualType: DiscountType,
    onTypeChanged: (DiscountType) -> Unit,
    valueInput: String,
    onValueChanged: (String) -> Unit,
    selectedReason: DiscountReason,
    onReasonSelected: (DiscountReason) -> Unit,
    customNote: String,
    onNoteChanged: (String) -> Unit,
    managerPin: String,
    onPinDigitClick: (String) -> Unit,
    onPinBackspace: () -> Unit,
    onPinClear: () -> Unit,
    errorMessage: String?
) {
    val colors = A3Theme.colors
    var reasonMenuExpanded by remember { mutableStateOf(false) }

    val numVal = valueInput.toLongOrNull() ?: 0L
    val calculatedDiscount = when (manualType) {
        DiscountType.PERCENT -> MoneyMath.pct(subtotal, numVal.coerceIn(0L, 100L).toInt())
        DiscountType.AMOUNT -> min(numVal, subtotal)
    }
    val dpp = (subtotal - calculatedDiscount).coerceAtLeast(0L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SISI KIRI: Form Input Diskon Manual & Live Preview
        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "TIPE & NILAI DISKON",
                color = colors.ink,
                style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )

            // Baris Toggle % vs Rp & Input Nilai
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle % vs Rp
                Row(
                    modifier = Modifier
                        .width(96.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(colors.surfaceAlt)
                        .padding(2.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (manualType == DiscountType.PERCENT) colors.accent else Color.Transparent)
                            .clickable { onTypeChanged(DiscountType.PERCENT) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "%",
                            color = if (manualType == DiscountType.PERCENT) Color.White else colors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (manualType == DiscountType.AMOUNT) colors.accent else Color.Transparent)
                            .clickable { onTypeChanged(DiscountType.AMOUNT) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Rp",
                            color = if (manualType == DiscountType.AMOUNT) Color.White else colors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Input Nilai
                A3TextField(
                    value = valueInput,
                    onValueChange = onValueChanged,
                    placeholder = if (manualType == DiscountType.PERCENT) "Contoh: 15" else "Contoh: 15000",
                    modifier = Modifier.weight(1f)
                )
            }

            // Pilihan Alasan Wajib (Daftar Tetap COR-01)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Alasan Diskon (Wajib Audit Log):",
                    color = colors.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(10.dp))
                        .clickable { reasonMenuExpanded = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = selectedReason.label,
                        color = colors.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    DropdownMenu(
                        expanded = reasonMenuExpanded,
                        onDismissRequest = { reasonMenuExpanded = false }
                    ) {
                        DiscountReason.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.label, fontSize = 12.5.sp) },
                                onClick = {
                                    onReasonSelected(r)
                                    reasonMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Input Catatan jika alasan adalah "Lainnya"
            if (selectedReason == DiscountReason.OTHER) {
                A3TextField(
                    value = customNote,
                    onValueChange = onNoteChanged,
                    placeholder = "Tuliskan rincian alasan diskon...",
                    singleLine = true
                )
            }

            // Pratinjau Perhitungan Ringkas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAlt.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Potongan:", color = colors.inkMuted, fontSize = 11.5.sp)
                        Text("-${formatRupiah(calculatedDiscount)}", color = colors.status.danger.text, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("DPP Bersih:", color = colors.inkMuted, fontSize = 11.5.sp)
                        Text(formatRupiah(dpp), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }

        // SISI KANAN: Otorisasi PIN Manager & Keypad 3x4 Kompak (Bebas 'S14')
        Column(
            modifier = Modifier
                .weight(0.95f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceAlt.copy(alpha = 0.45f))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Otorisasi PIN Manager",
                    color = colors.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Indikator 4 Digit PIN (● ● ● ●)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .testTag(CAFE_DISCOUNT_PIN_INPUT_TAG)
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < managerPin.length
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) colors.accent else Color(0xFFD1D5DB))
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = colors.status.danger.text,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }

            // Keypad Numerik 3x4 Kompak
            A3Keypad(
                onDigitClick = onPinDigitClick,
                onBackspace = onPinBackspace,
                onClear = onPinClear,
                showTripleZero = false,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}
