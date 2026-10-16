package id.tenang.kasir.laundry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.LaundryTheme
import id.tenang.kasir.laundry.navigation.LaundryDestinations
import id.tenang.kasir.laundry.navigation.LaundryNavHost

class MainActivity : ComponentActivity() {

    private val laundryNavItems = listOf(
        NavItem(LaundryDestinations.BERANDA, "Beranda", Icons.Outlined.Home),
        NavItem(LaundryDestinations.ORDER, "Order Baru", Icons.Outlined.AddCircle),
        NavItem(LaundryDestinations.CUCIAN, "Cucian", Icons.Outlined.Inventory2),
        NavItem(LaundryDestinations.SCAN, "Scan QR", Icons.Outlined.PhotoCamera),
        NavItem(LaundryDestinations.LAINNYA, "Lainnya", Icons.AutoMirrored.Outlined.ShortText)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaundryTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: LaundryDestinations.BERANDA

                val selectedIndex = laundryNavItems.indexOfFirst { it.id == currentRoute }.let {
                    if (it >= 0) it else 0
                }

                val isFocusFlowActive = currentRoute == LaundryDestinations.PEMBAYARAN

                AppShell(
                    businessName = "ATHIA LAUNDRY HOUSE",
                    monogramText = "AL",
                    cashierName = "Siti Rahma",
                    shiftInfo = "Shift Pagi",
                    syncStatus = SyncStatus.SYNCED,
                    pendingSyncCount = 0,
                    isPrinterConnected = true,
                    navItems = laundryNavItems,
                    selectedNavIndex = selectedIndex,
                    onNavItemSelected = { index ->
                        val targetRoute = laundryNavItems[index].id
                        if (targetRoute != currentRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    isFocusFlowActive = isFocusFlowActive
                ) { paddingValues ->
                    LaundryNavHost(
                        navController = navController,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }
}
