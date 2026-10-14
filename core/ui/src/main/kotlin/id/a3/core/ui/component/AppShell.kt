package id.a3.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.a3.core.ui.theme.A3Theme

const val APP_SHELL_TOP_BAR_TAG = "app_shell_top_bar"
const val APP_SHELL_BOTTOM_BAR_TAG = "app_shell_bottom_bar"

/**
 * Kerangka layar standar tablet landscape 11 inci (A3 POS):
 * - Bilah atas 64 dp: nama usaha, kasir/shift, indikator sinkronisasi, status printer, dan tombol admin.
 * - Area konten utama: dengan padding bawah dinamis agar tidak terhalang dok navigasi.
 * - Navigasi bawah mengambang: berjarak 16 dp dari tepi bawah layar, di tengah konten.
 *
 * Aturan UX Krusial (layout-standard.md):
 * Ketika [isFocusFlowActive] == true (alur pembayaran, input PIN, buka/tutup shift, atau cetak),
 * navigasi bawah otomatis disembunyikan agar kasir fokus tanpa distraksi.
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
    onAdminClick: (() -> Unit)? = null,
    navItems: List<NavItem> = emptyList(),
    selectedNavIndex: Int = 0,
    onNavItemSelected: (Int) -> Unit = {},
    isFocusFlowActive: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = A3Theme.colors

    // Jika alur fokus aktif (misal pembayaran), navigasi disembunyikan
    val isBottomBarVisible = !isFocusFlowActive && navItems.isNotEmpty()

    // Hitung safe padding bawah untuk konten
    val contentBottomPadding = if (isBottomBarVisible) 88.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Bilah Atas 64 dp
            TopBar(
                businessName = businessName,
                monogramText = monogramText,
                cashierName = cashierName,
                shiftInfo = shiftInfo,
                syncStatus = syncStatus,
                pendingSyncCount = pendingSyncCount,
                isPrinterConnected = isPrinterConnected,
                onAdminClick = onAdminClick,
                modifier = Modifier.testTag(APP_SHELL_TOP_BAR_TAG)
            )

            // Area Konten Utama
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                content(PaddingValues(bottom = contentBottomPadding))
            }
        }

        // Dok Navigasi Bawah Mengambang (16 dp dari dasar layar)
        FloatingBottomBar(
            items = navItems,
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavItemSelected,
            visible = isBottomBarVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .testTag(APP_SHELL_BOTTOM_BAR_TAG)
        )
    }
}
