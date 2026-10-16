package id.a3.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import id.a3.core.ui.theme.CafeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `A3MoneyText formats rupiah correctly`() {
        composeTestRule.setContent {
            CafeTheme {
                A3MoneyText(amount = 61400L)
            }
        }

        composeTestRule.onNodeWithText("Rp 61.400").assertIsDisplayed()
    }

    @Test
    fun `A3MoneyText shows free text for zero when configured`() {
        composeTestRule.setContent {
            CafeTheme {
                A3MoneyText(amount = 0L, showZeroAsFree = true)
            }
        }

        composeTestRule.onNodeWithText("Gratis").assertIsDisplayed()
    }

    @Test
    fun `A3StatusChip displays label and icon`() {
        composeTestRule.setContent {
            CafeTheme {
                A3StatusChip(
                    label = "Lunas",
                    token = StatusToken.SUCCESS,
                    icon = Icons.Outlined.CheckCircle
                )
            }
        }

        composeTestRule.onNodeWithText("Lunas").assertIsDisplayed()
    }

    @Test
    fun `A3Button displays label`() {
        composeTestRule.setContent {
            CafeTheme {
                A3Button(label = "Bayar Rp61.400", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("Bayar Rp61.400").assertIsDisplayed()
    }

    @Test
    fun `A3EmptyState displays title and description`() {
        composeTestRule.setContent {
            CafeTheme {
                A3EmptyState(
                    title = "Belum ada pesanan",
                    description = "Tambahkan item dari menu untuk memulai."
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada pesanan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tambahkan item dari menu untuk memulai.").assertIsDisplayed()
    }

    @Test
    fun `A3ErrorState displays title and description`() {
        composeTestRule.setContent {
            CafeTheme {
                A3ErrorState(
                    title = "Gagal memuat",
                    description = "Periksa koneksi internet dan coba lagi."
                )
            }
        }

        composeTestRule.onNodeWithText("Gagal memuat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Periksa koneksi internet dan coba lagi.").assertIsDisplayed()
    }
}
