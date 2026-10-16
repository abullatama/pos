package id.tenang.kasir.cafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.CafeTheme
import id.tenang.kasir.cafe.navigation.CafeDestinations
import id.tenang.kasir.cafe.navigation.CafeNavHost

class MainActivity : ComponentActivity() {

    private val cafeNavItems = listOf(
        NavItem(CafeDestinations.KASIR, "Kasir", Icons.Outlined.PointOfSale),
        NavItem(CafeDestinations.PESANAN, "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong),
        NavItem(CafeDestinations.HABIS, "Menu Habis", Icons.Outlined.Block),
        NavItem(CafeDestinations.SHIFT, "Shift", Icons.Outlined.AccessTime),
        NavItem(CafeDestinations.LAINNYA, "Lainnya", Icons.AutoMirrored.Outlined.ShortText)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CafeTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: CafeDestinations.KASIR

                val selectedIndex = cafeNavItems.indexOfFirst { it.id == currentRoute }.let {
                    if (it >= 0) it else 0
                }

                val isFocusFlowActive = currentRoute == CafeDestinations.PEMBAYARAN

                AppShell(
                    businessName = "AA COFFEESHOP",
                    monogramText = "AA",
                    cashierName = "Budi Santoso",
                    shiftInfo = "Shift 1",
                    syncStatus = SyncStatus.SYNCED,
                    pendingSyncCount = 0,
                    isPrinterConnected = true,
                    navItems = cafeNavItems,
                    selectedNavIndex = selectedIndex,
                    onNavItemSelected = { index ->
                        val targetRoute = cafeNavItems[index].id
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
                    CafeNavHost(
                        navController = navController,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }
}
