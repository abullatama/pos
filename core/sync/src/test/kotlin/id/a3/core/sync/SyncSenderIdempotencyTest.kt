package id.a3.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.sync.api.SyncApiService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals("VALIDATION_FAILED", outboxResult.reasonCode)
        assertEquals("Field wajib kosong", outboxResult.lastError)

        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(1, failedList.size)
        assertEquals(bundleId, failedList[0].bundleId)
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

        // Retryable bundle must NOT be in failed queue
        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(0, failedList.size)
    }

    @Test
    fun `F28 - accepted bundle with flags marks outbox as SENT and records flags without entering failed queue`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-1","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        // Server responds with ACCEPTED carrying quarantine flags (TOTAL_MISMATCH, SHIFT_RANGE)
        val flagsResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "ACCEPTED",
                        "flags": ["TOTAL_MISMATCH", "SHIFT_RANGE"],
                        "server_received_at": "2026-10-18T10:00:01Z"
                    }
                ],
                "server_time": "2026-10-18T10:00:01Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(flagsResponse))

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)
        assertEquals(0, summary.failed)

        val savedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(savedOutbox)
        assertEquals(OutboxStatus.SENT, savedOutbox!!.status)
        assertNotNull(savedOutbox.flagsJson)
        org.junit.Assert.assertTrue(savedOutbox.flagsJson!!.contains("TOTAL_MISMATCH"))
        org.junit.Assert.assertTrue(savedOutbox.flagsJson!!.contains("SHIFT_RANGE"))

        // Must NOT enter failed problem queue
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("ACCEPTED with flags must not enter problem queue", failedList.isEmpty())
    }

    @Test
    fun `F29 - retryFailedBundle resets FAILED bundle to PENDING and next sync succeeds`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-1","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.FAILED,
            attempts = 3,
            lastError = "Validation failed: invalid amount",
            reasonCode = "VALIDATION_FAILED"
        )
        database.outboxDao().insert(outbox)

        // Verify initial state: failed queue has 1 item
        val initialFailed = database.outboxDao().getFailedOutbox()
        assertEquals(1, initialFailed.size)

        // Trigger manual retry
        database.outboxDao().retryFailedBundle(bundleId)

        val retriedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(retriedOutbox)
        assertEquals(OutboxStatus.PENDING, retriedOutbox!!.status)
        assertEquals(0, retriedOutbox.attempts)
        org.junit.Assert.assertNull(retriedOutbox.lastError)
        org.junit.Assert.assertNull(retriedOutbox.reasonCode)

        // Now next sync picks it up
        val successResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "ACCEPTED",
                        "server_received_at": "2026-10-18T10:05:00Z"
                    }
                ],
                "server_time": "2026-10-18T10:05:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(successResponse))

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)
        assertEquals(0, summary.failed)

        val finalOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(finalOutbox)
        assertEquals(OutboxStatus.SENT, finalOutbox!!.status)
    }

    @Test
    fun `F29 - retention cleanup only deletes SENT bundles and never deletes FAILED or PENDING bundles`() = runBlocking {
        val oldTimestamp = 1000L
        val cutoffTimestamp = 5000L

        val sentBundle = OutboxEntity(
            bundleId = "bundle-sent",
            deviceId = "dev-1",
            shiftId = null,
            eventsJson = "[]",
            status = OutboxStatus.SENT,
            createdAt = oldTimestamp
        )
        val failedBundle = OutboxEntity(
            bundleId = "bundle-failed",
            deviceId = "dev-1",
            shiftId = null,
            eventsJson = """[{"event_id":"evt-failed","type":"payment.recorded"}]""",
            status = OutboxStatus.FAILED,
            reasonCode = "VALIDATION_FAILED",
            lastError = "Total mismatch",
            createdAt = oldTimestamp
        )
        val pendingBundle = OutboxEntity(
            bundleId = "bundle-pending",
            deviceId = "dev-1",
            shiftId = null,
            eventsJson = "[]",
            status = OutboxStatus.PENDING,
            createdAt = oldTimestamp
        )

        database.outboxDao().insert(sentBundle)
        database.outboxDao().insert(failedBundle)
        database.outboxDao().insert(pendingBundle)

        // Perform retention cleanup for records older than cutoff
        val deletedCount = database.outboxDao().deleteSentOlderThan(cutoffTimestamp)
        assertEquals(1, deletedCount)

        // SENT bundle was deleted
        org.junit.Assert.assertNull(database.outboxDao().getByBundleId("bundle-sent"))

        // FAILED bundle was PRESERVED completely (tidak ada event yang dihapus diam-diam)
        val preservedFailed = database.outboxDao().getByBundleId("bundle-failed")
        assertNotNull(preservedFailed)
        assertEquals(OutboxStatus.FAILED, preservedFailed!!.status)
        assertEquals("VALIDATION_FAILED", preservedFailed.reasonCode)
        org.junit.Assert.assertTrue(preservedFailed.eventsJson.contains("payment.recorded"))

        // PENDING bundle was PRESERVED completely
        val preservedPending = database.outboxDao().getByBundleId("bundle-pending")
        assertNotNull(preservedPending)
        assertEquals(OutboxStatus.PENDING, preservedPending!!.status)
    }

    @Test
    fun `getOrderNotificationStatus retrieves WhatsApp status successfully`() = runBlocking {
        val jsonResponse = """
            {
                "notifications": [
                    {
                        "kind": "siap",
                        "status": "sent",
                        "attempts": 1,
                        "last_attempt_at": "2026-10-20T10:15:00Z"
                    }
                ],
                "server_time": "2026-10-20T10:20:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = apiService.getOrderNotificationStatus("ord-uuid-123", appId = "laundry", appVersion = "1.0.0")
        org.junit.Assert.assertTrue(response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        assertEquals(1, body!!.notifications.size)
        assertEquals("siap", body.notifications[0].kind)
        assertEquals("sent", body.notifications[0].status)
        assertEquals(1, body.notifications[0].attempts)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/v1/orders/ord-uuid-123/notification-status", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `SyncSender automatically aligns outdated outbox deviceId with current deviceId`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceIdProvider = { "new-active-device-999" }
        )

        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "old-outdated-device-111",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-1","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val successResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "ACCEPTED",
                        "server_received_at": "2026-10-18T10:00:05Z"
                    }
                ],
                "server_time": "2026-10-18T10:00:05Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(successResponse))

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val recorded = mockWebServer.takeRequest()
        val requestBody = recorded.body.readUtf8()
        org.junit.Assert.assertTrue("Payload must use current active device ID", requestBody.contains("new-active-device-999"))

        val savedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertEquals("new-active-device-999", savedOutbox!!.deviceId)
    }

    @Test
    fun `SyncSender automatically aligns missing outbox shiftId with current shiftId`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-active-777" }
        )

        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = null,
            eventsJson = """[{"event_id":"evt-2","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val successResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "ACCEPTED",
                        "server_received_at": "2026-10-18T10:00:05Z"
                    }
                ],
                "server_time": "2026-10-18T10:00:05Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(successResponse))

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val recorded = mockWebServer.takeRequest()
        val requestBody = recorded.body.readUtf8()
        org.junit.Assert.assertTrue("Payload must contain active shift ID", requestBody.contains("shift-active-777"))

        val savedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertEquals("shift-active-777", savedOutbox!!.shiftId)
    }

    @Test
    fun `SyncSender regenerates bundleId when attributes are aligned on a previously failed outbox`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-active-888" }
        )

        val oldBundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = oldBundleId,
            deviceId = "dev-cafe-1",
            shiftId = null,
            eventsJson = """[{"event_id":"evt-3","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "shift_id:The shift id field is required"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertFalse("Bundle ID must be regenerated and not match old rejected ID", sentBody.contains(oldBundleId))
        org.junit.Assert.assertTrue("Payload must contain active shift ID", sentBody.contains("shift-active-888"))

        val oldRow = database.outboxDao().getByBundleId(oldBundleId)
        org.junit.Assert.assertNull(oldRow)

        val allPending = database.outboxDao().getOutboxByStatus(OutboxStatus.PENDING)
        assertEquals(0, allPending.size)
    }

    @Test
    fun `SyncSender regenerates bundleId when server rejects with hash conflict error`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-4","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{}}]""",
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val hashConflictResponse = """
            {
                "results": [
                    {
                        "bundle_id": "$bundleId",
                        "status": "REJECTED",
                        "reason_code": "VALIDATION_FAILED",
                        "message": "bundle_id sudah dipakai dengan isi yang berbeda"
                    }
                ],
                "server_time": "2026-10-18T10:00:05Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(hashConflictResponse))

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.failed)

        val oldRow = database.outboxDao().getByBundleId(bundleId)
        org.junit.Assert.assertNull("Old bundle ID must be replaced", oldRow)

        val failed = database.outboxDao().getOutboxByStatus(OutboxStatus.FAILED)
        assertEquals(1, failed.size)
        org.junit.Assert.assertNotEquals(bundleId, failed[0].bundleId)
    }

    @Test
    fun `SyncSender automatically aligns display_number prefix with target device code`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            currentDeviceCodeProvider = { "C8" },
            orderDao = database.orderDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C1-0001",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "open",
            subtotal = 50000L,
            total = 50000L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C1-0001",
                        "order_type": "dine_in"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "Pesan kesalahan server: display_number harus berformat C8-NNNN."
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload must use aligned C8-0001 prefix", sentBody.contains("C8-0001"))
        org.junit.Assert.assertFalse("Payload must no longer contain C1-0001", sentBody.contains("C1-0001"))

        val savedOrder = database.orderDao().getOrderById(orderId)
        assertEquals("C8-0001", savedOrder!!.displayNumber)
    }

    @Test
    fun `SyncSender auto-injects discount_rule and options when missing on discounted orders`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0001",
                        "order_type": "take_away",
                        "lines": [
                            {
                                "line_id": "line-1",
                                "item_ref_id": "menu-1",
                                "name": "Item 1",
                                "quantity": 1,
                                "unit_price": 50000
                            }
                        ],
                        "subtotal": 50000,
                        "discount": 5000,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 45000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "Pesan kesalahan server: payload.discount_rule.preset wajib bila ada diskon"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload must contain discount_rule with preset", sentBody.contains("\"preset\":\"Diskon 10%\""))
        org.junit.Assert.assertTrue("Payload lines must contain options array", sentBody.contains("\"options\":[]"))
        org.junit.Assert.assertTrue("Payload order_type must be normalized to takeaway", sentBody.contains("\"order_type\":\"takeaway\""))
    }

    @Test
    fun `SyncSender strips discount and recalculates total when rejected with unknown preset error`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C8-0002",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "open",
            subtotal = 60000L,
            discount = 3000L,
            total = 57000L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0002",
                        "order_type": "dine_in",
                        "discount_rule": {
                            "preset": "Diskon 5%"
                        },
                        "lines": [
                            {
                                "line_id": "line-2",
                                "item_ref_id": "menu-2",
                                "name": "Item 2",
                                "quantity": 1,
                                "unit_price": 60000,
                                "options": []
                            }
                        ],
                        "subtotal": 60000,
                        "discount": 3000,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 57000
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = null // Teruji meski lastError telah di-reset NULL oleh retryAllFailed
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertFalse("Payload must remove discount_rule", sentBody.contains("discount_rule"))
        org.junit.Assert.assertTrue("Payload discount must be zero", sentBody.contains("\"discount\":0"))
        org.junit.Assert.assertTrue("Payload total must be recalculated to subtotal", sentBody.contains("\"total\":60000"))

        val savedOrder = database.orderDao().getOrderById(orderId)
        assertEquals(0L, savedOrder!!.discount)
        assertEquals(60000L, savedOrder.total)
    }

    @Test
    fun `SyncSender auto-aligns tax and total when rejected with TOTAL_MISMATCH from server`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C8-0003",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "open",
            subtotal = 42000L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 42000L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0003",
                        "order_type": "dine_in",
                        "lines": [
                            {
                                "line_id": "line-latte",
                                "item_ref_id": 5,
                                "name": "Cafe Latte",
                                "quantity": 1,
                                "unit_price": 42000,
                                "options": []
                            }
                        ],
                        "subtotal": 42000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 42000
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "Total hitung ulang 46200, diterima 42000"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload tax must be aligned to 4200", sentBody.contains("\"tax\":4200"))
        org.junit.Assert.assertTrue("Payload total must be aligned to 46200", sentBody.contains("\"total\":46200"))

        val savedOrder = database.orderDao().getOrderById(orderId)
        assertEquals(4200L, savedOrder!!.tax)
        assertEquals(46200L, savedOrder.total)
    }

    @Test
    fun `SyncSender auto-aligns tax, order total, and payment when server reports 'total hitung 46200, diterima 42000'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C8-0005",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "paid",
            subtotal = 42000L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 42000L,
            paidAmount = 42000L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0005",
                        "order_type": "dine_in",
                        "lines": [
                            {
                                "line_id": "line-latte",
                                "item_ref_id": 5,
                                "name": "Cafe Latte",
                                "quantity": 1,
                                "unit_price": 42000,
                                "options": []
                            }
                        ],
                        "subtotal": 42000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 42000
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "payment.recorded",
                    "occurred_at": "2026-10-18T10:00:01Z",
                    "payload": {
                        "payment_id": "$paymentId",
                        "order_id": "$orderId",
                        "method": "cash",
                        "amount": 42000,
                        "tendered": 50000,
                        "idempotency_key": "${UUID.randomUUID()}"
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "total hitung 46200, diterima 42000"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload tax must be aligned to 4200", sentBody.contains("\"tax\":4200"))
        org.junit.Assert.assertTrue("Payload total must be aligned to 46200", sentBody.contains("\"total\":46200"))
        org.junit.Assert.assertTrue("Payment payload amount must be aligned to 46200", sentBody.contains("\"amount\":46200"))

        val savedOrder = database.orderDao().getOrderById(orderId)
        assertEquals(4200L, savedOrder!!.tax)
        assertEquals(46200L, savedOrder.total)
        assertEquals(46200L, savedOrder.paidAmount)

        assertEquals("10", database.syncStateDao().get("tax_percent"))
    }

    @Test
    fun `SyncSender auto-aligns table_label when missing or reported with error 'payload table_label harus teks 1-64 karakter'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C8-0006",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "open",
            subtotal = 30000L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 30000L,
            tableLabel = null
        )
        database.orderDao().insertOrder(orderEntity)

        // Event order.created tanpa table_label (atau string kosong "")
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-10-18T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0006",
                        "order_type": "dine_in",
                        "lines": [
                            {
                                "line_id": "line-item-1",
                                "item_ref_id": 1,
                                "name": "Americano",
                                "quantity": 1,
                                "unit_price": 30000,
                                "options": []
                            }
                        ],
                        "subtotal": 30000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 30000
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "payload.table_label harus teks 1-64 karakter!"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload must include table_label", sentBody.contains("\"table_label\":\"Meja 01\""))

        val savedOrder = database.orderDao().getOrderById(orderId)
        assertEquals("Meja 01", savedOrder!!.tableLabel)
    }

    @Test
    fun `SyncSender auto-aligns table_label in order table_moved when missing`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.table_moved",
                    "occurred_at": "2026-11-02T16:10:51.250Z",
                    "payload": {
                        "order_id": "$orderId",
                        "from_table": "Meja 01",
                        "to_table": "Meja 03"
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "payload.table_label harus teks 1-64 karakter!"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload must include table_label", sentBody.contains("\"table_label\":\"Meja 03\""))
        org.junit.Assert.assertTrue("Payload must include from_table", sentBody.contains("\"from_table\":\"Meja 01\""))
        org.junit.Assert.assertTrue("Payload must include to_table", sentBody.contains("\"to_table\":\"Meja 03\""))
    }

    @Test
    fun `SyncSender auto-aligns occurred_at when rejected with error 'waktu event lebih awal daripada waktu order dibuat'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderCreatedAtMs = 1793635973000L // 2026-11-02T16:12:53Z
        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C8-0007",
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = "dine_in",
            status = "open",
            subtotal = 42000L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 42000L,
            createdAt = orderCreatedAtMs
        )
        database.orderDao().insertOrder(orderEntity)

        // Event occurred_at 16:10:51 (2 menit lebih awal daripada order.created 16:12:53)
        val prematureTime = "2026-11-02T16:10:51.250Z"
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.table_moved",
                    "occurred_at": "$prematureTime",
                    "payload": {
                        "order_id": "$orderId",
                        "from_table": "Meja 01",
                        "to_table": "Meja 03",
                        "table_label": "Meja 03"
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "waktu event lebih awal daripada waktu order dibuat"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-10-18T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertFalse("Sent occurred_at must NOT be the premature time", sentBody.contains(prematureTime))
        val minAcceptableInstant = java.time.Instant.ofEpochMilli(orderCreatedAtMs)
        val sentOccurredAt = Regex(""""occurred_at":"([^"]+)"""").find(sentBody)!!.groupValues[1]
        val parsedSentInstant = java.time.Instant.parse(sentOccurredAt)
        org.junit.Assert.assertTrue("occurred_at must be strictly after order.createdAt", parsedSentInstant.isAfter(minAcceptableInstant))
    }

    @Test
    fun `SyncSender successfully syncs cashout recorded bundle and marks outbox as SENT`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 25000,
                        "category": "Operasional",
                        "reason": "Beli es batu kristal",
                        "receipt_file_id": null,
                        "approved_by": null
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                recordedRequest.set(request.body.readUtf8())
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$bundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)
        assertEquals(0, summary.failed)

        val updatedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(updatedOutbox)
        assertEquals(OutboxStatus.SENT, updatedOutbox!!.status)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Payload must contain cashout.recorded", sentBody.contains("cashout.recorded"))
        org.junit.Assert.assertTrue("Payload must contain cashout_id", sentBody.contains(cashoutId))
        org.junit.Assert.assertTrue("Payload must contain amount 25000", sentBody.contains("25000"))
        org.junit.Assert.assertTrue("Payload must contain category Operasional", sentBody.contains("Operasional"))
        org.junit.Assert.assertTrue("Payload must contain receipt_file_id null", sentBody.contains(""""receipt_file_id":null"""))
        org.junit.Assert.assertTrue("Payload must contain approved_by null", sentBody.contains(""""approved_by":null"""))
    }

    @Test
    fun `SyncSender auto-aligns cashout recorded payload when missing null fields`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        // Payload tanpa key receipt_file_id dan approved_by
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 35000,
                        "category": "Bahan baku",
                        "reason": "Beli susu UHT"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedRequest.get()
        org.junit.Assert.assertTrue("Sent payload must include receipt_file_id null", sentBody.contains(""""receipt_file_id":null"""))
        org.junit.Assert.assertTrue("Sent payload must include approved_by null", sentBody.contains(""""approved_by":null"""))
    }

    @Test
    fun `SyncSender marks outbox as FAILED with parsed error message when server returns HTTP 422 client error`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "${UUID.randomUUID()}",
                        "amount": 60000,
                        "category": "Operasional",
                        "reason": "Gas elpiji"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"code":"VALIDATION_FAILED","message":"Pengeluaran kas di atas Rp 50.000 wajib menyertakan foto nota dan persetujuan manager."}""")
        )

        val summary = syncSender.sendPendingBundles()
        assertEquals(0, summary.sent)
        assertEquals(1, summary.failed)

        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(1, failedList.size)
        val updatedOutbox = failedList.first()
        assertEquals("Outbox must be marked FAILED on HTTP 422 validation failure", OutboxStatus.FAILED, updatedOutbox.status)
        assertEquals("Pengeluaran kas di atas Rp 50.000 wajib menyertakan foto nota dan persetujuan manager.", updatedOutbox.lastError)
        assertEquals("HTTP_422", updatedOutbox.reasonCode)
    }

    @Test
    fun `SyncSender marks outbox as FAILED when retryable error exceeds 3 attempts`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "${UUID.randomUUID()}",
                        "amount": 25000,
                        "category": "Operasional",
                        "reason": "Es batu"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 3
        )
        database.outboxDao().insert(outbox)

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "REJECTED",
                                "reason_code": "DEPENDENCY_MISSING",
                                "message": "Shift belum terbuka di server",
                                "retryable": true
                            }
                        ],
                        "server_time": "2026-11-03T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(0, summary.sent)
        assertEquals(1, summary.failed)

        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(1, failedList.size)
        val updatedOutbox = failedList.first()
        assertEquals("Outbox must be marked FAILED after exceeding max attempts for retryable error", OutboxStatus.FAILED, updatedOutbox.status)
        assertEquals("Shift belum terbuka di server", updatedOutbox.lastError)
        assertEquals("DEPENDENCY_MISSING", updatedOutbox.reasonCode)
    }

    @Test
    fun `SyncSender uploads receipt photo to POST v1 files before sending bundle and succeeds`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 75000,
                        "category": "Operasional",
                        "reason": "Beli bahan darurat",
                        "receipt_file_id": "$fileId",
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val filesUploaded = java.util.concurrent.atomic.AtomicBoolean(false)
        val bundlesSent = java.util.concurrent.atomic.AtomicBoolean(false)

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("/v1/files") || path.contains("/files") -> {
                        filesUploaded.set(true)
                        MockResponse().setResponseCode(201).setBody("""{"file_id":"$fileId"}""")
                    }
                    path.contains("/v1/sync/bundles") || path.contains("/sync/bundles") -> {
                        bundlesSent.set(true)
                        val bodyStr = request.body.readUtf8()
                        val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                        val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                        MockResponse().setResponseCode(200).setBody("""
                            {
                                "results": [
                                    {
                                        "bundle_id": "$sentBundleId",
                                        "status": "ACCEPTED"
                                    }
                                ],
                                "server_time": "2026-11-03T10:00:05Z"
                            }
                        """.trimIndent())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)
        org.junit.Assert.assertTrue("POST /v1/files must be called before sending bundles", filesUploaded.get())
        org.junit.Assert.assertTrue("POST /v1/sync/bundles must be called", bundlesSent.get())

        val sentOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.SENT)
        assertEquals(1, sentOutbox.size)
    }

    @Test
    fun `SyncSender auto-aligns outbox when rejected with error 'foto nota belum diterima server'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 75000,
                        "category": "Operasional",
                        "reason": "Beli bahan darurat",
                        "receipt_file_id": "$fileId",
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "foto nota belum diterima server"
        )
        database.outboxDao().insert(outbox)

        val filesUploaded = java.util.concurrent.atomic.AtomicBoolean(false)
        val bundlesSent = java.util.concurrent.atomic.AtomicBoolean(false)
        val recordedSentBundleId = java.util.concurrent.atomic.AtomicReference<String>()

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("/v1/files") || path.contains("/files") -> {
                        filesUploaded.set(true)
                        MockResponse().setResponseCode(201).setBody("""{"file_id":"$fileId"}""")
                    }
                    path.contains("/v1/sync/bundles") || path.contains("/sync/bundles") -> {
                        bundlesSent.set(true)
                        val bodyStr = request.body.readUtf8()
                        val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                        val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                        recordedSentBundleId.set(sentBundleId)
                        MockResponse().setResponseCode(200).setBody("""
                            {
                                "results": [
                                    {
                                        "bundle_id": "$sentBundleId",
                                        "status": "ACCEPTED"
                                    }
                                ],
                                "server_time": "2026-11-03T10:00:05Z"
                            }
                        """.trimIndent())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)
        org.junit.Assert.assertTrue("POST /v1/files must be called", filesUploaded.get())
        org.junit.Assert.assertTrue("POST /v1/sync/bundles must be called", bundlesSent.get())
        org.junit.Assert.assertNotEquals("BundleId must be regenerated after foto nota rejection", bundleId, recordedSentBundleId.get())

        val sentOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.SENT)
        assertEquals(1, sentOutbox.size)
    }

    @Test
    fun `SyncSender falls back to null receipt_file_id for amount within 50k when server rejected with 'foto nota belum diterima server'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 25000,
                        "category": "Operasional",
                        "reason": "Es batu",
                        "receipt_file_id": "$fileId",
                        "approved_by": null
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "foto nota belum diterima server"
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("/v1/files") || path.contains("/files") -> {
                        // Simulasi server file gagal
                        MockResponse().setResponseCode(500)
                    }
                    path.contains("/v1/sync/bundles") || path.contains("/sync/bundles") -> {
                        val bodyStr = request.body.readUtf8()
                        recordedBody.set(bodyStr)
                        val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                        val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                        MockResponse().setResponseCode(200).setBody("""
                            {
                                "results": [
                                    {
                                        "bundle_id": "$sentBundleId",
                                        "status": "ACCEPTED"
                                    }
                                ],
                                "server_time": "2026-11-03T10:00:05Z"
                            }
                        """.trimIndent())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedBody.get()
        org.junit.Assert.assertTrue("receipt_file_id should be null for cashout <= 50k", sentBody.contains(""""receipt_file_id":null"""))
        val sentOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.SENT)
        assertEquals(1, sentOutbox.size)
    }

    @Test
    fun `SyncSender falls back to null receipt_file_id when upload fails and server previously rejected with 'foto nota belum diterima server'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val fileId = UUID.randomUUID().toString()
        val cashoutId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "cashout.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "cashout_id": "$cashoutId",
                        "amount": 75000,
                        "category": "Operasional",
                        "reason": "Beli bahan darurat",
                        "receipt_file_id": "$fileId",
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = UUID.randomUUID().toString(),
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 1,
            lastError = "foto nota belum diterima server"
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("/v1/files") || path.contains("/files") -> {
                        MockResponse().setResponseCode(500)
                    }
                    path.contains("/v1/sync/bundles") || path.contains("/sync/bundles") -> {
                        val bodyStr = request.body.readUtf8()
                        recordedBody.set(bodyStr)
                        val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                        val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                        MockResponse().setResponseCode(200).setBody("""
                            {
                                "results": [
                                    {
                                        "bundle_id": "$sentBundleId",
                                        "status": "ACCEPTED"
                                    }
                                ],
                                "server_time": "2026-11-03T10:00:05Z"
                            }
                        """.trimIndent())
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentBody = recordedBody.get()
        org.junit.Assert.assertTrue("receipt_file_id must be aligned to null when file upload fails", sentBody.contains(""""receipt_file_id":null"""))
        val sentOutbox = database.outboxDao().getOutboxByStatus(OutboxStatus.SENT)
        assertEquals(1, sentOutbox.size)
    }

    @Test
    fun `HTTP 401 keeps outbox in PENDING and increments attempts without marking FAILED`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = """[{"event_id":"evt-1","type":"order.created","occurred_at":"2026-10-18T10:00:00Z","payload":{"order_id":"ord-1","display_number":"C1-0001","order_type":"dine_in","table_label":"Meja 01","subtotal":10000,"discount":0,"service_charge":0,"tax":0,"rounding":0,"total":10000,"lines":[]}}]""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        // Server merespon HTTP 401 Unauthorized (misal token habis)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"message":"Unauthenticated"}""")
        )

        val summary = syncSender.sendPendingBundles()
        assertEquals(0, summary.sent)
        assertEquals(1, summary.failed)

        val outboxAfter = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(outboxAfter)
        assertEquals("Outbox HARUS tetap PENDING pada HTTP 401 agar otomatis terkirim saat login ulang", OutboxStatus.PENDING, outboxAfter!!.status)
        assertEquals(1, outboxAfter.attempts)
        org.junit.Assert.assertTrue(
            "lastError harus memuat informasi 401",
            outboxAfter.lastError?.contains("401") == true || outboxAfter.lastError?.contains("Unauthenticated") == true
        )

        // Pastikan TIDAK masuk ke antrean FAILED
        val failedList = database.outboxDao().getFailedOutbox()
        assertEquals(0, failedList.size)
    }

    @Test
    fun `retryAllFailedWithDeviceId preserves original bundle_id when retrying on the same device`(): Unit = runBlocking {
        val originalBundleId = UUID.randomUUID().toString()
        val currentDeviceId = "dev-cafe-1"
        val failedOutbox = OutboxEntity(
            bundleId = originalBundleId,
            deviceId = currentDeviceId,
            shiftId = "shift-001",
            eventsJson = "[]",
            status = OutboxStatus.FAILED,
            attempts = 3,
            lastError = "Network error"
        )
        database.outboxDao().insert(failedOutbox)

        // Panggil retryAllFailedWithDeviceId dengan deviceId yang sama
        database.outboxDao().retryAllFailedWithDeviceId(currentDeviceId = currentDeviceId, fallbackShiftId = "shift-001")

        // bundle_id asli WAJIB dipertahankan untuk menjamin deteksi DUPLICATE di server
        val retried = database.outboxDao().getByBundleId(originalBundleId)
        assertNotNull("bundle_id asli harus tetap ada dan tidak di-replace jika device sama", retried)
        assertEquals(OutboxStatus.PENDING, retried!!.status)
        assertEquals(0, retried.attempts)
        org.junit.Assert.assertNull(retried.lastError)
    }

    @Test
    fun `SyncSender translates correction recorded to Laravel 12 backend schema`(): Unit = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val correctionId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        // Event outbox dengan kode alasan OpenAPI bahasa Inggris ("wrong_input") dan approved_by teks
        val legacyEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "correction.recorded",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "correction_id": "$correctionId",
                        "type": "void",
                        "order_id": "$orderId",
                        "order_line_ids": ["$lineId"],
                        "amount": 25000,
                        "reason_code": "wrong_input",
                        "note": "Salah input pesanan",
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = legacyEventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T10:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        // Verifikasi translasi field Laravel 12
        org.junit.Assert.assertTrue("reason_code harus diterjemahkan ke salah_input", sentPayload.contains(""""reason_code":"salah_input""""))
        org.junit.Assert.assertTrue("reason_note harus disertakan", sentPayload.contains(""""reason_note":"Salah input pesanan""""))
        org.junit.Assert.assertTrue("order_line_id single harus disertakan", sentPayload.contains(""""order_line_id":"$lineId""""))
        org.junit.Assert.assertTrue("approved_by harus bertipe numerik", sentPayload.contains(""""approved_by":4"""))
        org.junit.Assert.assertTrue("payment_id harus disertakan (null)", sentPayload.contains(""""payment_id":null"""))
    }

    @Test
    fun `sendPendingBundles auto-aligns shift closed event with expected_cash difference and arrays`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val shiftId = "shift-auto-123"

        val legacyShiftClosedJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "shift.closed",
                    "occurred_at": "2026-11-03T11:00:00Z",
                    "payload": {
                        "shift_id": "$shiftId",
                        "counted_cash": 485000,
                        "cash_left": 200000,
                        "cash_deposited": 285000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = shiftId,
            eventsJson = legacyShiftClosedJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T11:00:05Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertTrue("expected_cash harus disertakan otomatis", sentPayload.contains(""""expected_cash":485000"""))
        org.junit.Assert.assertTrue("difference harus disertakan otomatis", sentPayload.contains(""""difference":0"""))
        org.junit.Assert.assertTrue("qris_checked_payment_ids array harus disertakan", sentPayload.contains(""""qris_checked_payment_ids":[]"""))
        org.junit.Assert.assertTrue("handover_order_ids array harus disertakan", sentPayload.contains(""""handover_order_ids":[]"""))
    }

    @Test
    fun `sendPendingBundles auto-aligns laundry status_changed and picked_up for Laravel 12`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val laundryEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "laundry.status_changed",
                    "occurred_at": "2026-11-03T12:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "status": "processing",
                        "user_id": null
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "laundry.picked_up",
                    "occurred_at": "2026-11-03T12:05:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "order_line_id": "$lineId",
                        "quantity": 1,
                        "is_final": true,
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = laundryEventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T12:06:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        // Verifikasi user_id numeric pada status_changed
        org.junit.Assert.assertTrue("user_id numerik harus disertakan pada status_changed", sentPayload.contains(""""user_id":4"""))
        // Verifikasi line_ids array dan status diambil pada picked_up
        org.junit.Assert.assertTrue("line_ids array harus disertakan pada picked_up", sentPayload.contains(""""line_ids":["$lineId"]"""))
        org.junit.Assert.assertTrue("status diambil harus disertakan pada picked_up", sentPayload.contains(""""status":"diambil""""))
        org.junit.Assert.assertTrue("approved_by numerik 4 harus disertakan pada picked_up", sentPayload.contains(""""approved_by":4"""))
    }

    @Test
    fun `sendPendingBundles auto-aligns secondary events customer created and menu sold out`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()

        val secondaryEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "customer.created",
                    "occurred_at": "2026-11-03T13:00:00Z",
                    "payload": {
                        "name": "Bapak Rudi",
                        "phone": "+62 812-9988-7766"
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "menu.sold_out_changed",
                    "occurred_at": "2026-11-03T13:01:00Z",
                    "payload": {
                        "menu_item_id": "menu-croissant",
                        "sold_out": "1"
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.sent_to_kitchen",
                    "occurred_at": "2026-11-03T13:02:00Z",
                    "payload": {
                        "order_id": "${UUID.randomUUID()}"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = secondaryEventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T13:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        // Verifikasi normalisasi telepon 08
        org.junit.Assert.assertTrue("nomor telepon harus dinormalisasi ke 081299887766", sentPayload.contains(""""phone":"081299887766""""))
        org.junit.Assert.assertTrue("customer_id UUID harus dibuat otomatis", sentPayload.contains(""""customer_id":"""))
        // Verifikasi sold_out boolean murni
        org.junit.Assert.assertTrue("sold_out harus boolean true", sentPayload.contains(""""sold_out":true"""))
        // Verifikasi line_ids array pada sent_to_kitchen
        org.junit.Assert.assertTrue("line_ids array harus disediakan", sentPayload.contains(""""line_ids":[]"""))
    }

    @Test
    fun `SyncSender handles unauthorized shift opened error and removes invalid local shift`(): Unit = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val shiftId = UUID.randomUUID().toString()

        val shiftEntity = id.a3.core.data.local.entity.ShiftEntity(
            id = shiftId,
            businessUnitId = "2",
            deviceId = "dev-laundry-1",
            cashierId = "3",
            cashierName = "Staf Laundry",
            status = "open",
            openingCash = 200000L,
            openingCashBreakdownJson = "{\"100000\":2}",
            openedAt = System.currentTimeMillis()
        )
        database.shiftDao().insertShift(shiftEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "shift.opened",
                    "occurred_at": "2026-11-03T10:00:00Z",
                    "payload": {
                        "user_id": 3,
                        "opening_cash": 200000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = shiftId,
            eventsJson = eventsJson
        )
        database.outboxDao().insert(outbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            appId = "laundry",
            shiftDao = database.shiftDao()
        )

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$bundleId",
                                "status": "REJECTED",
                                "reason_code": "FORBIDDEN",
                                "message": "akun shift tidak berizin di usaha ini",
                                "retryable": false
                            }
                        ],
                        "server_time": "2026-11-03T10:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.failed)

        // Verifikasi shift lokal yang tidak berizin otomatis dihapus dari Room
        val localShift = database.shiftDao().getShiftById(shiftId)
        org.junit.Assert.assertNull("Shift yang tidak berizin harus dihapus dari database lokal", localShift)

        // Verifikasi outbox berstatus FAILED dengan alasan UNAUTHORIZED_SHIFT
        val failedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(failedOutbox)
        assertEquals(OutboxStatus.FAILED, failedOutbox!!.status)
        assertEquals("UNAUTHORIZED_SHIFT", failedOutbox.reasonCode)
        org.junit.Assert.assertTrue(failedOutbox.lastError!!.contains("tidak berizin"))
    }

    @Test
    fun `sendPendingBundles filters customer created when order created is present and ensures inline customer payload`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "customer.created",
                    "occurred_at": "2026-11-03T14:00:00Z",
                    "payload": {
                        "name": "Ibu Ratna",
                        "phone": "+62 812-3456-7890"
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T14:00:01Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0025",
                        "order_type": "laundry",
                        "lines": [],
                        "subtotal": 31500,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 31500
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T14:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        // customer.created TIDAK boleh terkirim di jaringan agar server tidak melempar 'Tipe event tidak dikenal'
        org.junit.Assert.assertFalse("customer.created tidak boleh dikirim ke jaringan", sentPayload.contains(""""type":"customer.created""""))
        // order.created WAJIB terkirim
        org.junit.Assert.assertTrue("order.created harus terkirim", sentPayload.contains(""""type":"order.created""""))
        // customer inline harus ada dan nomor telepon dinormalisasi ke 081234567890
        org.junit.Assert.assertTrue("nama customer harus ada", sentPayload.contains(""""name":"Ibu Ratna""""))
        org.junit.Assert.assertTrue("nomor telepon customer harus dinormalisasi", sentPayload.contains(""""phone":"081234567890""""))
    }

    @Test
    fun `sendPendingBundles auto-recovers failed bundle rejected with unknown event type customer created`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "customer.created",
                    "occurred_at": "2026-11-03T14:10:00Z",
                    "payload": {
                        "name": "Pak Joko",
                        "phone": "081987654321"
                    }
                },
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T14:10:01Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0026",
                        "order_type": "laundry",
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

        // Simulasikan bundel yang saat ini berstatus FAILED di tablet karena error server
        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "Pesan kesalahan server: Tipe event tidak dikenal : customer.created",
            reasonCode = "UNKNOWN_EVENT_TYPE"
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T14:15:00Z"
                    }
                """.trimIndent())
            }
        }

        // Jalankan sendPendingBundles() yang akan memulihkan dan mengirim bundel
        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertFalse("customer.created harus dibersihkan dari antrean gagal", sentPayload.contains(""""type":"customer.created""""))
        org.junit.Assert.assertTrue("order.created harus berhasil terkirim", sentPayload.contains(""""type":"order.created""""))
        org.junit.Assert.assertTrue("customer info Pak Joko harus disertakan di order", sentPayload.contains(""""name":"Pak Joko""""))
        org.junit.Assert.assertTrue("nomor telepon 081987654321 harus ada", sentPayload.contains(""""phone":"081987654321""""))

        // Verifikasi tidak ada lagi antrean berstatus FAILED
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean failed harus bersih", failedList.isEmpty())
    }

    @Test
    fun `sendPendingBundles auto-marks SENT for bundle containing only customer created`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "customer.created",
                    "occurred_at": "2026-11-03T14:20:00Z",
                    "payload": {
                        "name": "Ibu Siti",
                        "phone": "081333444555"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val summary = syncSender.sendPendingBundles()
        assertEquals(0, summary.sent) // Tidak mengirim HTTP request ke server

        val savedOutbox = database.outboxDao().getByBundleId(bundleId)
        assertNotNull(savedOutbox)
        assertEquals(OutboxStatus.SENT, savedOutbox!!.status)
    }

    @Test
    fun `sendPendingBundles eliminates customer_id when order created has both customer and customer_id`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val oldCustId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T14:20:01Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0027",
                        "order_type": "laundry",
                        "customer_id": "$oldCustId",
                        "customer": {
                            "name": "Mbak Dewi",
                            "phone": "+62 813-1122-3344"
                        },
                        "lines": [],
                        "subtotal": 40000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 40000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T14:25:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        // Verifikasi kepatuhan aturan server: payload.customer dan payload.customer_id TIDAK BOLEH BERSAMAAN
        org.junit.Assert.assertTrue("customer inline Mbak Dewi harus ada", sentPayload.contains(""""name":"Mbak Dewi""""))
        org.junit.Assert.assertTrue("nomor telepon Mbak Dewi harus dinormalisasi", sentPayload.contains(""""phone":"081311223344""""))
        org.junit.Assert.assertFalse("customer_id wajib dihapus dari order.created jika customer inline ada", sentPayload.contains(""""customer_id":""""))
    }

    @Test
    fun `sendPendingBundles auto-recovers failed bundle rejected with payload customer dan customer_id tidak boleh bersamaan`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val oldCustId = UUID.randomUUID().toString()

        // Kasus nyata yang dialami tablet: Outbox tersangkut karena keduanya terkirim bersamaan
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T14:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0028",
                        "order_type": "laundry",
                        "customer_id": "$oldCustId",
                        "customer": {
                            "name": "Pak Hendra",
                            "phone": "081299887766"
                        },
                        "lines": [],
                        "subtotal": 35000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 35000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "PESAN KESALAHAN SERVER: payload.customer dan payload.customer_id tidak boleh bersamaan.",
            reasonCode = "REJECTED"
        )
        database.outboxDao().insert(outbox)

        val recordedBody = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedBody.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T14:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedBody.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertTrue("customer Pak Hendra harus ada", sentPayload.contains(""""name":"Pak Hendra""""))
        org.junit.Assert.assertTrue("nomor telepon Pak Hendra harus ada", sentPayload.contains(""""phone":"081299887766""""))
        org.junit.Assert.assertFalse("customer_id tidak boleh ada bersamaan dengan customer", sentPayload.contains(""""customer_id":""""))

        // Pastikan tidak ada lagi bundel berstatus FAILED di Room
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean failed harus bersih setelah auto-recovery", failedList.isEmpty())
    }

    @Test
    fun `SyncSender auto-recovers failed laundry bundle rejected with display_number harus berformat L2-NNNN`() = runBlocking {
        var learnedCode: String? = null
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { null },
            onDeviceCodeLearned = { code -> learnedCode = code },
            orderDao = database.orderDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "L1-0025",
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-laundry-1",
            cashierId = "cashier-1",
            orderType = "laundry",
            status = "open",
            subtotal = 35000L,
            total = 35000L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T15:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0025",
                        "order_type": "laundry",
                        "lines": [],
                        "subtotal": 35000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 35000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "PESAN KESALAHAN SERVER: display_number harus berformat L2-NNNN.",
            reasonCode = "REJECTED"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        // Verifikasi display_number otomatis diselaraskan ke L2-0025
        org.junit.Assert.assertTrue("display_number harus berformat L2-0025", sentPayload.contains(""""display_number":"L2-0025""""))
        org.junit.Assert.assertFalse("display_number lama L1-0025 tidak boleh terkirim", sentPayload.contains(""""display_number":"L1-0025""""))

        // Verifikasi Order di Room diperbarui nomor tampilnya
        val updatedOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(updatedOrder)
        assertEquals("L2-0025", updatedOrder!!.displayNumber)

        // Verifikasi callback kode perangkat menerima L2
        assertEquals("L2", learnedCode)

        // Verifikasi outbox berstatus SENT dan antrean gagal bersih
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean failed harus bersih", failedList.isEmpty())
    }

    @Test
    fun `SyncSender automatically aligns item_ref_id to valid numeric server ID from masterDataDao`() = runBlocking {
        // Simulasikan database master data yang telah tersinkron dari server dengan ID numerik resmi
        val realServerService = id.a3.core.data.local.entity.LaundryServiceEntity(
            id = "2",
            name = "Cuci Kering Lipat",
            unit = "kg",
            rate = 5000L,
            expressRate = 8000L,
            minWeight = 1.0,
            weightStep = 0.5,
            active = true
        )
        database.masterDataDao().upsertLaundryService(realServerService)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { "L2" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T15:20:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0001",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Abul",
                            "phone": "081224921695"
                        },
                        "lines": [
                            {
                                "line_id": "line-1",
                                "item_ref_id": "svc-cuci-saja",
                                "name": "Cuci Saja (Kiloan) (Express)",
                                "unit": "pcs",
                                "rate": 8000,
                                "line_total": 8000,
                                "quantity": 1,
                                "unit_price": 8000
                            }
                        ],
                        "subtotal": 16000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 16000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "Pesan kesalahan server: payload.lines.0.item_ref_id harus ID yang valid.",
            reasonCode = "VALIDATION_FAILED"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:25:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        // Verifikasi item_ref_id otomatis diselaraskan ke ID server numerik '2'
        org.junit.Assert.assertTrue("item_ref_id harus numerik 2", sentPayload.contains(""""item_ref_id":2"""))
        org.junit.Assert.assertFalse("item_ref_id lama svc-cuci-saja tidak boleh terkirim", sentPayload.contains(""""item_ref_id":"svc-cuci-saja""""))
        // Verifikasi unit otomatis diselaraskan ke 'kg' sesuai layanan 2
        org.junit.Assert.assertTrue("unit harus kg", sentPayload.contains(""""unit":"kg""""))
        org.junit.Assert.assertTrue("weight_grams harus 2000", sentPayload.contains(""""weight_grams":2000"""))
        org.junit.Assert.assertTrue("line_total harus 16000", sentPayload.contains(""""line_total":16000"""))

        // Verifikasi outbox berstatus SENT
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean failed harus bersih", failedList.isEmpty())
    }

    @Test
    fun `SyncSender auto-recovers failed laundry bundle rejected with Satuan pcs tidak cocok dengan layanan`() = runBlocking {
        val s1 = id.a3.core.data.local.entity.LaundryServiceEntity(
            id = "1",
            name = "Cuci + Setrika Reguler",
            unit = "kg",
            rate = 7000L,
            expressRate = 10000L,
            minWeight = 1.0,
            weightStep = 0.5,
            active = true
        )
        val s2 = id.a3.core.data.local.entity.LaundryServiceEntity(
            id = "2",
            name = "Cuci Kering Lipat",
            unit = "kg",
            rate = 5000L,
            expressRate = 8000L,
            minWeight = 1.0,
            weightStep = 0.5,
            active = true
        )
        database.masterDataDao().upsertLaundryService(s1)
        database.masterDataDao().upsertLaundryService(s2)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { "L2" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T15:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0001",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Abul",
                            "phone": "081224921695"
                        },
                        "lines": [
                            {
                                "line_id": "line-1",
                                "item_ref_id": 1,
                                "name": "Cuci Saja (Kiloan) (Express)",
                                "unit": "pcs",
                                "rate": 8000,
                                "line_total": 8000,
                                "quantity": 1,
                                "unit_price": 8000,
                                "options": [],
                                "express": true
                            }
                        ],
                        "subtotal": 16000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 16000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "PESAN KESALAHAN SERVER: Satuan pcs tidak cocok dengan layanan 1.",
            reasonCode = "VALIDATION_FAILED"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        // Verifikasi otomatis diselaraskan ke layanan 2, unit kg, dan weight_grams 2000
        org.junit.Assert.assertTrue("item_ref_id harus 2", sentPayload.contains(""""item_ref_id":2"""))
        org.junit.Assert.assertTrue("unit harus kg", sentPayload.contains(""""unit":"kg""""))
        org.junit.Assert.assertTrue("weight_grams harus 2000", sentPayload.contains(""""weight_grams":2000"""))
        org.junit.Assert.assertTrue("line_total harus 16000", sentPayload.contains(""""line_total":16000"""))

        // Verifikasi outbox berstatus SENT
        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean failed harus bersih", failedList.isEmpty())
    }

    @Test
    fun `SyncSender converts non-numeric customer_id UUID to inline customer using customerDao`() = runBlocking {
        val custId = "a4e93921-531c-4d35-b8fc-df461735bcea"
        val customer = id.a3.core.data.local.entity.CustomerEntity(
            id = custId,
            name = "Budi Santoso",
            phone = "081234567890"
        )
        database.customerDao().insertCustomer(customer)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { "L2" },
            orderDao = database.orderDao(),
            customerDao = database.customerDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T15:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0002",
                        "order_type": "laundry",
                        "customer_id": "$custId",
                        "lines": [
                            {
                                "line_id": "line-1",
                                "item_ref_id": 1,
                                "name": "Cuci + Setrika Reguler (Express)",
                                "unit": "kg",
                                "weight_grams": 2000,
                                "rate": 10000,
                                "line_total": 20000,
                                "quantity": 1,
                                "unit_price": 10000,
                                "options": [],
                                "express": true
                            }
                        ],
                        "subtotal": 20000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 20000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        // Verifikasi customer_id dihapus dan digantikan oleh customer inline
        org.junit.Assert.assertFalse("customer_id tidak boleh ada jika UUID", sentPayload.contains(""""customer_id""""))
        org.junit.Assert.assertTrue("customer inline harus ada", sentPayload.contains(""""customer":{"name":"Budi Santoso","phone":"081234567890"}"""))

        val sentOutbox = database.outboxDao().getByBundleId(bundleId) ?: database.outboxDao().getOutboxByStatus(OutboxStatus.SENT).firstOrNull()
        assertNotNull(sentOutbox)
        assertEquals(OutboxStatus.SENT, sentOutbox!!.status)
    }

    @Test
    fun `SyncSender translates laundry picked_up to laundry status_changed with status diambil`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "laundry.picked_up",
                    "occurred_at": "2026-11-03T15:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "order_line_id": "$lineId",
                        "quantity": 1,
                        "is_final": true
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        // Verifikasi translasi tipe event ke laundry.status_changed dan status = diambil
        org.junit.Assert.assertFalse("type laundry.picked_up tidak boleh dikirim ke server", sentPayload.contains(""""type":"laundry.picked_up""""))
        org.junit.Assert.assertTrue("type harus laundry.status_changed", sentPayload.contains(""""type":"laundry.status_changed""""))
        org.junit.Assert.assertTrue("status harus diambil", sentPayload.contains(""""status":"diambil""""))
        org.junit.Assert.assertTrue("line_ids harus array", sentPayload.contains(""""line_ids":["$lineId"]"""))
        org.junit.Assert.assertTrue("user_id harus default 4", sentPayload.contains(""""user_id":4"""))
    }

    @Test
    fun `SyncSender auto-recovers bundle rejected with payload customer_id harus bilangan bulat`() = runBlocking {
        val custId = "a4e93921-531c-4d35-b8fc-df461735bcea"
        val customer = id.a3.core.data.local.entity.CustomerEntity(
            id = custId,
            name = "Sari Laundry",
            phone = "081987654321"
        )
        database.customerDao().insertCustomer(customer)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            customerDao = database.customerDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-03T15:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0002",
                        "order_type": "laundry",
                        "customer_id": "$custId",
                        "lines": [],
                        "subtotal": 10000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 10000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "payload.customer_id harus bilangan bulat.",
            reasonCode = "VALIDATION_FAILED"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-03T15:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertFalse("customer_id tidak boleh ada jika UUID", sentPayload.contains(""""customer_id""""))
        org.junit.Assert.assertTrue("customer inline harus ada", sentPayload.contains(""""customer":{"name":"Sari Laundry","phone":"081987654321"}"""))
    }

    @Test
    fun `SyncSender auto-recovers and prepends order created when outbox has error 'order belum diterima server'`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val bundleId = UUID.randomUUID().toString()

        // 1. Simpan order di Room database lokal
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C1-0088",
            businessUnitId = "bu-cafe-1",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            orderType = "dine_in",
            tableLabel = "Meja 12",
            subtotal = 50000,
            discount = 0,
            serviceCharge = 0,
            tax = 0,
            rounding = 0,
            total = 50000,
            status = "paid",
            paidAmount = 50000,
            shiftId = "shift-01"
        )
        database.orderDao().insertOrder(orderEntity)

        val lineEntity = id.a3.core.data.local.entity.OrderLineEntity(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            itemRefId = "5",
            nameSnapshot = "Americano Ice",
            quantity = 2,
            unitPriceSnapshot = 25000
        )
        database.orderDao().insertLines(listOf(lineEntity))

        // 2. Outbox awalnya hanya berisi payment.recorded dan berstatus FAILED dengan error 'order belum diterima server'
        val paymentEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "payment.recorded",
                    "occurred_at": "2026-11-04T10:00:00Z",
                    "payload": {
                        "payment_id": "${UUID.randomUUID()}",
                        "order_id": "$orderId",
                        "method": "cash",
                        "amount": 50000
                    }
                }
            ]
        """.trimIndent()

        val failedOutbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-01",
            eventsJson = paymentEventsJson,
            status = OutboxStatus.FAILED,
            attempts = 2,
            lastError = "order belum diterima server",
            reasonCode = "DEPENDENCY_MISSING"
        )
        database.outboxDao().insert(failedOutbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            appId = "cafe",
            appVersion = "1.0.0",
            currentDeviceIdProvider = { "dev-cafe-1" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            customerDao = database.customerDao(),
            filesDirProvider = { appContext.filesDir }
        )

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T10:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertTrue("Harus memuat order.created", sentPayload.contains(""""type":"order.created""""))
        org.junit.Assert.assertTrue("Harus memuat payment.recorded", sentPayload.contains(""""type":"payment.recorded""""))
        org.junit.Assert.assertTrue("order.created harus mendahului payment.recorded", sentPayload.indexOf(""""order.created"""") < sentPayload.indexOf(""""payment.recorded""""))
    }

    @Test
    fun `SyncSender immediately auto-heals when receiving REJECTED order belum diterima server`() = runBlocking {
        val orderId = UUID.randomUUID().toString()
        val bundleId = UUID.randomUUID().toString()

        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "C1-0099",
            businessUnitId = "bu-cafe-1",
            deviceId = "dev-cafe-1",
            cashierId = "u-cashier-1",
            orderType = "takeaway",
            tableLabel = "Bungkus",
            subtotal = 30000,
            discount = 0,
            serviceCharge = 0,
            tax = 0,
            rounding = 0,
            total = 30000,
            status = "paid",
            paidAmount = 30000,
            shiftId = "shift-01"
        )
        database.orderDao().insertOrder(orderEntity)

        val paymentEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "payment.recorded",
                    "occurred_at": "2026-11-04T10:10:00Z",
                    "payload": {
                        "payment_id": "${UUID.randomUUID()}",
                        "order_id": "$orderId",
                        "method": "cash",
                        "amount": 30000
                    }
                }
            ]
        """.trimIndent()

        val pendingOutbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-01",
            eventsJson = paymentEventsJson,
            status = OutboxStatus.PENDING
        )
        database.outboxDao().insert(pendingOutbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            appId = "cafe",
            appVersion = "1.0.0",
            currentDeviceIdProvider = { "dev-cafe-1" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            customerDao = database.customerDao(),
            filesDirProvider = { appContext.filesDir }
        )

        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        val recordedSecondRequest = java.util.concurrent.atomic.AtomicReference<String>()

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val callNum = requestCount.incrementAndGet()
                val bodyStr = request.body.readUtf8()
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content

                return if (callNum == 1) {
                    // Server tolak pertama kali: order belum diterima server
                    MockResponse().setResponseCode(200).setBody("""
                        {
                            "results": [
                                {
                                    "bundle_id": "$sentBundleId",
                                    "status": "REJECTED",
                                    "reason_code": "DEPENDENCY_MISSING",
                                    "message": "order belum diterima server",
                                    "retryable": true
                                }
                            ],
                            "server_time": "2026-11-04T10:10:05Z"
                        }
                    """.trimIndent())
                } else {
                    // Pengiriman kedua (setelah auto-heal)
                    recordedSecondRequest.set(bodyStr)
                    MockResponse().setResponseCode(200).setBody("""
                        {
                            "results": [
                                {
                                    "bundle_id": "$sentBundleId",
                                    "status": "ACCEPTED"
                                }
                            ],
                            "server_time": "2026-11-04T10:10:10Z"
                        }
                    """.trimIndent())
                }
            }
        }

        // Pengiriman 1: Server merespon REJECTED order belum diterima server -> memicu auto-heal
        val summary1 = customSender.sendPendingBundles()
        assertEquals(1, summary1.failed)

        // Verifikasi bundel telah dipulihkan ke PENDING dan eventsJson memuat order.created
        val pendingList = database.outboxDao().getOutboxByStatus(OutboxStatus.PENDING)
        assertEquals(1, pendingList.size)
        org.junit.Assert.assertTrue("EventsJson harus sudah memuat order.created", pendingList[0].eventsJson.contains("order.created"))

        // Pengiriman 2: Bundel yang ter-autoheal kini terkirim dan sukses ACCEPTED
        val summary2 = customSender.sendPendingBundles()
        assertEquals(1, summary2.sent)

        val secondPayload = recordedSecondRequest.get()
        assertNotNull(secondPayload)
        org.junit.Assert.assertTrue("Harus memuat order.created", secondPayload.contains(""""type":"order.created""""))
        org.junit.Assert.assertTrue("Harus memuat payment.recorded", secondPayload.contains(""""type":"payment.recorded""""))
    }

    @Test
    fun `SyncSender auto-heals laundry bundle by prepending shift opened and setting valid shiftId when server rejected with 'shift belum diterima server'`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0010",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Ibu Sari",
                            "phone": "081234567890"
                        },
                        "lines": [
                            {
                                "line_id": "${UUID.randomUUID()}",
                                "item_ref_id": 1,
                                "name": "Cuci Komplit",
                                "unit": "kg",
                                "weight_grams": 2000,
                                "express": false,
                                "rate": 10000,
                                "line_total": 20000,
                                "quantity": 1,
                                "unit_price": 10000
                            }
                        ],
                        "subtotal": 20000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 20000
                    }
                }
            ]
        """.trimIndent()

        val failedOutbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = "old-ghost-shift-id",
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 2,
            lastError = "shift belum diterima server",
            reasonCode = "DEPENDENCY_MISSING"
        )
        database.outboxDao().insert(failedOutbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            appId = "laundry",
            appVersion = "1.0.0",
            currentDeviceIdProvider = { "dev-laundry-1" },
            currentDeviceCodeProvider = { "L2" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            customerDao = database.customerDao(),
            shiftDao = database.shiftDao(),
            filesDirProvider = { ApplicationProvider.getApplicationContext<Context>().filesDir }
        )

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val sentShiftId = sentBundle["shift_id"]?.jsonPrimitive?.content
        assertNotNull("shift_id wajib ada dan tidak boleh kosong (memenuhi OQ-28 Laravel validator)", sentShiftId)
        org.junit.Assert.assertTrue("shift_id harus valid UUID", sentShiftId!!.isNotBlank())
        org.junit.Assert.assertTrue("Harus memuat shift.opened secara otomatis", sentPayload.contains(""""type":"shift.opened""""))
        org.junit.Assert.assertTrue("Harus memuat order.created", sentPayload.contains(""""type":"order.created""""))
    }

    @Test
    fun `SyncSender auto-heals cafe bundle by prepending shift opened when server rejected with 'shift belum diterima server'`() = runBlocking {
        val shiftId = UUID.randomUUID().toString()
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        // 1. Simpan ShiftEntity di Room database
        val shiftEntity = id.a3.core.data.local.entity.ShiftEntity(
            id = shiftId,
            businessUnitId = "bu-cafe-1",
            deviceId = "dev-cafe-1",
            cashierId = "3",
            cashierName = "Budi Kasir",
            openingCash = 200000L,
            openingCashBreakdownJson = """{"100000":2}""",
            openedAt = System.currentTimeMillis() - 60000L,
            status = "open"
        )
        database.shiftDao().insertShift(shiftEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:01:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C8-0001",
                        "order_type": "dine_in",
                        "table_label": "Meja 1",
                        "lines": [
                            {
                                "line_id": "${UUID.randomUUID()}",
                                "item_ref_id": 1,
                                "name": "Espresso",
                                "quantity": 1,
                                "unit_price": 20000,
                                "options": []
                            }
                        ],
                        "subtotal": 20000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 20000
                    }
                }
            ]
        """.trimIndent()

        val failedOutbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = shiftId,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 2,
            lastError = "shift belum diterima server",
            reasonCode = "DEPENDENCY_MISSING"
        )
        database.outboxDao().insert(failedOutbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            appId = "cafe",
            appVersion = "1.0.0",
            currentDeviceIdProvider = { "dev-cafe-1" },
            currentDeviceCodeProvider = { "C8" },
            shiftDao = database.shiftDao(),
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            customerDao = database.customerDao(),
            filesDirProvider = { ApplicationProvider.getApplicationContext<Context>().filesDir }
        )

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        org.junit.Assert.assertTrue("Harus memuat shift.opened", sentPayload.contains(""""type":"shift.opened""""))
        org.junit.Assert.assertTrue("Harus memuat order.created", sentPayload.contains(""""type":"order.created""""))
        org.junit.Assert.assertTrue("shift.opened harus mendahului order.created", sentPayload.indexOf(""""shift.opened"""") < sentPayload.indexOf(""""order.created""""))
    }

    @Test
    fun `SyncSender auto-heals when server rejected with 'the shift id field is required' by prepending shift opened and setting valid shiftId`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L2-0015",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Pak Ahmad",
                            "phone": "081333444555"
                        },
                        "lines": [
                            {
                                "line_id": "${UUID.randomUUID()}",
                                "item_ref_id": 1,
                                "name": "Cuci Lipat",
                                "unit": "kg",
                                "weight_grams": 3000,
                                "express": false,
                                "rate": 8000,
                                "line_total": 24000,
                                "quantity": 1,
                                "unit_price": 8000
                            }
                        ],
                        "subtotal": 24000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 24000
                    }
                }
            ]
        """.trimIndent()

        val failedOutbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "shift_id : the shift id field is required",
            reasonCode = "VALIDATION_FAILED"
        )
        database.outboxDao().insert(failedOutbox)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            appId = "laundry",
            appVersion = "1.0.0",
            currentDeviceIdProvider = { "dev-laundry-1" },
            currentDeviceCodeProvider = { "L2" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao(),
            syncStateDao = database.syncStateDao(),
            cashOutDao = database.cashOutDao(),
            customerDao = database.customerDao(),
            shiftDao = database.shiftDao(),
            filesDirProvider = { ApplicationProvider.getApplicationContext<Context>().filesDir }
        )

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val sentShiftId = sentBundle["shift_id"]?.jsonPrimitive?.content
        assertNotNull("shift_id wajib ada dan tidak boleh kosong", sentShiftId)
        org.junit.Assert.assertTrue("Harus memuat shift.opened yang ter-prepend otomatis", sentPayload.contains(""""type":"shift.opened""""))
        org.junit.Assert.assertTrue("Harus memuat order.created", sentPayload.contains(""""type":"order.created""""))
    }

    @Test
    fun `SyncSender ensures laundry orders remain 0 tax and total equals subtotal even when tax_percent is configured in sync_state`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        // Pasang konfigurasi pajak 10% di sync_state (misal hasil bootstrap atau pengaturan toko)
        database.syncStateDao().set(id.a3.core.data.local.entity.SyncStateEntity(key = "tax_percent", value = "10"))

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "L1-0008",
            businessUnitId = "bu-laundry-01",
            shiftId = "shift-001",
            deviceId = "dev-laundry-1",
            cashierId = "cashier-laundry",
            orderType = "laundry",
            status = "open",
            subtotal = 31500L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 31500L,
            paidAmount = 0L
        )
        database.orderDao().insertOrder(orderEntity)

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0008",
                        "order_type": "laundry",
                        "lines": [
                            {
                                "line_id": "line-cuci-1",
                                "item_ref_id": 5,
                                "name": "Cuci Setrika Reguler",
                                "unit": "kg",
                                "weight_grams": 4500,
                                "express": false,
                                "rate": 7000,
                                "line_total": 31500
                            }
                        ],
                        "subtotal": 31500,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 31500
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            currentDeviceIdProvider = { "dev-laundry-1" },
            currentDeviceCodeProvider = { "L1" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val orderCreated = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "order.created" }.jsonObject
        val payload = orderCreated["payload"]!!.jsonObject

        assertEquals(0L, payload["tax"]!!.jsonPrimitive.content.toLong())
        assertEquals(0L, payload["service_charge"]!!.jsonPrimitive.content.toLong())
        assertEquals(0L, payload["rounding"]!!.jsonPrimitive.content.toLong())
        assertEquals(31500L, payload["total"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `SyncSender auto-recovers and sanitizes tax-contaminated laundry order back to 0 tax and correct total`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        database.syncStateDao().set(id.a3.core.data.local.entity.SyncStateEntity(key = "tax_percent", value = "10"))

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "L1-0009",
            businessUnitId = "bu-laundry-01",
            shiftId = "shift-001",
            deviceId = "dev-laundry-1",
            cashierId = "cashier-laundry",
            orderType = "laundry",
            status = "open",
            subtotal = 31500L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 3150L,
            rounding = 0L,
            total = 34650L,
            paidAmount = 0L
        )
        database.orderDao().insertOrder(orderEntity)

        // Bundel terkontaminasi pajak 10% dan sebelumnya ditolak server dengan selisih hitung total
        val contaminatedEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0009",
                        "order_type": "laundry",
                        "lines": [
                            {
                                "line_id": "line-cuci-2",
                                "item_ref_id": 5,
                                "name": "Cuci Setrika Reguler",
                                "unit": "kg",
                                "weight_grams": 4500,
                                "express": false,
                                "rate": 7000,
                                "line_total": 31500
                            }
                        ],
                        "subtotal": 31500,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 3150,
                        "rounding": 0,
                        "total": 34650
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            currentDeviceIdProvider = { "dev-laundry-1" },
            currentDeviceCodeProvider = { "L1" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = "shift-001",
            eventsJson = contaminatedEventsJson,
            status = OutboxStatus.FAILED,
            attempts = 1,
            lastError = "total hitung 31500, diterima 34650"
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val orderCreated = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "order.created" }.jsonObject
        val payload = orderCreated["payload"]!!.jsonObject

        // Verifikasi pajak telah dinolkan dan total kembali ke subtotal asli
        assertEquals(0L, payload["tax"]!!.jsonPrimitive.content.toLong())
        assertEquals(0L, payload["service_charge"]!!.jsonPrimitive.content.toLong())
        assertEquals(0L, payload["rounding"]!!.jsonPrimitive.content.toLong())
        assertEquals(31500L, payload["total"]!!.jsonPrimitive.content.toLong())

        // Verifikasi entitas lokal di Room juga telah dipulihkan
        val localOrder = database.orderDao().getOrderById(orderId)
        assertNotNull(localOrder)
        assertEquals(0L, localOrder!!.tax)
        assertEquals(31500L, localOrder.total)
    }

    @Test
    fun `SyncSender auto-injects is_dp and payment_type into payment recorded when missing`() = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()

        val orderEntity = id.a3.core.data.local.entity.OrderEntity(
            id = orderId,
            displayNumber = "L1-0021",
            businessUnitId = "bu-laundry-01",
            shiftId = "shift-001",
            deviceId = "dev-laundry-1",
            cashierId = "cashier-laundry",
            orderType = "laundry",
            status = "open",
            subtotal = 53000L,
            discount = 0L,
            serviceCharge = 0L,
            tax = 0L,
            rounding = 0L,
            total = 53000L,
            paidAmount = 20000L
        )
        database.orderDao().insertOrder(orderEntity)

        // Payload payment lama/offline tanpa field is_dp & payment_type
        val paymentEventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "payment.recorded",
                    "occurred_at": "2026-11-04T12:00:00Z",
                    "payload": {
                        "payment_id": "$paymentId",
                        "order_id": "$orderId",
                        "method": "cash",
                        "amount": 20000,
                        "tendered": 20000,
                        "idempotency_key": "${UUID.randomUUID()}"
                    }
                }
            ]
        """.trimIndent()

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentShiftIdProvider = { "shift-001" },
            currentDeviceIdProvider = { "dev-laundry-1" },
            currentDeviceCodeProvider = { "L1" },
            orderDao = database.orderDao(),
            syncStateDao = database.syncStateDao()
        )

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = "shift-001",
            eventsJson = paymentEventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
                val sentBundleId = sentBundle["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:05:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val paymentRecorded = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "payment.recorded" }.jsonObject
        val payload = paymentRecorded["payload"]!!.jsonObject

        // Verifikasi is_dp true dan payment_type dp terinjeksi otomatis karena 20000 < total 53000
        assertEquals(true, payload["is_dp"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("dp", payload["payment_type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SyncSender strips weight_grams and ensures numeric item_ref_id for pcs laundry service (Poin 4)`() = runBlocking {
        // Master data Room hanya memiliki entitas awal berbasis string (misal belum bootstrap)
        val initialPcsService = id.a3.core.data.local.entity.LaundryServiceEntity(
            id = "svc-selimut",
            name = "Selimut Tebal",
            unit = "pcs",
            rate = 25000L,
            expressRate = 35000L,
            minWeight = 1.0,
            weightStep = 1.0,
            active = true
        )
        database.masterDataDao().upsertLaundryService(initialPcsService)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { "L1" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        // Bundel dengan baris pcs yang terkontaminasi weight_grams dan item_ref_id string
        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:10:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0021",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Ibu Rahma",
                            "phone": "081234567899"
                        },
                        "lines": [
                            {
                                "line_id": "line-pcs-1",
                                "item_ref_id": "svc-selimut",
                                "name": "Selimut Tebal",
                                "unit": "pcs",
                                "weight_grams": 2000,
                                "rate": 25000,
                                "line_total": 50000,
                                "quantity": 2,
                                "unit_price": 25000
                            }
                        ],
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
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:15:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)

        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val orderCreated = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "order.created" }.jsonObject
        val sentLine = orderCreated["payload"]!!.jsonObject["lines"]!!.jsonArray[0].jsonObject

        // Verifikasi item_ref_id numerik 4L (terpetakan dari svc-selimut) dan bukan string
        assertEquals(4L, sentLine["item_ref_id"]!!.jsonPrimitive.content.toLong())
        assertEquals("pcs", sentLine["unit"]!!.jsonPrimitive.content)
        assertEquals(2, sentLine["quantity"]!!.jsonPrimitive.content.toInt())
        assertEquals(50000L, sentLine["line_total"]!!.jsonPrimitive.content.toLong())

        // Verifikasi mutlak: atribut weight_grams berhasil dihapus total dari baris pcs!
        org.junit.Assert.assertFalse("Atribut weight_grams wajib dihapus dari baris layanan satuan pcs", sentLine.containsKey("weight_grams"))
    }

    @Test
    fun `SyncSender maps initial string service ID svc-kiloan to numeric ID 1 when master data only has initial string entities (Poin 4)`() = runBlocking {
        val initialKiloanService = id.a3.core.data.local.entity.LaundryServiceEntity(
            id = "svc-kiloan",
            name = "Kiloan Reguler (Cuci + Setrika)",
            unit = "kg",
            rate = 7000L,
            expressRate = 10000L,
            minWeight = 1.0,
            weightStep = 0.5,
            active = true
        )
        database.masterDataDao().upsertLaundryService(initialKiloanService)

        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json,
            currentDeviceCodeProvider = { "L1" },
            orderDao = database.orderDao(),
            masterDataDao = database.masterDataDao()
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T12:20:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "L1-0022",
                        "order_type": "laundry",
                        "customer": {
                            "name": "Pak Joko",
                            "phone": "081234567811"
                        },
                        "lines": [
                            {
                                "line_id": "line-kg-1",
                                "item_ref_id": "svc-kiloan",
                                "name": "Kiloan Reguler (Cuci + Setrika)",
                                "unit": "kg",
                                "weight_grams": 3000,
                                "rate": 7000,
                                "line_total": 21000,
                                "quantity": 1,
                                "unit_price": 7000
                            }
                        ],
                        "subtotal": 21000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 21000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:25:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)

        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val orderCreated = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "order.created" }.jsonObject
        val sentLine = orderCreated["payload"]!!.jsonObject["lines"]!!.jsonArray[0].jsonObject

        // Verifikasi item_ref_id numerik 1L terpetakan dari svc-kiloan
        assertEquals(1L, sentLine["item_ref_id"]!!.jsonPrimitive.content.toLong())
        assertEquals("kg", sentLine["unit"]!!.jsonPrimitive.content)
        assertEquals(3000L, sentLine["weight_grams"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `SyncSender handles direct laundry status_changed with status diambil and normalizes approved_by (Poin 4)`() = runBlocking {
        val customSender = SyncSender(
            outboxDao = database.outboxDao(),
            apiService = apiService,
            json = json
        )

        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "laundry.status_changed",
                    "occurred_at": "2026-11-04T12:30:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "status": "diambil",
                        "order_line_id": "$lineId",
                        "quantity": 1,
                        "is_final": true,
                        "approved_by": "u-manager-1"
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-laundry-1",
            shiftId = null,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        database.outboxDao().insert(outbox)

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T12:35:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = customSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)

        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val statusChanged = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "laundry.status_changed" }.jsonObject
        val payload = statusChanged["payload"]!!.jsonObject

        assertEquals("diambil", payload["status"]!!.jsonPrimitive.content)
        assertEquals(4L, payload["user_id"]!!.jsonPrimitive.content.toLong())
        assertEquals(4L, payload["approved_by"]!!.jsonPrimitive.content.toLong())
        org.junit.Assert.assertTrue("line_ids harus array membawa lineId", payload["line_ids"]!!.jsonArray.any { it.jsonPrimitive.content == lineId })
    }

    @Test
    fun `sendPendingBundles sanitizes string item_ref_id in cafe order lines to numeric integer`(): Unit = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val lineId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2026-11-04T10:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C1-0005",
                        "order_type": "dine_in",
                        "table_label": "Meja 01",
                        "lines": [
                            {
                                "line_id": "$lineId",
                                "item_ref_id": "menu-latte",
                                "name": "Caffe Latte",
                                "quantity": 1,
                                "unit_price": 34000
                            }
                        ],
                        "subtotal": 34000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 34000
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

        val recordedRequest = java.util.concurrent.atomic.AtomicReference<String>()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                recordedRequest.set(bodyStr)
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$bundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T10:01:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val sentPayload = recordedRequest.get()
        assertNotNull(sentPayload)
        val jsonBody = json.parseToJsonElement(sentPayload).jsonObject
        val sentBundle = jsonBody["bundles"]!!.jsonArray[0].jsonObject
        val orderCreated = sentBundle["events"]!!.jsonArray.first { it.jsonObject["type"]!!.jsonPrimitive.content == "order.created" }.jsonObject
        val line = orderCreated["payload"]!!.jsonObject["lines"]!!.jsonArray[0].jsonObject

        // Verifikasi item_ref_id terkonversi menjadi Long numeric (1L untuk menu-latte)
        assertEquals(1L, line["item_ref_id"]!!.jsonPrimitive.content.toLong())
        // Verifikasi options array kosong disertakan
        assertNotNull(line["options"])
    }

    @Test
    fun `autoRecoverFailedBundles automatically corrects CLOCK_SKEW future timestamp and retries bundle`(): Unit = runBlocking {
        val bundleId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        val eventsJson = """
            [
                {
                    "event_id": "${UUID.randomUUID()}",
                    "type": "order.created",
                    "occurred_at": "2029-01-01T00:00:00Z",
                    "payload": {
                        "order_id": "$orderId",
                        "display_number": "C1-0009",
                        "order_type": "dine_in",
                        "table_label": "Meja 01",
                        "lines": [],
                        "subtotal": 20000,
                        "discount": 0,
                        "service_charge": 0,
                        "tax": 0,
                        "rounding": 0,
                        "total": 20000
                    }
                }
            ]
        """.trimIndent()

        val outbox = OutboxEntity(
            bundleId = bundleId,
            deviceId = "dev-cafe-1",
            shiftId = "shift-001",
            eventsJson = eventsJson,
            status = OutboxStatus.FAILED,
            lastError = "occurred_at tidak boleh lebih dari 5 menit di masa depan (CLOCK_SKEW)",
            reasonCode = "CLOCK_SKEW"
        )
        database.outboxDao().insert(outbox)

        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val bodyStr = request.body.readUtf8()
                val jsonBody = json.parseToJsonElement(bodyStr).jsonObject
                val sentBundleId = jsonBody["bundles"]!!.jsonArray[0].jsonObject["bundle_id"]!!.jsonPrimitive.content
                return MockResponse().setResponseCode(200).setBody("""
                    {
                        "results": [
                            {
                                "bundle_id": "$sentBundleId",
                                "status": "ACCEPTED"
                            }
                        ],
                        "server_time": "2026-11-04T10:00:00Z"
                    }
                """.trimIndent())
            }
        }

        val summary = syncSender.sendPendingBundles()
        assertEquals(1, summary.sent)

        val failedList = database.outboxDao().getFailedOutbox()
        org.junit.Assert.assertTrue("Antrean bermasalah harus bersih setelah CLOCK_SKEW dipulihkan", failedList.isEmpty())
    }
}
