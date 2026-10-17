package id.a3.feature.payment.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3Keypad
import id.a3.core.ui.component.A3MoneyText
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.payment.repository.PaymentMethod

const val PAYMENT_SCREEN_TAG = "payment_screen"
const val PAYMENT_METHOD_CASH_TAG = "payment_method_cash"
const val PAYMENT_METHOD_QRIS_TAG = "payment_method_qris"
const val PAYMENT_CONFIRM_BTN_TAG = "payment_confirm_btn"
const val PAYMENT_REPRINT_BTN_TAG = "payment_reprint_btn"
const val PAYMENT_FINISH_BTN_TAG = "payment_finish_btn"

/**
 * Layar Pembayaran Penuh Fokus (S05-S08).
 * Menangani seluruh alur pelunasan:
 * - S05: Pemilihan Metode (Tunai vs QRIS Statis)
 * - S06: Pembayaran Tunai (Nominal Cepat & Kembalian Otomatis K2)
 * - S07: Pembayaran QRIS Statis (Verifikasi Notifikasi Merchant)
 * - S08: Pembayaran Berhasil & Cetak Struk Pelanggan (PRT-01, PRT-03, PRT-05)
 */
@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onBackClick: () -> Unit,
    onFinishAndNewOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = A3Theme.colors

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(PAYMENT_SCREEN_TAG),
        color = Color(0xFFF4F2E6)
    ) {
        when (uiState.currentStep) {
            PaymentStep.METHOD_SELECTION -> {
                MethodSelectionStep(
                    uiState = uiState,
                    onSelectMethod = { viewModel.selectMethod(it) },
                    onProceed = { viewModel.proceedToMethodInput() },
                    onBackClick = onBackClick
                )
            }
            PaymentStep.CASH_INPUT -> {
                CashInputStep(
                    uiState = uiState,
                    onCashReceivedChange = { viewModel.setCashReceived(it) },
                    onQuickCashClick = { viewModel.applyQuickCash(it) },
                    onConfirmPayment = { viewModel.processPayment() },
                    onBackClick = { viewModel.backToMethodSelection() }
                )
            }
            PaymentStep.QRIS_CONFIRM -> {
                QrisConfirmStep(
                    uiState = uiState,
                    onConfirmPayment = { viewModel.processPayment() },
                    onBackClick = { viewModel.backToMethodSelection() }
                )
            }
            PaymentStep.SUCCESS -> {
                PaymentSuccessStep(
                    uiState = uiState,
                    onReprintClick = { viewModel.printCustomerReceipt(isCopy = true) },
                    onFinishClick = onFinishAndNewOrder
                )
            }
        }
    }
}

/**
 * S05: Pemilihan Metode Pembayaran (Tunai vs QRIS Statis)
 */
@Composable
private fun MethodSelectionStep(
    uiState: PaymentUiState,
    onSelectMethod: (PaymentMethod) -> Unit,
    onProceed: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = A3Theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Baris Atas: Tombol Kembali & Info Order
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Kembali",
                        tint = colors.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Pembayaran",
                        color = colors.ink,
                        style = A3Theme.typography.headline.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Order #${uiState.orderDisplayNumber}" + (uiState.tableOrQueueLabel?.let { " • $it" } ?: ""),
                        color = colors.inkMuted,
                        style = A3Theme.typography.bodySmall
                    )
                }
            }
        }

        // Area Tengah: Total Tagihan Besar & 2 Kartu Metode Pembayaran
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TOTAL TAGIHAN",
                    color = colors.inkMuted,
                    style = A3Theme.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
                A3MoneyText(
                    amount = uiState.totalAmount,
                    style = A3Theme.typography.headline.copy(fontSize = 38.sp, fontWeight = FontWeight.Bold)
                )
            }

            // Pilihan Metode Bayar (2 Kartu Berdampingan)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Kartu Tunai
                MethodCard(
                    title = "Tunai (Cash)",
                    subtitle = "Uang kertas & hitung kembalian otomatis",
                    icon = Icons.Outlined.Payments,
                    isSelected = uiState.selectedMethod == PaymentMethod.CASH,
                    onClick = { onSelectMethod(PaymentMethod.CASH) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PAYMENT_METHOD_CASH_TAG)
                )

                // Kartu QRIS Statis
                MethodCard(
                    title = "QRIS Statis",
                    subtitle = "Pindai stiker & verifikasi aplikasi merchant",
                    icon = Icons.Outlined.QrCode2,
                    isSelected = uiState.selectedMethod == PaymentMethod.STATIC_QRIS,
                    onClick = { onSelectMethod(PaymentMethod.STATIC_QRIS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PAYMENT_METHOD_QRIS_TAG)
                )
            }
        }

        // Tombol Lanjut ke Input
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.End
        ) {
            A3Button(
                label = "Lanjutkan ke Pembayaran",
                variant = ButtonVariant.PRIMARY,
                onClick = onProceed,
                modifier = Modifier.width(260.dp)
            )
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = A3Theme.colors

    Surface(
        modifier = modifier
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected) 2.dp else 0.8.dp,
                    color = if (isSelected) colors.accent else Color(0xFFE5E7EB),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.accent else colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else colors.ink,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        color = colors.ink,
                        style = A3Theme.typography.title.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = colors.inkMuted,
                        style = A3Theme.typography.bodySmall.copy(fontSize = 11.5.sp)
                    )
                }
            }
        }
    }
}

/**
 * S06: Layar Bayar Tunai (Nominal Cepat & Kembalian K2)
 */
@Composable
private fun CashInputStep(
    uiState: PaymentUiState,
    onCashReceivedChange: (Long) -> Unit,
    onQuickCashClick: (Long) -> Unit,
    onConfirmPayment: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = A3Theme.colors
    var enteredDigits by remember { mutableStateOf(uiState.totalAmount.toString()) }
    val isInsufficient = uiState.cashReceived < uiState.totalAmount

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // PANEL KIRI (52%): Total Tagihan, Uang Diterima, Pecahan Cepat, Kartu Kembalian
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header Tombol Kembali
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Pilih Metode Lain",
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Pembayaran Tunai",
                        color = colors.ink,
                        style = A3Theme.typography.title.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Kotak Total Tagihan & Uang Diterima
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Tagihan:", color = colors.inkMuted, fontSize = 13.sp)
                            Text(
                                text = formatRupiah(uiState.totalAmount),
                                color = colors.ink,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color(0xFFE5E7EB))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Uang Diterima:", color = colors.inkMuted, fontSize = 13.sp)
                            Text(
                                text = formatRupiah(uiState.cashReceived),
                                color = colors.accent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tombol Pecahan Cepat (Uang Pas, 70k, 100k, dll)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PECAHAN NOMINAL CEPAT",
                        color = colors.inkMuted,
                        style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.quickCashOptions.forEach { optionAmount ->
                            val isExact = optionAmount == uiState.totalAmount
                            val isSelected = optionAmount == uiState.cashReceived

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) colors.accent else Color.White)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.8.dp,
                                        color = if (isSelected) colors.accent else Color(0xFFD1D5DB),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        enteredDigits = optionAmount.toString()
                                        onQuickCashClick(optionAmount)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isExact) "Uang Pas" else formatRupiah(optionAmount),
                                    color = if (isSelected) Color.White else colors.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Kartu Kembalian Taktil (Test Vector K2)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isInsufficient) Color(0xFFFEF2F2) else Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isInsufficient) "Kurang Bayar:" else "Kembalian:",
                            color = if (isInsufficient) colors.status.danger.text else colors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (isInsufficient) {
                                formatRupiah(uiState.totalAmount - uiState.cashReceived)
                            } else {
                                formatRupiah(uiState.change)
                            },
                            color = if (isInsufficient) colors.status.danger.text else colors.accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tombol Konfirmasi Pembayaran
            A3Button(
                label = if (uiState.isProcessing) "Memproses..." else "Terima Pembayaran Tunai",
                variant = ButtonVariant.PRIMARY,
                enabled = !isInsufficient && !uiState.isProcessing,
                onClick = onConfirmPayment,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PAYMENT_CONFIRM_BTN_TAG)
            )
        }

        // PANEL KANAN (48%): Keypad Numerik Input Tunai 3x4
        Surface(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "KEYPAD TUNAI",
                    color = colors.inkMuted,
                    style = A3Theme.typography.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                A3Keypad(
                    onDigitClick = { digit ->
                        if (enteredDigits.length < 9) {
                            enteredDigits = if (enteredDigits == "0") digit else enteredDigits + digit
                            val parsed = enteredDigits.toLongOrNull() ?: 0L
                            onCashReceivedChange(parsed)
                        }
                    },
                    onBackspace = {
                        enteredDigits = if (enteredDigits.length <= 1) "0" else enteredDigits.dropLast(1)
                        val parsed = enteredDigits.toLongOrNull() ?: 0L
                        onCashReceivedChange(parsed)
                    },
                    onClear = {
                        enteredDigits = "0"
                        onCashReceivedChange(0L)
                    },
                    showTripleZero = true,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }
        }
    }
}

/**
 * S07: Layar Bayar QRIS Statis (Verifikasi Notifikasi Merchant)
 */
@Composable
private fun QrisConfirmStep(
    uiState: PaymentUiState,
    onConfirmPayment: () -> Unit,
    onBackClick: () -> Unit
) {
    val colors = A3Theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Baris Atas: Tombol Kembali
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Pilih Metode Lain",
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "Pembayaran QRIS Statis",
                color = colors.ink,
                style = A3Theme.typography.title.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold
            )
        }

        // Area Tengah: Instruksi Merchant & Nominal
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TOTAL PEMBAYARAN QRIS",
                        color = colors.inkMuted,
                        style = A3Theme.typography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    A3MoneyText(
                        amount = uiState.totalAmount,
                        style = A3Theme.typography.headline.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    )
                }

                // Kotak Panduan Kasir (PAY-03)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFBEB))
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Panduan Verifikasi Kasir:",
                                color = Color(0xFF92400E),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1. Minta pelanggan memindai stiker QRIS resmi di meja kasir.\n2. Pastikan dana masuk lewat notifikasi aplikasi merchant atau soundbox toko (bukan dari tangkapan layar pelanggan).",
                                color = Color(0xFF78350F),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Tombol Konfirmasi QRIS Diterima
        Row(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.End
        ) {
            A3Button(
                label = if (uiState.isProcessing) "Menyimpan..." else "Pembayaran QRIS Diterima",
                variant = ButtonVariant.PRIMARY,
                enabled = !uiState.isProcessing,
                onClick = onConfirmPayment,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PAYMENT_CONFIRM_BTN_TAG)
            )
        }
    }
}

/**
 * S08: Layar Pembayaran Berhasil & Cetak Struk (PRT-01, PRT-03, PRT-05)
 */
@Composable
private fun PaymentSuccessStep(
    uiState: PaymentUiState,
    onReprintClick: () -> Unit,
    onFinishClick: () -> Unit
) {
    val colors = A3Theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.65f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ikon Centang Sukses Besar
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Sukses",
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Pembayaran Berhasil!",
                        color = colors.ink,
                        style = A3Theme.typography.headline.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transaksi #${uiState.orderDisplayNumber} telah lunas dicatat.",
                        color = colors.inkMuted,
                        fontSize = 13.sp
                    )
                }

                // Kotak Ringkasan Pembayaran
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceAlt.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Metode Pembayaran:", color = colors.inkMuted, fontSize = 12.5.sp)
                            Text(uiState.selectedMethod.label, color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Lunas:", color = colors.inkMuted, fontSize = 12.5.sp)
                            Text(formatRupiah(uiState.totalAmount), color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (uiState.selectedMethod == PaymentMethod.CASH) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Uang Diterima:", color = colors.inkMuted, fontSize = 12.5.sp)
                                Text(formatRupiah(uiState.cashReceived), color = colors.ink, fontSize = 12.5.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Kembalian:", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(formatRupiah(uiState.change), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Status Notifikasi Printer
                if (!uiState.printStatusMessage.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = uiState.printStatusMessage ?: "",
                            color = colors.inkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tombol Aksi: Cetak Ulang (SALINAN) & Pesanan Baru
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    A3Button(
                        label = "Cetak Salinan Struk",
                        variant = ButtonVariant.SECONDARY,
                        onClick = onReprintClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(PAYMENT_REPRINT_BTN_TAG)
                    )

                    A3Button(
                        label = "Pesanan Baru / Selesai",
                        variant = ButtonVariant.PRIMARY,
                        onClick = onFinishClick,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag(PAYMENT_FINISH_BTN_TAG)
                    )
                }
            }
        }
    }
}
