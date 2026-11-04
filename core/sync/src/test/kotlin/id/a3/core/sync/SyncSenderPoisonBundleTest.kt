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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

/**
 * Satu bundel cacat (HTTP 4xx) tidak boleh menggagalkan bundel valid lain dalam permintaan yang sama.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncSenderPoisonBundleTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
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
        syncSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = retrofit.create(SyncApiService::class.java),
            json = json,
            cashOutDao = database.cashOutDao(),
            filesDirProvider = { context.filesDir }
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        database.close()
    }

    private fun tableMovedBundle(bundleId: String) = OutboxEntity(
        bundleId = bundleId,
        deviceId = "dev-cafe-1",
        shiftId = "shift-001",
        eventsJson = """
            [{"event_id":"${UUID.randomUUID()}","type":"order.table_moved",
              "occurred_at":"2026-10-14T10:00:00Z",
              "payload":{"order_id":"${UUID.randomUUID()}","table_label":"Meja 4"}}]
        """.trimIndent(),
        status = OutboxStatus.PENDING
    )

    @Test
    fun `one malformed bundle rejected with 422 does not fail the valid bundle in the same request`() = runBlocking {
        val goodId = UUID.randomUUID().toString()
        val badId = UUID.randomUUID().toString()
        database.outboxDao().insert(tableMovedBundle(goodId))
        database.outboxDao().insert(tableMovedBundle(badId))

        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.clone().readUtf8()
                val hasGood = body.contains(goodId)
                val hasBad = body.contains(badId)
                return when {
                    hasGood && !hasBad -> MockResponse().setResponseCode(200).setBody(
                        """{"results":[{"bundle_id":"$goodId","status":"ACCEPTED","server_received_at":"2026-10-14T10:00:01Z"}],"server_time":"2026-10-14T10:00:01Z"}"""
                    )
                    else -> MockResponse().setResponseCode(422).setBody(
                        """{"code":"VALIDATION_FAILED","message":"payload tidak valid"}"""
                    )
                }
            }
        }

        syncSender.sendPendingBundles()

        val good = database.outboxDao().getByBundleId(goodId)
        val bad = database.outboxDao().getByBundleId(badId)
        assertNotNull(good)
        assertNotNull(bad)
        assertEquals(OutboxStatus.SENT, good!!.status)
        assertEquals(OutboxStatus.FAILED, bad!!.status)
    }
}
