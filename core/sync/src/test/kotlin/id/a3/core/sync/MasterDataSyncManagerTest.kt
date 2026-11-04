package id.a3.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.sync.api.SyncMasterApiService
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
class MasterDataSyncManagerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: AppDatabase
    private lateinit var apiService: SyncMasterApiService
    private lateinit var syncManager: MasterDataSyncManager
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

        apiService = retrofit.create(SyncMasterApiService::class.java)
        syncManager = MasterDataSyncManager(
            syncStateDao = database.syncStateDao(),
            masterDataDao = database.masterDataDao(),
            apiService = apiService
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        database.close()
    }

    @Test
    fun `interrupted bootstrap resumes from last saved cursor and completes`() = runBlocking {
        // 1. First batch: section menu_items, next_cursor = "page-2", done = false
        val page1Json = """
            {
                "section": "menu_items",
                "records": [
                    { "id": "menu-latte", "name": "Latte", "price": 34000, "category": "Coffee", "active": true }
                ],
                "next_cursor": "page-2",
                "done": false
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))

        // 2. Next request fails (simulating network disconnect/crash)
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val result1 = syncManager.syncBootstrap()
        assertTrue("Bootstrap must report interrupted", result1 is BootstrapResult.Interrupted)
        assertEquals("page-2", (result1 as BootstrapResult.Interrupted).lastSavedCursor)

        // Verify page 1 items are saved
        val latte = database.masterDataDao().getMenuItem("menu-latte")
        assertNotNull(latte)
        assertEquals(34000L, latte!!.price)

        // Verify cursor page-2 is persisted in Room
        val savedCursor = database.syncStateDao().get(MasterDataSyncManager.KEY_BOOTSTRAP_CURSOR)
        assertEquals("page-2", savedCursor)

        // 3. Network recovers: server provides page 2 with done = true
        val page2Json = """
            {
                "section": "laundry_services",
                "records": [
                    { "id": "ls-reguler", "name": "Cuci Setrika", "unit": "kg", "rate": 7000, "active": true }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-100"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(page2Json))

        // Resume bootstrap!
        val result2 = syncManager.syncBootstrap()
        assertTrue("Bootstrap must complete", result2 is BootstrapResult.Completed)
        assertEquals("delta-100", (result2 as BootstrapResult.Completed).deltaCursor)

        // Verify request to server used cursor=page-2
        mockWebServer.takeRequest() // page 1 request
        mockWebServer.takeRequest() // failed request
        val resumeReq = mockWebServer.takeRequest()
        assertTrue("Resume request must include cursor=page-2", resumeReq.path!!.contains("cursor=page-2"))

        // Verify laundry service saved
        val service = database.masterDataDao().getLaundryService("ls-reguler")
        assertNotNull(service)
        assertEquals(7000L, service!!.rate)

        // Verify state: bootstrap cursor deleted, delta cursor set, bootstrap done = true
        assertNull(database.syncStateDao().get(MasterDataSyncManager.KEY_BOOTSTRAP_CURSOR))
        assertEquals("delta-100", database.syncStateDao().get(MasterDataSyncManager.KEY_DELTA_CURSOR))
        assertEquals("true", database.syncStateDao().get(MasterDataSyncManager.KEY_BOOTSTRAP_DONE))
    }

    @Test
    fun `delta sync applies upsert and delete to Room`() = runBlocking {
        // Pre-populate Room
        val initBootJson = """
            {
                "section": "menu_items",
                "records": [
                    { "id": "menu-latte", "name": "Latte", "price": 34000, "category": "Coffee", "active": true },
                    { "id": "menu-croissant", "name": "Croissant", "price": 28000, "category": "Pastry", "active": true }
                ],
                "done": true,
                "delta_cursor": "delta-100"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(initBootJson))
        syncManager.syncBootstrap()

        // Delta changes: upsert Latte with new price, delete Croissant
        val deltaChangesJson = """
            {
                "changes": [
                    {
                        "table": "menu_items",
                        "op": "upsert",
                        "record": { "id": "menu-latte", "name": "Latte Vanilla", "price": 38000, "category": "Coffee", "active": true }
                    },
                    {
                        "table": "menu_items",
                        "op": "delete",
                        "record": { "id": "menu-croissant" }
                    }
                ],
                "next_cursor": "delta-101",
                "has_more": false
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaChangesJson))

        val deltaResult = syncManager.syncDelta()
        assertTrue("Delta sync must succeed", deltaResult is DeltaResult.Success)
        val success = deltaResult as DeltaResult.Success
        assertEquals("delta-101", success.newCursor)
        assertEquals(2, success.count)

        // Verify Latte price updated
        val updatedLatte = database.masterDataDao().getMenuItem("menu-latte")
        assertNotNull(updatedLatte)
        assertEquals("Latte Vanilla", updatedLatte!!.name)
        assertEquals(38000L, updatedLatte.price)

        // Verify Croissant was deleted
        val deletedCroissant = database.masterDataDao().getMenuItem("menu-croissant")
        assertNull(deletedCroissant)

        // Verify delta cursor updated
        assertEquals("delta-101", database.syncStateDao().get(MasterDataSyncManager.KEY_DELTA_CURSOR))
    }

    @Test
    fun `expired cursor returning 410 triggers automatic rebootstrapping`() = runBlocking {
        // Setup state with an old/expired delta cursor
        database.syncStateDao().set(
            id.a3.core.data.local.entity.SyncStateEntity(
                key = MasterDataSyncManager.KEY_DELTA_CURSOR,
                value = "expired-cursor"
            )
        )
        database.syncStateDao().set(
            id.a3.core.data.local.entity.SyncStateEntity(
                key = MasterDataSyncManager.KEY_BOOTSTRAP_DONE,
                value = "true"
            )
        )

        // 1. Server responds to GET /changes with HTTP 410 Gone (Cursor Expired)
        val expired410Json = """
            {
                "code": "CURSOR_EXPIRED",
                "message": "Cursor kedaluwarsa, silakan lakukan bootstrap ulang"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(410).setBody(expired410Json))

        // 2. Server provides fresh bootstrap
        val freshBootstrapJson = """
            {
                "section": "menu_items",
                "records": [
                    { "id": "menu-fresh", "name": "Fresh Coffee", "price": 25000, "category": "Coffee", "active": true }
                ],
                "done": true,
                "delta_cursor": "fresh-delta-1"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(freshBootstrapJson))

        val deltaResult = syncManager.syncDelta()
        assertTrue("Expired cursor must trigger rebootstrapping", deltaResult is DeltaResult.Rebootstrapped)
        assertEquals("fresh-delta-1", (deltaResult as DeltaResult.Rebootstrapped).newDeltaCursor)

        // Verify fresh item was saved
        val freshItem = database.masterDataDao().getMenuItem("menu-fresh")
        assertNotNull(freshItem)
        assertEquals(25000L, freshItem!!.price)

        // Verify delta cursor was updated to fresh-delta-1
        assertEquals("fresh-delta-1", database.syncStateDao().get(MasterDataSyncManager.KEY_DELTA_CURSOR))
    }

    @Test
    fun `bootstrap saves menu_options and delta sync updates them`() = runBlocking {
        val bootJson = """
            {
                "section": "menu_options",
                "records": [
                    { "id": "1", "menu_item_id": "1", "kind": "varian", "name": "Large", "price_delta": 5000, "active": true },
                    { "id": "2", "menu_item_id": "1", "kind": "addon", "name": "Extra Shot", "price_delta": 6000, "active": true }
                ],
                "done": true,
                "delta_cursor": "delta-opt-1"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bootJson))

        val bootResult = syncManager.syncBootstrap()
        assertTrue(bootResult is BootstrapResult.Completed)

        val options = database.masterDataDao().getActiveOptionsForMenuItem("1")
        assertEquals(2, options.size)
        val largeOpt = options.find { it.id == "1" }
        assertNotNull(largeOpt)
        assertEquals("Large", largeOpt!!.name)
        assertEquals("variant", largeOpt.kind)
        assertEquals(5000L, largeOpt.priceDelta)

        // Delta sync update: add Oat Milk, delete Extra Shot (id: 2)
        val deltaJson = """
            {
                "changes": [
                    {
                        "table": "menu_options",
                        "op": "upsert",
                        "record": { "id": "3", "menu_item_id": "1", "kind": "addon", "name": "Oat Milk", "price_delta": 7000, "active": true }
                    },
                    {
                        "table": "menu_options",
                        "op": "delete",
                        "record": { "id": "2" }
                    }
                ],
                "next_cursor": "delta-opt-2",
                "has_more": false
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaJson))

        val deltaResult = syncManager.syncDelta()
        assertTrue(deltaResult is DeltaResult.Success)

        val updatedOptions = database.masterDataDao().getActiveOptionsForMenuItem("1")
        assertEquals(2, updatedOptions.size)
        val names = updatedOptions.map { it.name }
        assertTrue(names.contains("Large"))
        assertTrue(names.contains("Oat Milk"))
    }

    @Test
    fun `syncBootstrap downloads discount presets and saves to Room`() = runBlocking {
        val bootstrapPage = """
            {
                "section": "discount_presets",
                "records": [
                    { "id": "pre-1", "name": "Diskon Member 10%", "type": "percent", "value": 10, "active": true },
                    { "id": "pre-2", "name": "Voucher Kopi Rp10.000", "type": "amount", "value": 10000, "active": true }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-pre-1"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(bootstrapPage))

        val result = syncManager.syncBootstrap()
        assertTrue(result is BootstrapResult.Completed)

        val presets = database.masterDataDao().getActiveDiscountPresets()
        assertEquals(2, presets.size)
        assertEquals("Diskon Member 10%", presets[0].name)
        assertEquals("Voucher Kopi Rp10.000", presets[1].name)
    }

    @Test
    fun `syncConfig fetches remote settings and saves tax and service charge to sync_state in Room`() = runBlocking {
        val configResponse = """
            {
                "settings": {
                    "tax_percent": 10,
                    "tax_mode": "added",
                    "service_charge_percent": 5,
                    "rounding_step": 100
                },
                "feature_flags": {},
                "min_app_version": "1.0.0",
                "labels": {}
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(configResponse))

        val success = syncManager.syncConfig()
        assertTrue(success)

        val tax = database.syncStateDao().get("tax_percent")
        val mode = database.syncStateDao().get("tax_mode")
        val service = database.syncStateDao().get("service_charge_percent")

        assertEquals("10", tax)
        assertEquals("added", mode)
        assertEquals("5", service)
    }

    @Test
    fun `syncConfig converts basis point settings tax_bp and service_bp to whole percent`() = runBlocking {
        val configResponse = """
            {
                "settings": { "tax_bp": 1000, "service_bp": 500 },
                "feature_flags": {},
                "min_app_version": "1.0.0",
                "labels": {}
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(configResponse))

        assertTrue(syncManager.syncConfig())

        assertEquals("10", database.syncStateDao().get("tax_percent"))
        assertEquals("5", database.syncStateDao().get("service_charge_percent"))
    }

    @Test
    fun `syncConfig ignores basis point tax that is not a whole percent`() = runBlocking {
        val configResponse = """
            { "settings": { "tax_bp": 1050 }, "feature_flags": {}, "min_app_version": "1.0.0", "labels": {} }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(configResponse))

        assertTrue(syncManager.syncConfig())

        assertEquals(null, database.syncStateDao().get("tax_percent"))
    }

    @Test
    fun `syncServerTime fetches server time and saves offset to sync_state`() = runBlocking {
        val serverTimeIso = "2026-11-03T10:15:30Z"
        val timeResponse = """
            {
                "server_time": "$serverTimeIso",
                "timezone": "UTC"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(timeResponse))

        val offset = syncManager.syncServerTime()
        assertNotNull(offset)

        val savedOffset = database.syncStateDao().get(MasterDataSyncManager.KEY_SERVER_CLOCK_OFFSET)
        assertNotNull(savedOffset)
        assertEquals(offset.toString(), savedOffset)
    }

    @Test
    fun `bootstrap with section 'services' and implicit active flag saves active laundry services`() = runBlocking {
        val pageJson = """
            {
                "section": "services",
                "records": [
                    { "id": "svc-express-kilo", "name": "Kiloan Kilat", "unit": "kiloan", "price": 12000, "express_price": 15000 }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-services-1"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pageJson))

        val result = syncManager.syncBootstrap()
        assertTrue("Bootstrap must complete", result is BootstrapResult.Completed)

        val svc = database.masterDataDao().getLaundryService("svc-express-kilo")
        assertNotNull(svc)
        assertEquals("Kiloan Kilat", svc!!.name)
        assertEquals("kg", svc.unit)
        assertEquals(12000L, svc.rate)
        assertEquals(15000L, svc.expressRate)
        assertTrue(svc.active)
    }

    @Test
    fun `delta sync with table 'services' and string price applies upsert without crashing`() = runBlocking {
        database.syncStateDao().set(id.a3.core.data.local.entity.SyncStateEntity(key = MasterDataSyncManager.KEY_BOOTSTRAP_DONE, value = "true"))
        database.syncStateDao().set(id.a3.core.data.local.entity.SyncStateEntity(key = MasterDataSyncManager.KEY_DELTA_CURSOR, value = "cursor-1"))

        val deltaJson = """
            {
                "changes": [
                    {
                        "table": "services",
                        "op": "upsert",
                        "record": {
                            "id": "svc-jas-1",
                            "name": "Jas Kantor",
                            "unit": "satuan",
                            "price": "35000",
                            "is_active": true
                        }
                    }
                ],
                "next_cursor": "cursor-2",
                "has_more": false,
                "server_time": "2026-11-03T12:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(deltaJson))

        val result = syncManager.syncDelta()
        assertTrue(result is DeltaResult.Success)

        val svc = database.masterDataDao().getLaundryService("svc-jas-1")
        assertNotNull(svc)
        assertEquals("Jas Kantor", svc!!.name)
        assertEquals("pcs", svc.unit)
        assertEquals(35000L, svc.rate)
        assertTrue(svc.active)
    }

    @Test
    fun `bootstrap with section 'Laundry' mixed case and service_id saves active laundry services`() = runBlocking {
        val pageJson = """
            {
                "section": "Laundry",
                "records": [
                    { "service_id": "svc-bedcover-super", "title": "Bed Cover Super", "unit": "pcs", "tarif": "45.000", "tarif_express": "60.000" }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-laundry-1"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pageJson))

        val result = syncManager.syncBootstrap()
        assertTrue("Bootstrap must complete", result is BootstrapResult.Completed)

        val svc = database.masterDataDao().getLaundryService("svc-bedcover-super")
        assertNotNull(svc)
        assertEquals("Bed Cover Super", svc!!.name)
        assertEquals("pcs", svc.unit)
        assertEquals(45000L, svc.rate)
        assertEquals(60000L, svc.expressRate)
        assertTrue(svc.active)
    }

    @Test
    fun `bootstrap with null section but laundry fields parses and saves active services`() = runBlocking {
        val pageJson = """
            {
                "section": null,
                "records": [
                    { "id": "svc-cuci-lipat", "name": "Cuci Lipat", "unit": "kg", "rate": 6000, "min_weight": 2.0 }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-laundry-2"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pageJson))

        val result = syncManager.syncBootstrap()
        assertTrue("Bootstrap must complete", result is BootstrapResult.Completed)

        val svc = database.masterDataDao().getLaundryService("svc-cuci-lipat")
        assertNotNull(svc)
        assertEquals("Cuci Lipat", svc!!.name)
        assertEquals("kg", svc.unit)
        assertEquals(6000L, svc.rate)
        assertEquals(2.0, svc.minWeight, 0.01)
        assertTrue(svc.active)
    }

    @Test
    fun `bootstrap with settings section containing JsonArray for discount_presets completes without crash`() = runBlocking {
        val pageJson = """
            {
                "section": "settings",
                "records": [
                    { "id": 1, "key": "opening_cash_default", "value": 200000 },
                    { "id": 4, "key": "discount_presets", "value": [{"name":"Promo 10%","type":"percent","value":1000}] },
                    { "id": 5, "key": "tax_rate", "value": 1000 }
                ],
                "next_cursor": null,
                "done": true,
                "delta_cursor": "delta-settings-done"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pageJson))

        val result = syncManager.syncBootstrap()
        assertTrue("Bootstrap must complete", result is BootstrapResult.Completed)

        val taxVal = database.syncStateDao().get(MasterDataSyncManager.KEY_TAX_PERCENT)
        assertEquals("1000", taxVal)

        val presets = database.masterDataDao().getActiveDiscountPresets()
        assertNotNull(presets)
        assertEquals(1, presets.size)
        assertEquals("Promo 10%", presets[0].name)
    }
}
