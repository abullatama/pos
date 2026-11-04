package id.a3.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.sync.api.SyncApiService
import id.a3.core.sync.model.BundleResultDto
import id.a3.core.sync.model.OrderNotificationStatusResponse
import id.a3.core.sync.model.SyncRequest
import id.a3.core.sync.model.SyncResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OutboxSyncManagerTest {

    private lateinit var database: AppDatabase
    private lateinit var syncSender: SyncSender
    private lateinit var fakeApi: FakeSyncApiService

    private class FakeSyncApiService : SyncApiService {
        var sendBundlesCallCount = 0
        var shouldSucceed = true

        override suspend fun sendBundles(
            request: SyncRequest,
            appId: String?,
            appVersion: String?
        ): Response<SyncResponse> {
            sendBundlesCallCount++
            return if (shouldSucceed) {
                val results = request.bundles.map { bundle ->
                    BundleResultDto(
                        bundleId = bundle.bundleId,
                        status = "ACCEPTED",
                        serverReceivedAt = "2026-11-01T10:00:00Z"
                    )
                }
                Response.success(SyncResponse(results = results, serverTime = "2026-11-01T10:00:00Z"))
            } else {
                Response.error(500, okhttp3.ResponseBody.create(null, "Server Error"))
            }
        }

        override suspend fun uploadFile(
            file: okhttp3.MultipartBody.Part,
            fileId: okhttp3.RequestBody,
            kind: okhttp3.RequestBody,
            appId: String?,
            appVersion: String?
        ): Response<id.a3.core.sync.model.FileUploadResponse> {
            return Response.success(id.a3.core.sync.model.FileUploadResponse(fileId = "test-file"))
        }

        override suspend fun getOrderNotificationStatus(
            orderId: String,
            appId: String?,
            appVersion: String?
        ): Response<OrderNotificationStatusResponse> {
            return Response.success(OrderNotificationStatusResponse(status = "sent"))
        }

        override suspend fun searchCustomers(
            query: String,
            appId: String?,
            appVersion: String?
        ): Response<id.a3.core.sync.model.CustomerSearchResponse> {
            return Response.success(id.a3.core.sync.model.CustomerSearchResponse())
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)
        fakeApi = FakeSyncApiService()
        syncSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = fakeApi
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `triggerSync sends pending outbox bundles and updates state`() = runTest {
        val manager = OutboxSyncManager(
            syncSender = syncSender,
            outboxDao = database.outboxDao(),
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val bundleId = UUID.randomUUID().toString()
        val item = OutboxEntity(
            bundleId = bundleId,
            deviceId = "C1-TABLET",
            shiftId = "shift-01",
            eventsJson = """[{"event_id":"e-1","type":"order.created","occurred_at":"2026-11-01T08:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(item)

        assertEquals(1, database.outboxDao().getPendingCount())

        val summary = manager.triggerSync()

        assertEquals(1, summary.sent)
        assertEquals(0, summary.duplicates)
        assertEquals(0, summary.failed)
        assertEquals(0, database.outboxDao().getPendingCount())
        assertEquals(summary, manager.lastSyncResult.value)
    }

    @Test
    fun `startForegroundSyncLoop polls and sends pending outbox automatically`() = runTest {
        val manager = OutboxSyncManager(
            syncSender = syncSender,
            outboxDao = database.outboxDao(),
            scope = this,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            pollIntervalMillis = 5_000L
        )

        val bundleId = UUID.randomUUID().toString()
        val item = OutboxEntity(
            bundleId = bundleId,
            deviceId = "L1-TABLET",
            shiftId = null,
            eventsJson = """[{"event_id":"e-2","type":"laundry.order_created","occurred_at":"2026-11-01T08:30:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(item)

        manager.startForegroundSyncLoop()

        var attempts = 0
        while (database.outboxDao().getPendingCount() > 0 && attempts < 20) {
            kotlinx.coroutines.delay(50)
            attempts++
        }

        assertEquals(0, database.outboxDao().getPendingCount())
        assertTrue(fakeApi.sendBundlesCallCount >= 1)
        assertNotNull(manager.lastSyncResult.value)
        assertEquals(1, manager.lastSyncResult.value?.sent)

        manager.stopForegroundSyncLoop()
    }
}
