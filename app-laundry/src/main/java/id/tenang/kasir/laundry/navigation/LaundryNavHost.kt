package id.tenang.kasir.laundry.navigation

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
import id.a3.feature.laundry.LaundryViewModelFactory
import id.a3.feature.laundry.presentation.LaundryHomeScreen
import id.a3.feature.laundry.presentation.LaundryHomeViewModel
import id.a3.feature.laundry.presentation.LaundryOrderScreen
import id.a3.feature.laundry.presentation.LaundryOrderViewModel
import id.a3.feature.laundry.presentation.LaundryOrdersScreen
import id.a3.feature.laundry.presentation.LaundryOrdersViewModel
import id.a3.feature.laundry.presentation.LaundryScanViewModel
import id.a3.feature.laundry.scanner.LaundryScanScreen
import id.a3.feature.payment.PaymentViewModelFactory
import id.a3.feature.payment.presentation.PaymentScreen
import id.a3.feature.payment.presentation.PaymentViewModel
import id.a3.feature.shift.ShiftViewModelFactory
import id.a3.feature.shift.presentation.CloseShiftScreen
import id.a3.feature.shift.presentation.CloseShiftViewModel
import id.a3.feature.shift.presentation.OpenShiftScreen
import id.a3.feature.shift.presentation.ShiftViewModel
import id.a3.feature.shift.repository.ActiveShift

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
    val homeViewModel: LaundryHomeViewModel = viewModel(
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
    val shiftViewModel: ShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
    )
    val closeShiftViewModel: CloseShiftViewModel = viewModel(
        factory = ShiftViewModelFactory(context, printerService)
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
            LaundryHomeScreen(
                viewModel = homeViewModel,
                onNavigateToOrder = {
                    navController.navigate(LaundryDestinations.ORDER) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToScan = {
                    navController.navigate(LaundryDestinations.SCAN) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToCucian = {
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

        composable(LaundryDestinations.ORDER) {
            LaundryOrderScreen(
                uiState = uiState,
                viewModel = laundryViewModel,
                onProceedToPayment = { orderId, amountToPay, displayNumber, isDp ->
                    paymentViewModel.initializeWithOrder(
                        orderId = orderId,
                        totalAmount = amountToPay,
                        displayNumber = displayNumber,
                        tableOrQueueLabel = if (isDp) "Athia Laundry (DP)" else "Athia Laundry",
                        cashierName = "Kasir Laundry",
                        storeName = "ATHIA LAUNDRY HOUSE",
                        storeAddress = "Jl. Trans Sulawesi, Bintauna",
                        storePhone = "+62 823-4778-5986",
                        isDp = isDp
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
                        cashierName = "Kasir Laundry",
                        storeName = "ATHIA LAUNDRY HOUSE",
                        storeAddress = "Jl. Trans Sulawesi, Bintauna",
                        storePhone = "+62 823-4778-5986",
                        isDp = false
                    )
                    navController.navigate(LaundryDestinations.PEMBAYARAN)
                }
            )
        }

        composable(LaundryDestinations.SCAN) {
            LaundryScanScreen(viewModel = scanViewModel)
        }

        composable(LaundryDestinations.LAINNYA) {
            val shiftUiState by shiftViewModel.uiState.collectAsState()
            val activeShift = shiftUiState.currentShift
            var isClosingShift by remember { mutableStateOf(false) }

            if (isClosingShift && activeShift != null) {
                CloseShiftScreen(
                    viewModel = closeShiftViewModel,
                    shiftId = activeShift.shiftId,
                    storeName = "ATHIA LAUNDRY HOUSE",
                    storeAddress = "Jl. Trans Sulawesi, Bintauna",
                    storePhone = "+62 823-4778-5986",
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
                    cashierName = "Siti Rahma",
                    cashierId = "cashier-ldr-1",
                    businessUnitId = "bu-laundry-01",
                    deviceId = "dev-laundry-1",
                    onShiftOpened = {
                        navController.navigate(LaundryDestinations.BERANDA) {
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
                text = "Shift Kasir Aktif",
                style = A3Theme.typography.title,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )

            Text(
                text = "Kasir dapat menerima pembayaran tunai dan mengelola pesanan laundry.",
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
