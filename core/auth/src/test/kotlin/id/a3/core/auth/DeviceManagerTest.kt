package id.a3.core.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.auth.api.DeviceApiService
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.domain.device.DeviceStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceManagerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cryptoManager: TinkCryptoManager
    private lateinit var inMemoryDataStore: InMemoryPreferencesDataStore
    private lateinit var encryptedDataStore: EncryptedAuthDataStore
    private lateinit var deviceApiService: DeviceApiService
    private lateinit var deviceManager: DeviceManager

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
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

        deviceApiService = retrofit.create(DeviceApiService::class.java)
        deviceManager = DeviceManager(
            encryptedDataStore = encryptedDataStore,
            apiService = deviceApiService,
            appId = "cafe",
            appVersion = "1.0.0"
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initial status is UNREGISTERED and creates persistent UUID device_id`() = runBlocking {
        deviceManager.initialize()

        assertEquals(DeviceStatus.UNREGISTERED, deviceManager.deviceStatus.value)
        val deviceId = deviceManager.getDeviceId()
        assertNotNull(deviceId)
        assertTrue(deviceId.isNotBlank())
        // Persistent check
        assertEquals(deviceId, deviceManager.getDeviceId())
    }

    @Test
    fun `successful registerDevice with 202 sets status to PENDING and saves name`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"PENDING"}""")
        )

        val result = deviceManager.registerDevice("REG-12345", "Tablet Kasir Utama")

        assertTrue(result is DeviceRegisterResult.PendingApproval)
        assertEquals(DeviceStatus.PENDING, deviceManager.deviceStatus.value)
        assertEquals("Tablet Kasir Utama", deviceManager.getDeviceName())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/v1/devices/register", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.body.readUtf8().contains("REG-12345"))
    }

    @Test
    fun `registerDevice with blank code or blank name fails without network call`() = runBlocking {
        val resultBlankCode = deviceManager.registerDevice("   ", "Tablet Kasir")
        assertTrue(resultBlankCode is DeviceRegisterResult.Error)

        val resultBlankName = deviceManager.registerDevice("REG-123", "   ")
        assertTrue(resultBlankName is DeviceRegisterResult.Error)

        assertEquals(0, mockWebServer.requestCount)
        assertEquals(DeviceStatus.UNREGISTERED, deviceManager.deviceStatus.value)
    }

    @Test
    fun `registerDevice with 409 conflict returns error and remains UNREGISTERED`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"CODE_ALREADY_USED","message":"Kode pendaftaran sudah dipakai"}""")
        )

        val result = deviceManager.registerDevice("REG-USED", "Tablet 2")

        assertTrue(result is DeviceRegisterResult.Error)
        assertEquals(DeviceStatus.UNREGISTERED, deviceManager.deviceStatus.value)
    }

    @Test
    fun `checkStatus with PENDING maintains PENDING status`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"PENDING"}""")
        )

        val result = deviceManager.checkStatus()

        assertTrue(result is DeviceCheckResult.StatusUpdated)
        assertEquals(DeviceStatus.PENDING, (result as DeviceCheckResult.StatusUpdated).status)
        assertEquals(DeviceStatus.PENDING, deviceManager.deviceStatus.value)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/devices/me", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `checkStatus sends X-Registration-Code when device is pending approval`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"PENDING"}""")
        )
        deviceManager.registerDevice("REG-839201", "Tablet Kasir")
        mockWebServer.takeRequest() // consume register request

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"PENDING"}""")
        )

        deviceManager.checkStatus()
        val recordedMe = mockWebServer.takeRequest()
        assertEquals("REG-839201", recordedMe.getHeader("X-Registration-Code"))
    }

    @Test
    fun `checkStatus with ACTIVE saves encrypted device credential and updates status to ACTIVE`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{
                    "status":"ACTIVE",
                    "business_unit_id":"bu-cafe-01",
                    "device_credential":"secret-device-token-abc"
                }""".trimIndent())
        )

        val result = deviceManager.checkStatus()

        assertTrue(result is DeviceCheckResult.StatusUpdated)
        assertEquals(DeviceStatus.ACTIVE, (result as DeviceCheckResult.StatusUpdated).status)
        assertEquals(DeviceStatus.ACTIVE, deviceManager.deviceStatus.value)

        // Credential is encrypted and accessible
        assertEquals("secret-device-token-abc", deviceManager.getDeviceCredential())
    }

    @Test
    fun `checkStatus with REVOKED marks device revoked and wipes credentials`() = runBlocking {
        // Setup existing active state
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ACTIVE","device_credential":"temp-cred"}""")
        )
        deviceManager.checkStatus()
        assertEquals(DeviceStatus.ACTIVE, deviceManager.deviceStatus.value)

        // Now server returns REVOKED
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"REVOKED"}""")
        )

        val result = deviceManager.checkStatus()

        assertTrue(result is DeviceCheckResult.StatusUpdated)
        assertEquals(DeviceStatus.REVOKED, (result as DeviceCheckResult.StatusUpdated).status)
        assertEquals(DeviceStatus.REVOKED, deviceManager.deviceStatus.value)
        assertNull(deviceManager.getDeviceCredential())
    }

    @Test
    fun `checkStatus with 401 or 403 marks device revoked immediately`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":"DEVICE_REVOKED","message":"Perangkat telah dicabut"}""")
        )

        val result = deviceManager.checkStatus()

        assertTrue(result is DeviceCheckResult.StatusUpdated)
        assertEquals(DeviceStatus.REVOKED, (result as DeviceCheckResult.StatusUpdated).status)
        assertEquals(DeviceStatus.REVOKED, deviceManager.deviceStatus.value)
        assertNull(deviceManager.getDeviceCredential())
    }

    @Test
    fun `activateForDebug sets status to ACTIVE and saves credentials locally without network`() = runBlocking {
        deviceManager.initialize()
        assertEquals(DeviceStatus.UNREGISTERED, deviceManager.deviceStatus.value)

        deviceManager.activateForDebug("Tablet Uji Coba")

        assertEquals(DeviceStatus.ACTIVE, deviceManager.deviceStatus.value)
        assertEquals("Tablet Uji Coba", deviceManager.getDeviceName())
        assertEquals("demo-device-credential-local", deviceManager.getDeviceCredential())
        assertEquals(0, mockWebServer.requestCount) // Zero network calls!
    }

    @Test
    fun `resetDeviceForDebug restores status to UNREGISTERED and clears credentials`() = runBlocking {
        deviceManager.activateForDebug("Tablet Uji Coba")
        assertEquals(DeviceStatus.ACTIVE, deviceManager.deviceStatus.value)

        deviceManager.resetDeviceForDebug()

        assertEquals(DeviceStatus.UNREGISTERED, deviceManager.deviceStatus.value)
        assertNull(deviceManager.getDeviceCredential())
    }

    @Test
    fun `sendHeartbeat sends telemetry payload to POST v1 devices heartbeat with 204 response`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val response = deviceApiService.sendHeartbeat(
            request = id.a3.core.auth.model.DeviceHeartbeatRequest(
                appVersion = "1.2.0",
                outboxPending = 2,
                outboxProblem = 0,
                batteryPercent = 85,
                lastSyncAt = "2026-10-20T10:00:00Z",
                printerStatus = "ok"
            ),
            deviceCredential = "test_dev_cred"
        )

        assertTrue(response.isSuccessful)
        assertEquals(204, response.code())

        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/devices/heartbeat", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("test_dev_cred", recorded.getHeader("X-Device-Credential"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"app_version\":\"1.2.0\""))
        assertTrue(body.contains("\"battery_percent\":85"))
        assertTrue(body.contains("\"printer_status\":\"ok\""))
    }
}
