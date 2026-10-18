package id.tenang.kasir.laundry.navigation

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import id.a3.feature.laundry.LaundryViewModelFactory
import id.a3.feature.laundry.presentation.LaundryOrderScreen
import id.a3.feature.laundry.presentation.LaundryOrderViewModel
import id.a3.feature.laundry.presentation.LaundryOrdersScreen
import id.a3.feature.laundry.presentation.LaundryOrdersViewModel
import id.a3.feature.laundry.presentation.LaundryScanViewModel
import id.a3.feature.laundry.scanner.LaundryScanScreen
import id.a3.feature.payment.PaymentViewModelFactory
import id.a3.feature.payment.presentation.PaymentScreen
import id.a3.feature.payment.presentation.PaymentViewModel

object LaundryDestinations {
    const val BERANDA = "beranda"
    const val ORDER = "order"
    const val CUCIAN = "cucian"
    const val SCAN = "scan"
    const val LAINNYA = "lainnya"
    const val PEMBAYARAN = "pembayaran"
}

@Composable
fun LaundryNavHost(
    navController: NavHostController,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    printerService: PrinterService? = null
) {
    val context = LocalContext.current

    val laundryViewModel: LaundryOrderViewModel = viewModel(
        factory = LaundryViewModelFactory(context, printerService)
    )
    val scanViewModel: LaundryScanViewModel = viewModel(
        factory = LaundryViewModelFactory(context, printerService)
    )
    val ordersViewModel: LaundryOrdersViewModel = viewModel(
        factory = LaundryViewModelFactory(context, printerService)
    )
    val paymentViewModel: PaymentViewModel = viewModel(
        factory = PaymentViewModelFactory(context, printerService)
    )

    val uiState by laundryViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = LaundryDestinations.BERANDA,
        enterTransition = {
            if (targetState.destination.route == LaundryDestinations.PEMBAYARAN) {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220))
            } else {
                fadeIn(animationSpec = tween(100))
            }
        },
        exitTransition = {
            if (targetState.destination.route == LaundryDestinations.PEMBAYARAN) {
                slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(150))
            } else {
                fadeOut(animationSpec = tween(80))
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == LaundryDestinations.PEMBAYARAN) {
                slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(220)) + fadeIn(animationSpec = tween(150))
            } else {
                fadeIn(animationSpec = tween(100))
            }
        },
        popExitTransition = {
            if (initialState.destination.route == LaundryDestinations.PEMBAYARAN) {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
            } else {
                fadeOut(animationSpec = tween(80))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        composable(LaundryDestinations.BERANDA) {
            LaundryPlaceholderView("Layar Beranda Laundry (L01)")
        }

        composable(LaundryDestinations.ORDER) {
            LaundryOrderScreen(
                uiState = uiState,
                viewModel = laundryViewModel,
                onProceedToPayment = { orderId, total, displayNumber ->
                    paymentViewModel.initializeWithOrder(
                        orderId = orderId,
                        totalAmount = total,
                        displayNumber = displayNumber,
                        tableOrQueueLabel = "Athia Laundry",
                        cashierName = "Kasir Laundry"
                    )
                    navController.navigate(LaundryDestinations.PEMBAYARAN)
                },
                onOrderSavedSuccessfully = {
                    navController.navigate(LaundryDestinations.CUCIAN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(LaundryDestinations.CUCIAN) {
            LaundryOrdersScreen(
                viewModel = ordersViewModel,
                onProceedToSettlement = { orderId, remaining, displayNumber ->
                    paymentViewModel.initializeWithOrder(
                        orderId = orderId,
                        totalAmount = remaining,
                        displayNumber = displayNumber,
                        tableOrQueueLabel = "Athia Laundry (Pelunasan)",
                        cashierName = "Kasir Laundry"
                    )
                    navController.navigate(LaundryDestinations.PEMBAYARAN)
                }
            )
        }

        composable(LaundryDestinations.SCAN) {
            LaundryScanScreen(viewModel = scanViewModel)
        }

        composable(LaundryDestinations.LAINNYA) {
            LaundryPlaceholderView("Layar Pengaturan & Menu Admin")
        }

        composable(LaundryDestinations.PEMBAYARAN) {
            PaymentScreen(
                viewModel = paymentViewModel,
                onBackClick = {
                    navController.popBackStack()
                },
                onFinishAndNewOrder = {
                    laundryViewModel.clearOrder()
                    navController.navigate(LaundryDestinations.ORDER) {
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
}

@Composable
private fun LaundryPlaceholderView(title: String) {
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
