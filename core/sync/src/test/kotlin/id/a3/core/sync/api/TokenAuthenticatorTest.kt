package id.a3.core.sync.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import id.a3.core.auth.AuthManager
import id.a3.core.auth.api.AuthApiService
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.auth.model.TokenResponse
import id.a3.core.auth.model.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TokenAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cryptoManager: TinkCryptoManager
    private lateinit var encryptedDataStore: EncryptedAuthDataStore
    private lateinit var authApiService: AuthApiService
    private lateinit var authManager: AuthManager
    private lateinit var tokenAuthenticator: TokenAuthenticator

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        cryptoManager = TinkCryptoManager.create(context)
        encryptedDataStore = EncryptedAuthDataStore(
            dataStore = InMemoryPreferencesDataStore(),
            cryptoManager = cryptoManager
        )
        encryptedDataStore.clearAuth()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApiService = retrofit.create(AuthApiService::class.java)
        authManager = AuthManager(
            encryptedDataStore = encryptedDataStore,
            apiService = authApiService,
            appId = "cafe",
            appVersion = "1.0.0"
        )

        tokenAuthenticator = TokenAuthenticator(
            authManager = authManager,
            deviceCredentialProvider = { "cred-device-test" }
        )
    }

    @After
    fun tearDown() = runBlocking {
        encryptedDataStore.clearAuth()
        mockWebServer.shutdown()
    }

    private fun create401Response(token: String?, priorCount: Int = 0): Response {
        val requestBuilder = Request.Builder()
            .url(mockWebServer.url("/v1/sync/bundles"))
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        val request = requestBuilder.build()

        var currentResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()

        for (i in 1..priorCount) {
            currentResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .priorResponse(currentResponse)
                .build()
        }

        return currentResponse
    }

    @Test
    fun `authenticator refreshes token and returns request with new token on 401`() = runBlocking {
        // Setup existing token di DataStore
        val initialToken = TokenResponse(
            accessToken = "expired_token_123",
            refreshToken = "valid_refresh_456",
            expiresIn = 3600,
            user = UserDto(id = "1", name = "Kasir", role = "kasir"),
            businessUnitId = null,
            serverTime = "2026-11-03T10:00:00Z"
        )
        encryptedDataStore.saveAuth(initialToken, "1234")

        // Mock respon refresh token dari server
        val refreshResponse = """
            {
                "access_token": "fresh_token_789",
                "refresh_token": "new_refresh_999",
                "expires_in": 3600,
                "server_time": "2026-11-03T10:00:05Z",
                "user": {
                    "id": 1,
                    "name": "Kasir",
                    "role": "kasir"
                }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(refreshResponse))

        val response401 = create401Response(token = "expired_token_123")
        val retryRequest = tokenAuthenticator.authenticate(null, response401)

        assertNotNull("Request harus di-retry dengan request baru", retryRequest)
        assertEquals(
            "Authorization header harus menggunakan token baru",
            "Bearer fresh_token_789",
            retryRequest!!.header("Authorization")
        )
        assertEquals("Token baru harus tersimpan di authManager", "fresh_token_789", authManager.getAccessToken())
    }

    @Test
    fun `authenticator stops infinite retry when response count is 3 or more`() = runBlocking {
        val initialToken = TokenResponse(
            accessToken = "expired_token_123",
            refreshToken = "valid_refresh_456",
            expiresIn = 3600,
            user = UserDto(id = "1", name = "Kasir", role = "kasir"),
            businessUnitId = null,
            serverTime = "2026-11-03T10:00:00Z"
        )
        encryptedDataStore.saveAuth(initialToken, "1234")

        // Buat response dengan 3 kali priorResponse
        val responseWithPrior = create401Response(token = "expired_token_123", priorCount = 3)
        val retryRequest = tokenAuthenticator.authenticate(null, responseWithPrior)

        assertNull("Authenticator harus mengembalikan null untuk memutus infinite loop", retryRequest)
    }

    @Test
    fun `authenticator uses already refreshed token if another coroutine has updated it`() = runBlocking {
        // DataStore sudah menyimpan token baru dari coroutine lain
        val updatedToken = TokenResponse(
            accessToken = "already_updated_token_888",
            refreshToken = "valid_refresh_456",
            expiresIn = 3600,
            user = UserDto(id = "1", name = "Kasir", role = "kasir"),
            businessUnitId = null,
            serverTime = "2026-11-03T10:00:00Z"
        )
        encryptedDataStore.saveAuth(updatedToken, "1234")

        // Request yang gagal membawa token lama (stale)
        val responseWithStaleToken = create401Response(token = "stale_token_111")
        val retryRequest = tokenAuthenticator.authenticate(null, responseWithStaleToken)

        assertNotNull(retryRequest)
        assertEquals(
            "Request langsung memakai token baru tanpa memanggil endpoint refresh",
            "Bearer already_updated_token_888",
            retryRequest!!.header("Authorization")
        )
        assertEquals("MockWebServer tidak boleh menerima panggilan refresh", 0, mockWebServer.requestCount)
    }

    @Test
    fun `authenticator returns null when refresh token fails`() = runBlocking {
        val initialToken = TokenResponse(
            accessToken = "expired_token_123",
            refreshToken = "invalid_refresh_456",
            expiresIn = 3600,
            user = UserDto(id = "1", name = "Kasir", role = "kasir"),
            businessUnitId = null,
            serverTime = "2026-11-03T10:00:00Z"
        )
        encryptedDataStore.saveAuth(initialToken, "1234")

        // Server merespon 401 saat refresh dipanggil (sesi habis total)
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Refresh token expired"}"""))

        val response401 = create401Response(token = "expired_token_123")
        val retryRequest = tokenAuthenticator.authenticate(null, response401)

        assertNull("Jika refresh gagal, authenticator harus mengembalikan null", retryRequest)
    }
}
