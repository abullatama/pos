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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.a3.core.ui.theme.A3Theme
import id.a3.feature.laundry.scanner.LaundryScanScreen

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
    modifier: Modifier = Modifier
) {
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
            LaundryPlaceholderView("Layar Order Baru Laundry (L03)")
        }
        composable(LaundryDestinations.CUCIAN) {
            LaundryPlaceholderView("Layar Daftar & Detail Cucian (L05/L06)")
        }
        composable(LaundryDestinations.SCAN) {
            LaundryScanScreen(
                onStatusAdvanceClick = { orderId ->
                    // Di M2 akan memicu update status cucian via LaundryOrderRepository
                }
            )
        }
        composable(LaundryDestinations.LAINNYA) {
            LaundryPlaceholderView("Layar Pengaturan & Menu Admin")
        }
        composable(LaundryDestinations.PEMBAYARAN) {
            LaundryPlaceholderView("Alur Pembayaran (Fokus Penuh)")
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
