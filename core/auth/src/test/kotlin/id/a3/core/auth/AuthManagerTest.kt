package id.a3.core.auth

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import id.a3.core.auth.api.AuthApiService
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.auth.model.TokenResponse
import id.a3.core.auth.model.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AuthManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cryptoManager: TinkCryptoManager
    private lateinit var inMemoryDataStore: InMemoryPreferencesDataStore
    private lateinit var encryptedDataStore: EncryptedAuthDataStore
    private lateinit var authApiService: AuthApiService
    private lateinit var authManager: AuthManager

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO + Job())
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        cryptoManager = TinkCryptoManager.create(context)
        inMemoryDataStore = InMemoryPreferencesDataStore()
        encryptedDataStore = EncryptedAuthDataStore(inMemoryDataStore, cryptoManager)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApiService = retrofit.create(AuthApiService::class.java)
        authManager = AuthManager(encryptedDataStore, authApiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        testScope.cancel()
    }

    @Test
    fun `TinkCryptoManager encrypts and decrypts correctly`() {
        val originalText = "my-super-secret-jwt-token-999"
        val cipher = cryptoManager.encrypt(originalText)

        assertFalse("Ciphertext must not be equal to original text", cipher == originalText)
        assertFalse("Ciphertext must not contain original text", cipher.contains(originalText))

        val decrypted = cryptoManager.decrypt(cipher)
        assertEquals("Decrypted text must match original", originalText, decrypted)
    }

    @Test
    fun `real file storage encrypts tokens so plain text is not visible in raw disk file`() = runBlocking {
        val realDiskFile = File(tempFolder.root, "disk_auth_${UUID.randomUUID()}.preferences_pb")
        val realFileStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { realDiskFile }
        )
        val fileAuthStore = EncryptedAuthDataStore(realFileStore, cryptoManager)

        val plainAccessToken = "ey-jwt-secret-access-token-12345"
        val plainRefreshToken = "ey-jwt-secret-refresh-token-67890"
        val plainPin = "123456"

        val tokenResponse = TokenResponse(
            accessToken = plainAccessToken,
            refreshToken = plainRefreshToken,
            expiresIn = 3600,
            user = UserDto(id = "u-1", name = "Budi Kasir", role = "kasir"),
            businessUnitId = "bu-cafe-01",
            serverTime = "2026-10-14T10:00:00Z"
        )

        // Save auth to real file on disk
        fileAuthStore.saveAuth(tokenResponse, plainPin)

        // Read physical file content directly from disk
        assertTrue("DataStore file must exist on disk", realDiskFile.exists())
        val rawDiskContent = realDiskFile.readText()

        // CRITICAL ACCEPTANCE VERIFICATION: Token and PIN must NOT appear in plain text
        assertFalse(
            "Plain text access token MUST NOT be stored in DataStore file",
            rawDiskContent.contains(plainAccessToken)
        )
        assertFalse(
            "Plain text refresh token MUST NOT be stored in DataStore file",
            rawDiskContent.contains(plainRefreshToken)
        )
        assertFalse(
            "Plain text PIN MUST NOT be stored in DataStore file",
            rawDiskContent.contains(plainPin)
        )

        // Verify tokens can be decrypted through secure accessors
        assertEquals(plainAccessToken, fileAuthStore.getAccessToken())
        assertEquals(plainRefreshToken, fileAuthStore.getRefreshToken())
        assertTrue(fileAuthStore.verifyOfflinePin(plainPin))
    }

    @Test
    fun `online login sets session and decrypts tokens`() = runBlocking {
        val plainAccessToken = "ey-access-token-secret-12345"
        val plainRefreshToken = "ey-refresh-token-secret-67890"
        val pin = "123456"

        val serverResponse = """
            {
                "access_token": "$plainAccessToken",
                "refresh_token": "$plainRefreshToken",
                "expires_in": 3600,
                "user": {
                    "id": "u-kasir-1",
                    "name": "Budi Kasir",
                    "role": "kasir"
                },
                "business_unit_id": "bu-cafe-01",
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverResponse))

        val result = authManager.loginWithPinOnline(userId = "u-kasir-1", pin = pin)
        assertTrue("Login must be successful", result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("Budi Kasir", user.name)
        assertTrue("App must be unlocked after login", authManager.isUnlocked.value)

        // Verify tokens can be retrieved decrypted
        assertEquals(plainAccessToken, authManager.getAccessToken())
        assertEquals(plainRefreshToken, authManager.getRefreshToken())
        assertEquals("bu-cafe-01", authManager.getBusinessUnitId())
    }

    @Test
    fun `offline unlock works with correct PIN and fails with wrong PIN without internet`() = runBlocking {
        val plainAccessToken = "secret-token-abc"
        val plainRefreshToken = "secret-refresh-xyz"
        val correctPin = "654321"

        val serverResponse = """
            {
                "access_token": "$plainAccessToken",
                "refresh_token": "$plainRefreshToken",
                "expires_in": 3600,
                "user": {
                    "id": "u-kasir-2",
                    "name": "Siti Staf",
                    "role": "staf_laundry"
                },
                "business_unit_id": "bu-laundry-01",
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverResponse))

        authManager.loginWithPinOnline(userId = "u-kasir-2", pin = correctPin)
        assertTrue(authManager.isUnlocked.value)

        // Lock device (e.g. idle timeout / change cashier)
        authManager.lock()
        assertFalse("App must be locked", authManager.isUnlocked.value)

        // Offline attempt with WRONG PIN (mockWebServer shutdown to prove zero network used)
        mockWebServer.shutdown()

        val wrongUnlock = authManager.unlockWithPinOffline("000000")
        assertFalse("Wrong PIN must fail offline unlock", wrongUnlock)
        assertFalse("App remains locked on wrong PIN", authManager.isUnlocked.value)

        // Offline attempt with CORRECT PIN (offline)
        val correctUnlock = authManager.unlockWithPinOffline(correctPin)
        assertTrue("Correct PIN must succeed offline unlock", correctUnlock)
        assertTrue("App must be unlocked", authManager.isUnlocked.value)

        // Decrypted tokens are still available
        assertEquals(plainAccessToken, authManager.getAccessToken())
    }

    @Test
    fun `logout clears all tokens and locks session`() = runBlocking {
        val serverResponse = """
            {
                "access_token": "token-to-delete",
                "refresh_token": "refresh-to-delete",
                "expires_in": 3600,
                "user": {
                    "id": "u-1",
                    "name": "Manager",
                    "role": "manager"
                },
                "business_unit_id": "bu-cafe-01",
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverResponse))

        val result = authManager.loginWithPinOnline(userId = "u-1", pin = "1234")
        assertTrue(result is AuthResult.Success)
        assertTrue(authManager.isUnlocked.value)
        assertNotNull(authManager.getAccessToken())

        // Perform logout
        authManager.logout()

        assertFalse("Session must be locked after logout", authManager.isUnlocked.value)
        assertNull("Access token must be null after logout", authManager.getAccessToken())
        assertNull("Refresh token must be null after logout", authManager.getRefreshToken())
        assertNull("User must be null after logout", authManager.getCurrentUser())
    }

    @Test
    fun `refreshToken updates tokens on successful server response`() = runBlocking {
        val initialResponse = """
            {
                "access_token": "initial-access-token",
                "refresh_token": "initial-refresh-token",
                "expires_in": 3600,
                "user": {
                    "id": "u-1",
                    "name": "Budi",
                    "role": "kasir"
                },
                "business_unit_id": "bu-cafe-01",
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(initialResponse))

        authManager.loginWithPinOnline(userId = "u-1", pin = "1234")
        assertEquals("initial-access-token", authManager.getAccessToken())

        // Next response: refresh token
        val refreshedResponse = """
            {
                "access_token": "new-refreshed-access-token",
                "refresh_token": "new-refreshed-refresh-token",
                "expires_in": 3600,
                "user": {
                    "id": "u-1",
                    "name": "Budi",
                    "role": "kasir"
                },
                "business_unit_id": "bu-cafe-01",
                "server_time": "2026-10-14T11:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(refreshedResponse))

        val refreshSuccess = authManager.refreshToken()
        assertTrue(refreshSuccess)
        assertEquals("new-refreshed-access-token", authManager.getAccessToken())
        assertEquals("new-refreshed-refresh-token", authManager.getRefreshToken())
    }

    @Test
    fun `logout clears only cashier session and preserves device registration and status`() = runBlocking {
        encryptedDataStore.saveDeviceRegistration(
            deviceId = "dev-test-123",
            deviceName = "Tablet Kasir Utama",
            status = id.a3.core.domain.device.DeviceStatus.ACTIVE
        )
        encryptedDataStore.updateDeviceStatus(
            status = id.a3.core.domain.device.DeviceStatus.ACTIVE,
            credential = "secret-device-credential-xyz",
            businessUnitId = "bu-cafe-01"
        )

        val serverResponse = """
            {
                "access_token": "token-abc",
                "refresh_token": "ref-xyz",
                "expires_in": 3600,
                "user": {
                    "id": "u-1",
                    "name": "Budi",
                    "role": "kasir"
                },
                "business_unit_id": "bu-cafe-01",
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(serverResponse))
        authManager.loginWithPinOnline(userId = "u-1", pin = "1234")

        assertTrue(authManager.isUnlocked.value)
        assertNotNull(authManager.getAccessToken())

        authManager.logout()

        assertFalse("Session kasir harus terkunci", authManager.isUnlocked.value)
        assertNull("Access token harus null", authManager.getAccessToken())
        assertNull("User harus null", authManager.getCurrentUser())

        assertEquals(id.a3.core.domain.device.DeviceStatus.ACTIVE, encryptedDataStore.getDeviceStatus())
        assertEquals("secret-device-credential-xyz", encryptedDataStore.getDeviceCredential())
        assertEquals("dev-test-123", encryptedDataStore.getOrCreateDeviceId())
        assertEquals("Tablet Kasir Utama", encryptedDataStore.getDeviceName())
    }

    @Test
    fun `unlockWithPinOffline rejects the old default manager PIN 4444 when no cashier was logged in`() = runBlocking {
        authManager.lock()
        assertFalse(authManager.isUnlocked.value)

        val unlocked = authManager.unlockWithPinOffline("4444")
        assertFalse("PIN bawaan lama tidak boleh membuka kunci", unlocked)
        assertFalse(authManager.isUnlocked.value)
    }
}
