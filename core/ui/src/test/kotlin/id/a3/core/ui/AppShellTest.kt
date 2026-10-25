package id.a3.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import id.a3.core.ui.component.APP_SHELL_BOTTOM_BAR_TAG
import id.a3.core.ui.component.APP_SHELL_TOP_BAR_TAG
import id.a3.core.ui.component.AppShell
import id.a3.core.ui.component.NavItem
import id.a3.core.ui.component.SyncStatus
import id.a3.core.ui.theme.CafeColors
import id.a3.core.ui.theme.CafeShapes
import id.a3.core.ui.theme.CafeTheme
import id.a3.core.ui.theme.LaundryColors
import id.a3.core.ui.theme.LaundryShapes
import id.a3.core.ui.theme.LaundryTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testNavItems = listOf(
        NavItem("kasir", "Kasir", Icons.Outlined.PointOfSale),
        NavItem("pesanan", "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong),
        NavItem("shift", "Shift", Icons.Outlined.AccessTime)
    )

    @Test
    fun `theme tokens define distinct branding and shapes for cafe and laundry`() {
        assertNotEquals(CafeColors.canvas, LaundryColors.canvas)
        assertNotEquals(CafeColors.accent, LaundryColors.accent)
        assertNotEquals(CafeShapes.bottomBarShape, LaundryShapes.bottomBarShape)
    }

    @Test
    fun `app shell renders top bar, sync badge, and content in normal flow`() {
        composeTestRule.setContent {
            CafeTheme {
                AppShell(
                    businessName = "AA COFFEESHOP",
                    cashierName = "Budi Santoso",
                    shiftInfo = "Shift 1",
                    syncStatus = SyncStatus.SYNCED,
                    isPrinterConnected = true,
                    navItems = testNavItems,
                    selectedNavIndex = 0,
                    isFocusFlowActive = false
                ) {
                    Text("Konten Utama POS")
                }
            }
        }

        // Top Bar elements
        composeTestRule.onNodeWithTag(APP_SHELL_TOP_BAR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Budi Santoso · Shift 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tersinkron").assertIsDisplayed()
        composeTestRule.onNodeWithText("Printer Siap").assertIsDisplayed()

        // Content
        composeTestRule.onNodeWithText("Konten Utama POS").assertIsDisplayed()

        // Floating Bottom Bar must be visible
        composeTestRule.onNodeWithTag(APP_SHELL_BOTTOM_BAR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Kasir").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pesanan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shift").assertIsDisplayed()
    }

    @Test
    fun `floating bottom bar is HIDDEN when isFocusFlowActive is true`() {
        composeTestRule.setContent {
            CafeTheme {
                AppShell(
                    businessName = "AA COFFEESHOP",
                    cashierName = "Budi Santoso",
                    shiftInfo = "Shift 1",
                    syncStatus = SyncStatus.SYNCED,
                    navItems = testNavItems,
                    selectedNavIndex = 0,
                    isFocusFlowActive = true // Payment / PIN / Shift focus flow active!
                ) {
                    Text("Layar Pembayaran Tunai")
                }
            }
        }

        // Content is displayed
        composeTestRule.onNodeWithText("Layar Pembayaran Tunai").assertIsDisplayed()

        // Floating Bottom Bar must NOT exist or be displayed
        composeTestRule.onNodeWithTag(APP_SHELL_BOTTOM_BAR_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("Kasir").assertDoesNotExist()
    }

    @Test
    fun `sync status indicator reflects pending count and error correctly`() {
        composeTestRule.setContent {
            LaundryTheme {
                AppShell(
                    businessName = "ATHIA LAUNDRY HOUSE",
                    syncStatus = SyncStatus.PENDING,
                    pendingSyncCount = 5,
                    isPrinterConnected = false,
                    navItems = testNavItems
                ) {
                    Text("Area Laundry")
                }
            }
        }

        composeTestRule.onNodeWithText("5 Menunggu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Printer Putus").assertIsDisplayed()
    }

    @Test
    fun `navigation rail highlights admin button when isAdminSelected is true`() {
        composeTestRule.setContent {
            CafeTheme {
                AppShell(
                    businessName = "AA COFFEESHOP",
                    navItems = testNavItems,
                    selectedNavIndex = -1,
                    isAdminSelected = true,
                    onAdminClick = {}
                ) {
                    Text("Halaman Admin")
                }
            }
        }

        composeTestRule.onNodeWithTag(id.a3.core.ui.component.A3_NAV_RAIL_ADMIN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Admin").assertIsDisplayed()
    }
}
