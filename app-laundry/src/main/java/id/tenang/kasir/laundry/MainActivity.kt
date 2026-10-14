package id.tenang.kasir.laundry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.A3Theme
import id.a3.core.ui.theme.LaundryTheme

class MainActivity : ComponentActivity() {

    private val laundryNavItems = listOf(
        NavItem("beranda", "Beranda", "🏠"),
        NavItem("order", "Order Baru", "➕"),
        NavItem("cucian", "Cucian", "🧺"),
        NavItem("scan", "Scan QR", "📷"),
        NavItem("lainnya", "Lainnya", "⋯")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaundryTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var isPaymentActive by remember { mutableStateOf(false) }

                AppShell(
                    businessName = "ATHIA LAUNDRY HOUSE",
                    monogramText = "AL",
                    cashierName = "Siti Rahma",
                    shiftInfo = "Shift Pagi",
                    syncStatus = SyncStatus.SYNCED,
                    pendingSyncCount = 0,
                    isPrinterConnected = true,
                    navItems = laundryNavItems,
                    selectedNavIndex = selectedTab,
                    onNavItemSelected = { selectedTab = it },
                    isFocusFlowActive = isPaymentActive
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Layar Laundry: ${laundryNavItems[selectedTab].label}",
                            color = A3Theme.colors.ink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
