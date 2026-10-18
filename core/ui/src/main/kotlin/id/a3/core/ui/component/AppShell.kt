package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

const val APP_SHELL_TOP_BAR_TAG = "app_shell_top_bar"
const val APP_SHELL_NAV_RAIL_TAG = "app_shell_nav_rail"
// Alias untuk menjaga kompatibilitas pengetesan
const val APP_SHELL_BOTTOM_BAR_TAG = APP_SHELL_NAV_RAIL_TAG

/**
 * Kerangka layar standar tablet landscape 11 inci (A3 POS):
 * - Bilah Atas Penuh (TopBar 64 dp): Berada di pucuk layar dengan padding status bar sistem,
 *   menyatukan Logo Monogram Merek ([AA] / [AL]) dan Nama Usaha berdampingan secara utuh.
 *   Tidak ada lagi warna hitam yang memotong jam, hari, dan tanggal tablet.
 * - Bilah Navigasi Mengambang di Sisi Kiri (Floating Left Navigation Rail):
 *   Mengambang elegan di bawah TopBar dengan sudut melengkung dan bayangan halus.
 * - Area Konten: Mengisi seluruh ruang layar yang tersisa secara lega dan bebas tabrakan.
 * - Alur Fokus Penuh: Ketika [isFocusFlowActive] == true (alur pembayaran, input PIN, tutup shift),
 *   bilah navigasi kiri otomatis meluncur keluar ke samping agar kasir fokus tanpa distraksi.
 */
@Composable
fun AppShell(
    businessName: String,
    modifier: Modifier = Modifier,
    monogramText: String = businessName.take(2).uppercase(),
    cashierName: String? = null,
    shiftInfo: String? = null,
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    pendingSyncCount: Int = 0,
    isPrinterConnected: Boolean = false,
    isPrinterConnecting: Boolean = false,
    pendingPrintCount: Int = 0,
    onPrinterClick: (() -> Unit)? = null,
    onAdminClick: (() -> Unit)? = null,
    navItems: List<NavItem> = emptyList(),
    selectedNavIndex: Int = 0,
    onNavItemSelected: (Int) -> Unit = {},
    isFocusFlowActive: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = A3Theme.colors

    val isRailVisible = !isFocusFlowActive && navItems.isNotEmpty()

    // WindowInsets untuk edge-to-edge (Android 15+ default)
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. BILAH ATAS PENUH (TopBar): Menghormati status bar tablet, menyatukan logo & nama usaha
            TopBar(
                businessName = businessName,
                monogramText = monogramText,
                showMonogram = true,
                cashierName = cashierName,
                shiftInfo = shiftInfo,
                syncStatus = syncStatus,
                pendingSyncCount = pendingSyncCount,
                isPrinterConnected = isPrinterConnected,
                isPrinterConnecting = isPrinterConnecting,
                pendingPrintCount = pendingPrintCount,
                onPrinterClick = onPrinterClick,
                onAdminClick = onAdminClick,
                modifier = Modifier
                    .padding(top = statusBarTop)
                    .testTag(APP_SHELL_TOP_BAR_TAG)
            )

            // 2. AREA DI BAWAH TOPBAR:
            // Sisi Kiri: Bilah Navigasi Mengambang (Floating Left Dock)
            // Sisi Kanan: Viewport Konten Utama
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Bilah Navigasi Mengambang di Sisi Kiri
                A3NavigationRail(
                    items = navItems,
                    selectedIndex = selectedNavIndex,
                    onItemSelected = onNavItemSelected,
                    onAdminClick = onAdminClick,
                    visible = isRailVisible,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .testTag(APP_SHELL_NAV_RAIL_TAG)
                )

                // Viewport Konten Utama (Katalog & Tiket Pesanan)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content(PaddingValues(bottom = safeDrawing.calculateBottomPadding()))
                }
            }
        }
    }
}
