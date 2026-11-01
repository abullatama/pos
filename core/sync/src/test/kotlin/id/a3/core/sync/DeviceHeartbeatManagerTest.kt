package id.a3.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import id.a3.core.auth.DeviceHeartbeatResult
import id.a3.core.auth.DeviceManager
import id.a3.core.auth.api.DeviceApiService
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID

private class TestInMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceHeartbeatManagerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var deviceManager: DeviceManager
    private lateinit var heartbeatManager: DeviceHeartbeatManager
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)

        val cryptoManager = TinkCryptoManager.create(context)
        val dataStore = EncryptedAuthDataStore(
            dataStore = TestInMemoryPreferencesDataStore(),
            cryptoManager = cryptoManager
        )

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(DeviceApiService::class.java)

        deviceManager = DeviceManager(
            encryptedDataStore = dataStore,
            apiService = apiService,
            appId = "cafe",
            appVersion = "1.0.0"
        )

        heartbeatManager = DeviceHeartbeatManager(
            deviceManager = deviceManager,
            outboxDao = database.outboxDao(),
            printerStatusProvider = { "ok" }
        )
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `executeHeartbeat skips when device is not active`() = runBlocking {
        deviceManager.initialize()
        val result = heartbeatManager.executeHeartbeat()

        assertEquals(DeviceHeartbeatResult.SkippedNotActive, result)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `executeHeartbeat collects outbox count and sends payload successfully`() = runBlocking {
        deviceManager.activateForDebug("Tablet Kasir Bar")
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        // Insert 2 pending and 1 failed outbox
        database.outboxDao().insert(
            OutboxEntity(
                bundleId = UUID.randomUUID().toString(),
                deviceId = "dev-01",
                shiftId = "shift-01",
                eventsJson = "[]",
                status = OutboxStatus.PENDING
            )
        )
        database.outboxDao().insert(
            OutboxEntity(
                bundleId = UUID.randomUUID().toString(),
                deviceId = "dev-01",
                shiftId = "shift-01",
                eventsJson = "[]",
                status = OutboxStatus.PENDING
            )
        )
        database.outboxDao().insert(
            OutboxEntity(
                bundleId = UUID.randomUUID().toString(),
                deviceId = "dev-01",
                shiftId = "shift-01",
                eventsJson = "[]",
                status = OutboxStatus.FAILED
            )
        )

        val result = heartbeatManager.executeHeartbeat()

        assertEquals(DeviceHeartbeatResult.Success, result)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/devices/heartbeat", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"outbox_pending\":2"))
        assertTrue(body.contains("\"outbox_problem\":1"))
        assertTrue(body.contains("\"printer_status\":\"ok\""))
    }

    @Test
    fun `executeHeartbeat returns DeviceRevoked on 403`() = runBlocking {
        deviceManager.activateForDebug("Tablet Kasir")
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val result = heartbeatManager.executeHeartbeat()

        assertEquals(DeviceHeartbeatResult.DeviceRevoked, result)
    }
}
