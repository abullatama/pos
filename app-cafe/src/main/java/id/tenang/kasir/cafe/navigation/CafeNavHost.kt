package id.tenang.kasir.cafe.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.a3.core.printer.PrinterService
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.cafe.CafeMenuViewModelFactory
import id.a3.feature.cafe.presentation.CafeMenuViewModel
import id.a3.feature.cafe.presentation.screen.CafeOpenOrdersScreen
import id.a3.feature.cafe.presentation.screen.CafePosScreen
import id.a3.feature.cafe.presentation.screen.CafeSoldOutScreen
import id.a3.feature.payment.PaymentViewModelFactory
import id.a3.feature.payment.presentation.PaymentScreen
import id.a3.feature.payment.presentation.PaymentViewModel
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
            CafePlaceholderView("Layar Manajemen Shift & Laci Kas")
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
