package id.a3.core.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.repository.OrderTransactionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Menguji query daftar cucian yang disaring di SQL (filter bawaan Belum Diambil, pencarian nota/nama/HP). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LaundryOrdersFilteredQueryTest {

    private lateinit var database: AppDatabase
    private lateinit var manager: OrderTransactionManager

    private fun order(id: String, number: String, status: String, customerId: String?, type: String = "laundry", at: Long) =
        OrderEntity(
            id = id,
            displayNumber = number,
            businessUnitId = "bu-laundry-01",
            shiftId = null,
            deviceId = "dev-1",
            cashierId = "cashier-1",
            orderType = type,
            customerId = customerId,
            status = status,
            subtotal = 10000L,
            total = 10000L,
            createdAt = at
        )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.buildInMemory(context)
        manager = OrderTransactionManager(database)

        database.customerDao().insertCustomer(CustomerEntity(id = "c-budi", name = "Budi Santoso", phone = "081234567890"))
        database.customerDao().insertCustomer(CustomerEntity(id = "c-siti", name = "Siti Aminah", phone = "081200000002"))

        val dao = database.orderDao()
        dao.insertOrder(order("o1", "L1-0001", "open", "c-budi", at = 1))
        dao.insertOrder(order("o2", "L1-0002", "ready", "c-siti", at = 2))
        dao.insertOrder(order("o3", "L1-0003", "picked_up", "c-budi", at = 3))
        dao.insertOrder(order("o4", "L1-0004", "void", null, at = 4))
        dao.insertOrder(order("o5", "C1-0005", "open", null, type = "dine_in", at = 5))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun ids(status: String?, unfinished: Boolean, query: String): List<String> = runBlocking {
        manager.observeLaundryOrdersFiltered(status, unfinished, query).first().map { it.order.id }
    }

    @Test
    fun `belum diambil menyembunyikan diambil dan batal serta bukan laundry`() {
        assertEquals(listOf("o2", "o1"), ids(null, true, ""))
    }

    @Test
    fun `semua menampilkan seluruh pesanan laundry terbaru dulu`() {
        assertEquals(listOf("o4", "o3", "o2", "o1"), ids(null, false, ""))
    }

    @Test
    fun `filter status tertentu`() {
        assertEquals(listOf("o3"), ids("picked_up", false, ""))
    }

    @Test
    fun `cari nama pelanggan tanpa membedakan huruf besar kecil`() {
        assertEquals(listOf("o2"), ids(null, true, "siti"))
        assertEquals(listOf("o3", "o1"), ids(null, false, "BUDI"))
    }

    @Test
    fun `cari nomor HP dan nomor nota`() {
        assertEquals(listOf("o2"), ids(null, false, "0002"))
        assertEquals(listOf("o2"), ids(null, false, "081200"))
    }

    @Test
    fun `pencarian dalam belum diambil tidak menemukan yang sudah diambil`() {
        assertEquals(listOf("o1"), ids(null, true, "budi"))
    }

    @Test
    fun `karakter khusus LIKE dicari apa adanya`() {
        assertEquals(emptyList<String>(), ids(null, false, "%"))
        assertEquals(emptyList<String>(), ids(null, false, "_"))
    }

    @Test
    fun `pencarian tanpa hasil mengembalikan daftar kosong`() {
        assertEquals(emptyList<String>(), ids(null, false, "zzz"))
    }
}
