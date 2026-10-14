package id.a3.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.sync.api.SyncApiService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncSenderIdempotencyTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var apiService: SyncApiService
    private lateinit var syncSender: SyncSender
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        apiService = retrofit.create(SyncApiService::class.java)
        syncSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        database.close()
    }

    @Test
    fun `bundle sent twice receives ACCEPTED first then DUPLICATE and both mark outbox as SENT`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-14T10:00:00Z",
                    "payload": {
                        "order_id": "${UUID.randomUUID()}",
                        "display_number": "C1-0001",
                        "order_type": "dine_in",
                        "lines": [],
                        "subtotal": 50000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 50000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        // 1. First send: Server responds with ACCEPTED
        val firstResponseJson = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "ACCEPTED",
                        "server_received_at": "2026-10-14T10:00:01Z"
                    }
                ],
                "server_time": "2026-10-14T10:00:01Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(firstResponseJson))

        val summary1 = syncSender.sendPendingBundles()
        assertEquals(1, summary1.sent)
        assertEquals(0, summary1.duplicates)
        assertEquals(0, summary1.failed)

        val outboxAfterFirst = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(outboxAfterFirst)
        assertEquals(OutboxStatus.SENT, outboxAfterFirst!!.status)

        // 2. Second send (simulate network retry or duplicate trigger): Reset outbox to PENDING
        database.outboxDao().insert(outbox.copy(status = OutboxStatus.PENDING))

        // Server detects duplicate bundle_id in sync_bundles and responds with DUPLICATE
        val duplicateResponseJson = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "DUPLICATE",
                        "server_received_at": "2026-10-14T10:00:01Z"
                    }
                ],
                "server_time": "2026-10-14T10:00:05Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(duplicateResponseJson))

        val summary2 = syncSender.sendPendingBundles()
        assertEquals(0, summary2.sent)
        assertEquals(1, summary2.duplicates)
        assertEquals(0, summary2.failed)

        val outboxAfterDuplicate = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(outboxAfterDuplicate)
        assertEquals("DUPLICATE must be treated as successful idempotent delivery", OutboxStatus.SENT, outboxAfterDuplicate!!.status)

        // Verify request payload contained exactly 1 bundle with bundleId
        val req1 = mockWebServer.takeRequest()
        val req2 = mockWebServer.takeRequest()
        assertEquals("/v1/sync/bundles", req1.path)
        assertEquals("/v1/sync/bundles", req2.path)
    }

    @Test
    fun `rejected non-retryable bundle marks outbox as FAILED`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = null,
            eventsJson = "[]",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val rejectResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "REJECTED",
                        "reason_code": "VALIDATION_FAILED",
                        "retryable": false,
                        "message": "Field wajib kosong"
                    }
                ],
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(rejectResponse))

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.failed)

        val outboxResult = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(outboxResult)
        assertEquals(OutboxStatus.FAILED, outboxResult!!.status)
        assertEquals("Field wajib kosong", outboxResult.lastError)
    }

    @Test
    fun `rejected retryable bundle keeps outbox PENDING and increments attempts`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = null,
            eventsJson = "[]",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val retryableResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "REJECTED",
                        "reason_code": "DEPENDENCY_MISSING",
                        "retryable": true,
                        "message": "Shift belum sinkron"
                    }
                ],
                "server_time": "2026-10-14T10:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(retryableResponse))

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.failed)

        val outboxResult = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(outboxResult)
        assertEquals(OutboxStatus.PENDING, outboxResult!!.status)
        assertEquals(1, outboxResult.attempts)
        assertEquals("Shift belum sinkron", outboxResult.lastError)
    }
}
