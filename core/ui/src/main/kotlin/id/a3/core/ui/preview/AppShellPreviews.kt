package id.a3.core.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PointOfSale
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.A3Theme
import id.a3.core.ui.theme.CafeTheme
import id.a3.core.ui.theme.LaundryTheme

val SampleCafeNavItems = listOf(
    NavItem("kasir", "Kasir", Icons.Outlined.PointOfSale),
    NavItem("pesanan", "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong),
    NavItem("habis", "Menu Habis", Icons.Outlined.Block),
    NavItem("shift", "Shift", Icons.Outlined.AccessTime),
    NavItem("lainnya", "Lainnya", Icons.AutoMirrored.Outlined.ShortText)
)

val SampleLaundryNavItems = listOf(
    NavItem("beranda", "Beranda", Icons.Outlined.Home),
    NavItem("order", "Order Baru", Icons.Outlined.AddCircle),
    NavItem("cucian", "Cucian", Icons.Outlined.Inventory2),
    NavItem("scan", "Scan QR", Icons.Outlined.PhotoCamera),
    NavItem("lainnya", "Lainnya", Icons.AutoMirrored.Outlined.ShortText)
)

@Preview(name = "AA Coffeeshop - Normal Flow", widthDp = 1138, heightDp = 711)
@Composable
fun CafeAppShellPreview() {
    CafeTheme {
        AppShell(
            businessName = "AA COFFEESHOP",
            monogramText = "AA",
            cashierName = "Budi Santoso",
            shiftInfo = "Shift 1",
            syncStatus = SyncStatus.SYNCED,
            isPrinterConnected = true,
            navItems = SampleCafeNavItems,
            selectedNavIndex = 0
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Area Konten POS Cafe (Pola 65% Menu / 35% Struk)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = A3Theme.colors.ink
                )
            }
        }
    }
}

@Preview(name = "Athia Laundry House - Normal Flow", widthDp = 1138, heightDp = 711)
@Composable
fun LaundryAppShellPreview() {
    LaundryTheme {
        AppShell(
            businessName = "ATHIA LAUNDRY HOUSE",
            monogramText = "AL",
            cashierName = "Siti Rahma",
            shiftInfo = "Shift Pagi",
            syncStatus = SyncStatus.PENDING,
            pendingSyncCount = 3,
            isPrinterConnected = true,
            navItems = SampleLaundryNavItems,
            selectedNavIndex = 1
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Area Konten Laundry (Pola 60% Form Order / 40% Tagihan)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = A3Theme.colors.ink
                )
            }
        }
    }
}

@Preview(name = "AppShell - Alur Pembayaran (Navigasi Tersembunyi)", widthDp = 1138, heightDp = 711)
@Composable
fun FocusFlowHiddenNavPreview() {
    CafeTheme {
        AppShell(
            businessName = "AA COFFEESHOP",
            cashierName = "Budi Santoso",
            shiftInfo = "Shift 1",
            syncStatus = SyncStatus.SYNCED,
            isPrinterConnected = true,
            navItems = SampleCafeNavItems,
            selectedNavIndex = 0,
            isFocusFlowActive = true // ALUR FOKUS: Navigasi bawah tersembunyi
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Layar Pembayaran (Alur Fokus Penuh)",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = A3Theme.colors.ink
                    )
                    Text(
                        text = "Navigasi bawah otomatis disembunyikan agar kasir fokus tanpa distraksi",
                        fontSize = 14.sp,
                        color = A3Theme.colors.inkMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
