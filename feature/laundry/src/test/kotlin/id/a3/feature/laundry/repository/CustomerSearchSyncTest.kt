package id.a3.feature.laundry.repository

import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.sync.api.SyncApiService
import id.a3.core.sync.model.CustomerSearchResponse
import id.a3.core.sync.model.FileUploadResponse
import id.a3.core.sync.model.OrderNotificationStatusResponse
import id.a3.core.sync.model.RemoteCustomerDto
import id.a3.core.sync.model.SyncRequest
import id.a3.core.sync.model.SyncResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class CustomerSearchSyncTest {

    private val localCustomerList = mutableListOf<CustomerEntity>()
    private val customersFlow = MutableStateFlow<List<CustomerEntity>>(emptyList())

    private val fakeCustomerDao = object : CustomerDao {
        override suspend fun insertCustomer(customer: CustomerEntity) {
            localCustomerList.removeAll { it.id == customer.id || it.phone == customer.phone }
            localCustomerList.add(customer)
            customersFlow.value = localCustomerList.toList()
        }

        override suspend fun upsertCustomers(customers: List<CustomerEntity>) {
            customers.forEach { insertCustomer(it) }
        }

        override suspend fun updateCustomer(customer: CustomerEntity) {
            insertCustomer(customer)
        }

        override suspend fun getCustomerById(id: String): CustomerEntity? =
            localCustomerList.find { it.id == id }

        override suspend fun findCustomerByPhone(phone: String): CustomerEntity? =
            localCustomerList.find { it.phone == phone }

        override fun searchCustomers(query: String): Flow<List<CustomerEntity>> =
            customersFlow.map { list ->
                list.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
            }

        override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> =
            customersFlow

        override fun observeCustomersByIds(ids: List<String>): Flow<List<CustomerEntity>> =
            customersFlow.map { list -> list.filter { it.id in ids } }

        override suspend fun deleteCustomer(id: String) {
            localCustomerList.removeAll { it.id == id }
            customersFlow.value = localCustomerList.toList()
        }

        override suspend fun clearCustomers() {
            localCustomerList.clear()
            customersFlow.value = emptyList()
        }
    }

    private val fakeMasterDataDao: MasterDataDao = Proxy.newProxyInstance(
        MasterDataDao::class.java.classLoader,
        arrayOf(MasterDataDao::class.java)
    ) { _, _, _ -> null } as MasterDataDao

    private val fakeOrderDao: OrderDao = Proxy.newProxyInstance(
        OrderDao::class.java.classLoader,
        arrayOf(OrderDao::class.java)
    ) { _, _, _ -> null } as OrderDao

    private val fakeSyncApiService = object : SyncApiService {
        var remoteCustomers = listOf<RemoteCustomerDto>()
        var queryReceived: String? = null

        override suspend fun sendBundles(request: SyncRequest, appId: String?, appVersion: String?): Response<SyncResponse> =
            throw NotImplementedError()

        override suspend fun uploadFile(file: MultipartBody.Part, fileId: RequestBody, kind: RequestBody, appId: String?, appVersion: String?): Response<FileUploadResponse> =
            throw NotImplementedError()

        override suspend fun getOrderNotificationStatus(orderId: String, appId: String?, appVersion: String?): Response<OrderNotificationStatusResponse> =
            throw NotImplementedError()

        override suspend fun searchCustomers(query: String, appId: String?, appVersion: String?): Response<CustomerSearchResponse> {
            queryReceived = query
            val filtered = remoteCustomers.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
            return Response.success(CustomerSearchResponse(customers = filtered))
        }
    }

    @Test
    fun `searchCustomers returns local data and syncs remote customers into Room`() = runBlocking {
        // 1. Data lokal awal di Room
        val localCustomer = CustomerEntity(
            id = "c-local-1",
            name = "Ibu Siti",
            phone = "081211112222"
        )
        fakeCustomerDao.insertCustomer(localCustomer)

        // 2. Data di backend server (+62 internasional)
        fakeSyncApiService.remoteCustomers = listOf(
            RemoteCustomerDto(
                id = null,
                name = "Ibu Siti Nurhaliza",
                phone = "+62 812-3456-7890",
                waOptOut = false
            )
        )

        val repository = DefaultLaundryOrderRepository(
            masterDataDao = fakeMasterDataDao,
            orderDao = fakeOrderDao,
            transactionManager = null,
            customerDao = fakeCustomerDao,
            syncApiService = fakeSyncApiService
        )

        // Cari "Siti"
        val flow = repository.searchCustomers("Siti")
        val result = flow.first()

        // Verifikasi API terpanggil
        assertEquals("Siti", fakeSyncApiService.queryReceived)

        // Verifikasi pelanggan remote tersimpan ke Room dengan normalisasi 08
        val savedInRoom = fakeCustomerDao.findCustomerByPhone("081234567890")
        assertNotNull("Pelanggan remote harus di-upsert ke Room", savedInRoom)
        assertEquals("Ibu Siti Nurhaliza", savedInRoom!!.name)
        assertEquals("081234567890", savedInRoom.phone)

        // Verifikasi hasil pencarian memuat kedua pelanggan
        assertTrue(result.any { it.name == "Ibu Siti" })
        assertTrue(result.any { it.name == "Ibu Siti Nurhaliza" })
    }

    @Test
    fun `seedInitialServicesIfEmpty seeds initial laundry services when database is empty`() = runBlocking {
        val servicesList = mutableListOf<LaundryServiceEntity>()
        val masterDao = object : MasterDataDao by fakeMasterDataDao {
            override suspend fun countLaundryServices(): Int = servicesList.size
            override suspend fun upsertLaundryServices(services: List<LaundryServiceEntity>) {
                servicesList.addAll(services)
            }
        }

        val repository = DefaultLaundryOrderRepository(
            masterDataDao = masterDao,
            orderDao = fakeOrderDao,
            transactionManager = null,
            customerDao = fakeCustomerDao,
            syncApiService = fakeSyncApiService
        )

        assertEquals(0, servicesList.size)
        repository.seedInitialServicesIfEmpty()
        assertEquals(DefaultLaundryOrderRepository.INITIAL_LAUNDRY_SERVICES.size, servicesList.size)

        // Seed again when not empty - should not re-add
        repository.seedInitialServicesIfEmpty()
        assertEquals(DefaultLaundryOrderRepository.INITIAL_LAUNDRY_SERVICES.size, servicesList.size)
    }
}
