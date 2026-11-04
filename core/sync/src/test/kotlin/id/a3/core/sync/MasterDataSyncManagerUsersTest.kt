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

/** Data akun staf dari server, termasuk verifier PIN khusus perangkat, disimpan untuk persetujuan manager offline. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MasterDataSyncManagerUsersTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var manager: MasterDataSyncManager
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        db = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext<Context>())
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        manager = MasterDataSyncManager(
            syncStateDao = db.syncStateDao(),
            masterDataDao = db.masterDataDao(),
            apiService = retrofit.create(SyncMasterApiService::class.java),
            userDao = db.userDao()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun bootstrapUsers() {
        val page = """
            {
              "section": "users",
              "records": [
                { "id": 2, "name": "Siti Aminah", "role": "manager", "active": true,
                  "pin_kdf_salt": "MDEyMzQ1Njc4OWFiY2RlZg==", "pin_kdf_iterations": 120000,
                  "pin_verifier": "7yqmVjYV/c2MJ1R8mOnJN22MR46wMe9m1baM3mX3uQw=" },
                { "id": 3, "name": "Budi", "role": "kasir", "active": true,
                  "pin_kdf_salt": null, "pin_kdf_iterations": null, "pin_verifier": null }
              ],
              "next_cursor": null, "done": true, "delta_cursor": "d1"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(page))
    }

    @Test
    fun `bootstrap menyimpan akun dan verifier PIN, pin null tetap null`() = runBlocking {
        bootstrapUsers()
        assertTrue(manager.syncBootstrap() is BootstrapResult.Completed)

        val manager2 = db.userDao().getById(2L)
        assertNotNull(manager2)
        assertEquals("manager", manager2!!.role)
        assertEquals(120_000, manager2.pinKdfIterations)
        assertEquals("7yqmVjYV/c2MJ1R8mOnJN22MR46wMe9m1baM3mX3uQw=", manager2.pinVerifier)

        val kasir = db.userDao().getById(3L)
        assertNull(kasir!!.pinVerifier)
        assertNull(kasir.pinKdfSalt)
    }

    @Test
    fun `delta memperbarui akun dan menghapus akun yang dicabut`() = runBlocking {
        bootstrapUsers()
        manager.syncBootstrap()

        val delta = """
            {
              "changes": [
                { "table": "users", "op": "upsert",
                  "record": { "id": 2, "name": "Siti Aminah", "role": "manager", "active": false,
                              "pin_kdf_salt": null, "pin_kdf_iterations": null, "pin_verifier": null } },
                { "table": "users", "op": "delete", "record": { "id": 3 } }
              ],
              "next_cursor": "d2", "has_more": false
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(delta))

        assertTrue(manager.syncDelta() is DeltaResult.Success)

        assertEquals(false, db.userDao().getById(2L)!!.active)
        assertNull(db.userDao().getById(3L))
        assertTrue(db.userDao().getActiveApprovers(listOf("manager")).isEmpty())
    }
}
