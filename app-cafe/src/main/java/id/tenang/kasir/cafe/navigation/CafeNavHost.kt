package id.tenang.kasir.cafe.navigation

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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.a3.core.ui.theme.A3Theme

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
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = CafeDestinations.KASIR,
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        composable(CafeDestinations.KASIR) {
            CafePlaceholderView("Layar Kasir (C01): Menu & Keranjang")
        }
        composable(CafeDestinations.PESANAN) {
            CafePlaceholderView("Layar Pesanan Terbuka (C04)")
        }
        composable(CafeDestinations.HABIS) {
            CafePlaceholderView("Layar Menu Habis (C07)")
        }
        composable(CafeDestinations.SHIFT) {
            CafePlaceholderView("Layar Manajemen Shift")
        }
        composable(CafeDestinations.LAINNYA) {
            CafePlaceholderView("Layar Pengaturan & Menu Admin")
        }
        composable(CafeDestinations.PEMBAYARAN) {
            CafePlaceholderView("Alur Pembayaran (Fokus Penuh)")
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
