package id.a3.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import id.a3.core.domain.device.DeviceStatus
import id.a3.core.ui.theme.CafeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceRegistrationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `unregistered status renders registration form and submit button`() {
        composeTestRule.setContent {
            CafeTheme {
                DeviceRegistrationScreen(
                    deviceStatus = DeviceStatus.UNREGISTERED,
                    deviceId = "dev-uuid-1234",
                    deviceName = "Kasir 1",
                    onDeviceNameChange = {},
                    registrationCode = "REG-9999",
                    onRegistrationCodeChange = {},
                    onRegisterClick = {},
                    onCheckStatusClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(DEVICE_REG_CARD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Daftarkan Tablet").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DEVICE_REG_NAME_INPUT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DEVICE_REG_CODE_INPUT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DEVICE_REG_SUBMIT_BTN_TAG).assertExists()
        composeTestRule.onNodeWithText("Daftarkan Perangkat").assertExists()
    }

    @Test
    fun `pending status renders pending badge and check status button`() {
        composeTestRule.setContent {
            CafeTheme {
                DeviceRegistrationScreen(
                    deviceStatus = DeviceStatus.PENDING,
                    deviceId = "dev-uuid-1234",
                    deviceName = "Kasir 1",
                    onDeviceNameChange = {},
                    registrationCode = "",
                    onRegistrationCodeChange = {},
                    onRegisterClick = {},
                    onCheckStatusClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Menunggu Persetujuan Owner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pendaftaran Terkirim").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DEVICE_REG_CHECK_STATUS_BTN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Periksa Status Sekarang").assertIsDisplayed()
    }

    @Test
    fun `active status renders active badge and unit name`() {
        composeTestRule.setContent {
            CafeTheme {
                DeviceRegistrationScreen(
                    deviceStatus = DeviceStatus.ACTIVE,
                    deviceId = "dev-uuid-1234",
                    deviceName = "Kasir 1",
                    onDeviceNameChange = {},
                    registrationCode = "",
                    onRegistrationCodeChange = {},
                    onRegisterClick = {},
                    onCheckStatusClick = {},
                    businessUnitName = "AA Coffeeshop Pusat"
                )
            }
        }

        composeTestRule.onNodeWithText("Perangkat Aktif").assertIsDisplayed()
        composeTestRule.onNodeWithText("AA Coffeeshop Pusat").assertIsDisplayed()
    }

    @Test
    fun `revoked status renders revoked lockout card`() {
        composeTestRule.setContent {
            CafeTheme {
                DeviceRegistrationScreen(
                    deviceStatus = DeviceStatus.REVOKED,
                    deviceId = "dev-uuid-1234",
                    deviceName = "Kasir 1",
                    onDeviceNameChange = {},
                    registrationCode = "",
                    onRegistrationCodeChange = {},
                    onRegisterClick = {},
                    onCheckStatusClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(DEVICE_REVOKED_BANNER_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Akses Tablet Dicabut").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device ID: dev-uuid-1234").assertIsDisplayed()
    }
}
