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
}
