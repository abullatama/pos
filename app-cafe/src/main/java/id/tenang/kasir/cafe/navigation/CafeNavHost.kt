package id.tenang.kasir.cafe.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.a3.core.printer.PrinterService
import id.a3.core.ui.component.A3Button
import id.a3.core.ui.component.ButtonVariant
import id.a3.core.ui.component.formatRupiah
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.CafeMenuViewModelFactory
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.screen.CafeOpenOrdersScreen
import id.a3.feature.cafe.presentation.screen.CafePosScreen
import id.a3.feature.cafe.presentation.screen.CafeSoldOutScreen
import id.a3.feature.payment.PaymentViewModelFactory
import id.a3.feature.payment.presentation.PaymentScreen
import id.a3.feature.payment.presentation.PaymentViewModel
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.CloseShiftScreen
import id.a3.feature.shift.presentation.CloseShiftViewModel
import id.a3.feature.shift.presentation.OpenShiftScreen
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3.feature.shift.repository.ActiveShift
import kotlinx.coroutines.launch

object CafeDestinations {
    const val KASIR = "kasir"
    const val PESANAN = "pesanan"
    const val HABIS = "habis"
    const val SHIFT = "shift"
    const val LAINNYA = "lainnya"
    const val PEMBAYARAN = "pembayaran"
}

@Composable
fun CafeNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    printerService: PrinterService? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val menuViewModel: CafeMenuViewModel = viewModel(
        factory = CafeMenuViewModelFactory(context, printerService)
    )
    val paymentViewModel: PaymentViewModel = viewModel(
        factory = PaymentViewModelFactory(context, printerService)
    )
    val shiftViewModel: ShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
    )
    val closeShiftViewModel: CloseShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
    )

    NavHost(
        navController = navController,
        startDestination = CafeDestinations.KASIR,
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
                                tableOrQueueLabel = header.tableOrQueueLabel
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
        composable(CafeDestinations.PESANAN) {
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
                        totalAmount = total
                    )
                    navController.navigate(CafeDestinations.PEMBAYARAN)
                }
            )
        }
        composable(CafeDestinations.HABIS) {
            CafeSoldOutScreen(viewModel = menuViewModel)
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
                    cashierName = "Budi Santoso",
                    cashierId = "u-cashier-1",
                    businessUnitId = "bu-cafe-01",
                    deviceId = "dev-cafe-1",
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
                    }
                )
            }
        }
        composable(CafeDestinations.LAINNYA) {
            CafePlaceholderView("Layar Pengaturan & Menu Admin")
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
    onCloseShiftClick: () -> Unit
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
                text = "Kasir dapat menerima pembayaran tunai dan mengelola pesanan meja.",
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
                label = "Tutup Shift Kasir",
                variant = ButtonVariant.SECONDARY,
                onClick = onCloseShiftClick,
                fillWidth = true
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
