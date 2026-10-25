package id.tenang.kasir.cafe.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.a3.core.data.local.AppDatabase
import id.a3.core.printer.PrinterService
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.A3EmptyState
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.admin.presentation.AdminMenuScreen
import id.a3.feature.admin.presentation.AdminViewModel
import id.a3.feature.admin.presentation.AdminViewModelFactory
import id.a3.feature.cafe.CafeMenuViewModelFactory
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.screen.CafeOpenOrdersScreen
import id.a3.feature.cafe.presentation.screen.CafePosScreen
import id.a3.feature.cafe.presentation.screen.CafeSoldOutScreen
import id.a3.feature.payment.PaymentViewModelFactory
import id.a3.feature.payment.presentation.PaymentScreen
import id.a3.feature.payment.presentation.PaymentViewModel
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.CashOutScreen
import id.a3.feature.shift.presentation.CashOutViewModel
import id.a3.feature.shift.presentation.CloseShiftScreen
import id.a3.feature.shift.presentation.CloseShiftViewModel
import id.a3.feature.shift.presentation.ForceCloseShiftDialog
import id.a3.feature.shift.presentation.OpenShiftScreen
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3.feature.shift.repository.ActiveShift
import kotlinx.coroutines.launch

object CafeDestinations {
    const val KASIR = "kasir"
    const val PESANAN = "pesanan"
    const val HABIS = "habis"
    const val KAS_KELUAR = "kas_keluar"
    const val SHIFT = "shift"
    const val ADMIN = "admin"
    const val PEMBAYARAN = "pembayaran"
}

@Composable
fun CafeNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    printerService: PrinterService? = null,
    shiftViewModel: ShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(LocalContext.current, printerService)
    ),
    startDestination: String = CafeDestinations.KASIR,
    deviceId: String = "dev-cafe-1",
    businessUnitId: String = "bu-cafe-01",
    cashierName: String = "Kasir Cafe",
    cashierId: String = "u-cashier-1"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val menuViewModel: CafeMenuViewModel = viewModel(
        factory = CafeMenuViewModelFactory(context, printerService)
    )
    val paymentViewModel: PaymentViewModel = viewModel(
        factory = PaymentViewModelFactory(context, printerService)
    )
    val closeShiftViewModel: CloseShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
    )
    val cashOutViewModel: CashOutViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
    )
    val adminViewModel: AdminViewModel = viewModel(
        factory = AdminViewModelFactory(
            database = AppDatabase.build(context),
            printerService = printerService,
            deviceId = deviceId,
            businessUnitId = businessUnitId,
            appVersion = "1.0.0"
        )
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (targetState.destination.route == CafeDestinations.PEMBAYARAN) {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220))
            } else {
                fadeIn(animationSpec = tween(100))
            }
        },
        exitTransition = {
            if (targetState.destination.route == CafeDestinations.PEMBAYARAN) {
                slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(150))
            } else {
                fadeOut(animationSpec = tween(80))
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == CafeDestinations.PEMBAYARAN) {
                slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(150))
            } else {
                fadeIn(animationSpec = tween(100))
            }
        },
        popExitTransition = {
            if (initialState.destination.route == CafeDestinations.PEMBAYARAN) {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
            } else {
                fadeOut(animationSpec = tween(80))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        composable(CafeDestinations.KASIR) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift

            if (activeShift == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(A3Theme.colors.canvas)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .clip(A3Theme.shapes.cardShape)
                            .background(A3Theme.colors.surface)
                            .border(1.dp, A3Theme.colors.border, A3Theme.shapes.cardShape)
                            .padding(32.dp)
                    ) {
                        A3EmptyState(
                            title = "Shift Kasir Belum Dibuka",
                            description = "Silakan buka shift kasir terlebih dahulu dengan memasukkan kas awal laci sebelum melayani pesanan cafe.",
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = A3Theme.colors.accent,
                                    modifier = Modifier.size(56.dp)
                                )
                            },
                            action = {
                                A3Button(
                                    label = "Buka Shift Kasir Sekarang",
                                    variant = ButtonVariant.PRIMARY,
                                    onClick = {
                                        navController.navigate(CafeDestinations.SHIFT) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            } else {
                CafePosScreen(
                    viewModel = menuViewModel,
                    onCheckoutClick = { calculation ->
                        if (menuViewModel.isCheckingOut.value) return@CafePosScreen
                        menuViewModel.setCheckingOut(true)
                        coroutineScope.launch {
                            try {
                                val header = menuViewModel.uiState.value.orderHeader
                                val saveResult = menuViewModel.directCheckout(calculation)
                                val targetOrderId = saveResult.getOrNull() ?: header.orderId

                                paymentViewModel.initializeWithOrder(
                                    orderId = targetOrderId,
                                    totalAmount = calculation.total,
                                    displayNumber = header.receiptId,
                                    tableOrQueueLabel = header.tableOrQueueLabel,
                                    cashierName = activeShift.cashierName,
                                    shiftId = activeShift.shiftId
                                )
                                navController.navigate(CafeDestinations.PEMBAYARAN)
                            } finally {
                                menuViewModel.setCheckingOut(false)
                            }
                        }
                    },
                    onSendToKitchenClick = {
                        navController.navigate(CafeDestinations.PESANAN) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
        composable(CafeDestinations.PESANAN) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift

            if (activeShift == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(A3Theme.colors.canvas)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .clip(A3Theme.shapes.cardShape)
                            .background(A3Theme.colors.surface)
                            .border(1.dp, A3Theme.colors.border, A3Theme.shapes.cardShape)
                            .padding(32.dp)
                    ) {
                        A3EmptyState(
                            title = "Shift Belum Dibuka",
                            description = "Daftar pesanan aktif dan riwayat transaksi memerlukan shift kasir aktif.",
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = A3Theme.colors.accent,
                                    modifier = Modifier.size(56.dp)
                                )
                            },
                            action = {
                                A3Button(
                                    label = "Buka Shift Kasir Sekarang",
                                    variant = ButtonVariant.PRIMARY,
                                    onClick = {
                                        navController.navigate(CafeDestinations.SHIFT) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            } else {
                CafeOpenOrdersScreen(
                    viewModel = menuViewModel,
                    onNavigateToPos = {
                        navController.navigate(CafeDestinations.KASIR) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onProceedToPayment = { orderId, total ->
                        paymentViewModel.initializeWithOrder(
                            orderId = orderId,
                            totalAmount = total,
                            cashierName = activeShift.cashierName,
                            shiftId = activeShift.shiftId
                        )
                        navController.navigate(CafeDestinations.PEMBAYARAN)
                    }
                )
            }
        }
        composable(CafeDestinations.HABIS) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift

            if (activeShift == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(A3Theme.colors.canvas)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .clip(A3Theme.shapes.cardShape)
                            .background(A3Theme.colors.surface)
                            .border(1.dp, A3Theme.colors.border, A3Theme.shapes.cardShape)
                            .padding(32.dp)
                    ) {
                        A3EmptyState(
                            title = "Shift Belum Dibuka",
                            description = "Pengaturan menu habis memerlukan shift kasir aktif untuk memperbarui ketersediaan menu.",
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = A3Theme.colors.accent,
                                    modifier = Modifier.size(56.dp)
                                )
                            },
                            action = {
                                A3Button(
                                    label = "Buka Shift Kasir Sekarang",
                                    variant = ButtonVariant.PRIMARY,
                                    onClick = {
                                        navController.navigate(CafeDestinations.SHIFT) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            } else {
                CafeSoldOutScreen(viewModel = menuViewModel)
            }
        }
        composable(CafeDestinations.KAS_KELUAR) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift

            if (activeShift == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(A3Theme.colors.canvas)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .clip(A3Theme.shapes.cardShape)
                            .background(A3Theme.colors.surface)
                            .border(1.dp, A3Theme.colors.border, A3Theme.shapes.cardShape)
                            .padding(32.dp)
                    ) {
                        A3EmptyState(
                            title = "Shift Belum Dibuka",
                            description = "Pencatatan kas keluar memerlukan shift kasir aktif untuk memotong saldo kas laci.",
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = A3Theme.colors.accent,
                                    modifier = Modifier.size(56.dp)
                                )
                            },
                            action = {
                                A3Button(
                                    label = "Buka Shift Kasir Sekarang",
                                    variant = ButtonVariant.PRIMARY,
                                    onClick = {
                                        navController.navigate(CafeDestinations.SHIFT) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            } else {
                CashOutScreen(
                    viewModel = cashOutViewModel,
                    onBack = {
                        navController.navigate(CafeDestinations.KASIR) {
                            popUpTo(CafeDestinations.KASIR) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    storeName = "AA COFFEESHOP",
                    storeAddress = "Jl. Kopi Pilihan No. 3",
                    storePhone = "0812-3456-7890"
                )
            }
        }
        composable(CafeDestinations.SHIFT) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift
            var isClosingShift by remember { mutableStateOf(false) }

            if (isClosingShift && activeShift != null) {
                CloseShiftScreen(
                    viewModel = closeShiftViewModel,
                    shiftId = activeShift.shiftId,
                    storeName = "AA COFFEESHOP",
                    storeAddress = "Jl. Kopi Pilihan No. 3",
                    storePhone = "0812-3456-7890",
                    onCloseCompleted = {
                        isClosingShift = false
                    },
                    onCancel = {
                        isClosingShift = false
                    }
                )
            } else if (activeShift == null) {
                OpenShiftScreen(
                    viewModel = shiftViewModel,
                    cashierName = cashierName,
                    cashierId = cashierId,
                    businessUnitId = businessUnitId,
                    deviceId = deviceId,
                    onShiftOpened = {
                        navController.navigate(CafeDestinations.KASIR) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            } else {
                ActiveShiftCard(
                    activeShift = activeShift,
                    onCloseShiftClick = {
                        isClosingShift = true
                    },
                    onForceCloseClick = shiftViewModel::openForceCloseDialog
                )
            }

            if (shiftUiState.isForceCloseDialogOpen && activeShift != null) {
                ForceCloseShiftDialog(
                    shiftId = activeShift.shiftId,
                    cashierName = activeShift.cashierName,
                    errorMessage = shiftUiState.forceCloseErrorMessage,
                    isManagerLocked = shiftUiState.isManagerLocked,
                    lockoutRemainingSeconds = shiftUiState.lockoutRemainingSeconds,
                    isSubmitting = shiftUiState.isForceClosing,
                    onDismiss = shiftViewModel::closeForceCloseDialog,
                    onSubmitForceClose = { pin, note ->
                        shiftViewModel.verifyManagerPinAndForceClose(
                            pin = pin,
                            note = note,
                            onSuccess = {
                                // Shift berhasil ditutup paksa
                            }
                        )
                    }
                )
            }
        }
        composable(CafeDestinations.ADMIN) {
            AdminMenuScreen(
                viewModel = adminViewModel,
                onBackClick = {
                    if (!navController.popBackStack()) {
                        val fallback = if (shiftViewModel.uiState.value.currentShift != null) {
                            CafeDestinations.KASIR
                        } else {
                            CafeDestinations.SHIFT
                        }
                        navController.navigate(fallback) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(CafeDestinations.PEMBAYARAN) {
            PaymentScreen(
                viewModel = paymentViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onFinishAndNewOrder = {
                    menuViewModel.clearCart()
                    navController.navigate(CafeDestinations.KASIR) {
                        popUpTo(CafeDestinations.KASIR) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun ActiveShiftCard(
    activeShift: ActiveShift,
    onCloseShiftClick: () -> Unit,
    onForceCloseClick: () -> Unit
) {
    val colors = A3Theme.colors
    val shapes = A3Theme.shapes

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(shapes.cardShape)
                .background(colors.surface)
                .border(1.dp, colors.border, shapes.cardShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.status.success.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = colors.status.success.text,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Shift Sedang Aktif",
                style = A3Theme.typography.title,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Text(
                text = "Kasir dapat melayani pesanan cafe, menerima pembayaran tunai & QRIS, serta menutup shift saat jam kerja berakhir.",
                style = A3Theme.typography.body,
                color = colors.inkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            HorizontalDivider(color = colors.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Kasir Bertugas:", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                Text(text = activeShift.cashierName, style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = colors.ink)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Kas Awal:", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                Text(text = formatRupiah(activeShift.openingCash), style = A3Theme.typography.body, fontWeight = FontWeight.Bold, color = colors.accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "ID Shift:", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
                Text(text = activeShift.shiftId.take(8) + "...", style = A3Theme.typography.bodySmall, color = colors.inkMuted)
            }

            HorizontalDivider(color = colors.border)

            A3Button(
                label = "Tutup Shift Sekarang",
                variant = ButtonVariant.PRIMARY,
                onClick = onCloseShiftClick,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Tutup Paksa Shift (Manager)",
                color = Color(0xFFC62828),
                style = A3Theme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onForceCloseClick)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun CafePlaceholderView(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = A3Theme.colors.ink,
            style = A3Theme.typography.title,
            fontWeight = FontWeight.Bold
        )
    }
}
