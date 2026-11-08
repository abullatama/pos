package id.a3.feature.laundry.repository

import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.sync.model.RemoteCustomerDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** DAO palsu yang meniru REPLACE Room: baris dengan id sama atau nomor sama (unik) ikut terhapus. */
private class FakeCustomerDao : CustomerDao {
    val rows = mutableListOf<CustomerEntity>()

    override suspend fun insertCustomer(customer: CustomerEntity) {
        rows.removeAll { it.id == customer.id || it.phone == customer.phone }
        rows.add(customer)
    }

    override suspend fun upsertCustomers(customers: List<CustomerEntity>) = customers.forEach { insertCustomer(it) }
    override suspend fun updateCustomer(customer: CustomerEntity) {}
    override suspend fun getCustomerById(id: String): CustomerEntity? = rows.firstOrNull { it.id == id }
    override suspend fun findCustomerByPhone(phone: String): CustomerEntity? = rows.firstOrNull { it.phone == phone }
    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> = flowOf(rows.toList())
    override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> = flowOf(rows.toList())
    override fun observeCustomersByIds(ids: List<String>): Flow<List<CustomerEntity>> =
        flowOf(rows.filter { it.id in ids })
    override suspend fun deleteCustomer(id: String) { rows.removeAll { it.id == id } }
    override suspend fun clearCustomers() { rows.clear() }
}

class StoreRemoteCustomersTest {
    @Test
    fun `pelanggan server dengan nomor sama dan id lain tidak menimpa pelanggan lokal`() = runTest {
        val dao = FakeCustomerDao()
        dao.insertCustomer(CustomerEntity(id = "lokal-1", name = "abul", phone = "081224921695"))

        storeRemoteCustomers(dao, listOf(RemoteCustomerDto(id = "server-9", name = "Abul", phone = "+6281224921695")))

        assertNotNull(dao.getCustomerById("lokal-1"))
        assertNull(dao.getCustomerById("server-9"))
        assertEquals(1, dao.rows.size)
        assertEquals("abul", dao.rows.single().name)
    }

    @Test
    fun `server tanpa id tidak menimpa pelanggan lokal bernomor sama`() = runTest {
        val dao = FakeCustomerDao()
        dao.insertCustomer(CustomerEntity(id = "lokal-1", name = "abul", phone = "081224921695"))

        storeRemoteCustomers(dao, listOf(RemoteCustomerDto(id = null, name = "Abul", phone = "081224921695")))

        assertEquals("lokal-1", dao.rows.single().id)
    }

    @Test
    fun `pelanggan baru dari server disimpan`() = runTest {
        val dao = FakeCustomerDao()

        storeRemoteCustomers(dao, listOf(RemoteCustomerDto(id = "server-9", name = "Sari", phone = "6281300000001")))

        val saved = dao.getCustomerById("server-9")
        assertNotNull(saved)
        assertEquals("081300000001", saved!!.phone)
    }

    @Test
    fun `id sama diperbarui`() = runTest {
        val dao = FakeCustomerDao()
        dao.insertCustomer(CustomerEntity(id = "server-9", name = "Sari", phone = "081300000001"))

        storeRemoteCustomers(dao, listOf(RemoteCustomerDto(id = "server-9", name = "Sari Dewi", phone = "081300000001")))

        assertEquals("Sari Dewi", dao.rows.single().name)
    }
}
