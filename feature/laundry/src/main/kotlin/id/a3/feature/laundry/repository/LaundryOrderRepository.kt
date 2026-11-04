package id.a3.feature.laundry.repository

import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.sync.api.SyncApiService
import id.a3.core.sync.model.OrderNotificationStatusResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

interface LaundryOrderRepository {
    fun observeServices(): Flow<List<LaundryServiceEntity>>
    suspend fun seedInitialServicesIfEmpty()
    fun observeOrders(): Flow<List<OrderEntity>>

    /** Pasangan (orderId, metode bayar) untuk label Tunai/QRIS di riwayat. Hanya tampilan. */
    fun observePaymentMethods(): Flow<List<Pair<String, String>>> = flowOf(emptyList())
    fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>>
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?
    suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity?
    suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines?
    suspend fun findOrderByNumberFlexible(query: String): OrderWithLines?
    suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit>
    suspend fun advanceOrderStatus(
        orderId: String,
        targetStatus: String,
        rework: Boolean = false,
        reworkReason: String? = null,
        reworkApprovedBy: String? = null,
        userId: String? = null
    ): Result<OutboxEntity>
    suspend fun saveOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit>

    // Manajemen Pelanggan (F19)
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>
    fun observeRecentCustomers(limit: Int = 20): Flow<List<CustomerEntity>>
    suspend fun getCustomerById(id: String): CustomerEntity?
    suspend fun saveCustomerLocally(customer: CustomerEntity): Result<Unit>
    suspend fun createLaundryOrder(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        customer: CustomerEntity?,
        isNewCustomer: Boolean,
        deviceId: String = "dev-laundry-1",
        shiftId: String? = null
    ): Result<OutboxEntity>

    // Operasional Daftar Cucian, Pengambilan (F22, F24) & Cuci Ulang (F23)
    fun observeLaundryOrders(statusFilter: String? = null): Flow<List<OrderWithLines>>
    fun observePickups(orderId: String): Flow<List<LaundryPickupEntity>>
    suspend fun recordPickups(
        orderId: String,
        items: List<LaundryPickupItem>,
        userId: String? = null,
        approvedBy: String? = null,
        deviceId: String = "dev-laundry-1"
    ): Result<OutboxEntity>
    suspend fun reworkOrder(
        orderId: String,
        reason: String,
        approvedBy: String,
        deviceId: String = "dev-laundry-1"
    ): Result<OutboxEntity>

    suspend fun recordCorrection(
        orderId: String,
        type: String,
        amount: Long,
        reasonCode: String,
        approvedBy: String,
        note: String? = null
    ): Result<Unit>

    // Generator Nomor & Metriks Beranda (L01)
    suspend fun getNextDisplayNumber(prefix: String = "L1-"): String
    fun observeTodayOrdersCount(): Flow<Int>
    fun observeReadyOrdersCount(): Flow<Int>
    fun observeUnpaidOrdersCount(): Flow<Int>
    fun observeReadyOrders(limit: Int = 10): Flow<List<OrderWithLines>>

    // Status Notifikasi WhatsApp (F42)
    suspend fun getNotificationStatus(orderId: String): Result<OrderNotificationStatusResponse>

    // Pemulihan baris pakaian yang hilang (Self-healing)
    suspend fun repairMissingOrderLines(orderId: String): List<OrderLineEntity> = emptyList()
}

class DefaultLaundryOrderRepository(
    private val masterDataDao: MasterDataDao,
    private val orderDao: OrderDao,
    private val transactionManager: OrderTransactionManager? = null,
    private val customerDao: CustomerDao = transactionManager?.customerDao ?: throw IllegalArgumentException("customerDao required when transactionManager is null"),
    private val syncApiService: SyncApiService? = null
) : LaundryOrderRepository {

    companion object {
        val INITIAL_LAUNDRY_SERVICES = listOf(
            LaundryServiceEntity(
                id = "svc-kiloan",
                name = "Kiloan Reguler (Cuci + Setrika)",
                unit = "kg",
                rate = 7000L,
                expressRate = 10000L,
                minWeight = 1.0,
                weightStep = 0.5,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-cuci-saja",
                name = "Cuci Saja (Kiloan)",
                unit = "kg",
                rate = 5000L,
                expressRate = 8000L,
                minWeight = 1.0,
                weightStep = 0.5,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-setrika-saja",
                name = "Setrika Saja (Kiloan)",
                unit = "kg",
                rate = 5000L,
                expressRate = 8000L,
                minWeight = 1.0,
                weightStep = 0.5,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-selimut",
                name = "Selimut Tebal",
                unit = "pcs",
                rate = 25000L,
                expressRate = 35000L,
                minWeight = 1.0,
                weightStep = 1.0,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-bedcover",
                name = "Bed Cover",
                unit = "pcs",
                rate = 40000L,
                expressRate = 55000L,
                minWeight = 1.0,
                weightStep = 1.0,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-jas",
                name = "Jas / Blazer",
                unit = "pcs",
                rate = 35000L,
                expressRate = 50000L,
                minWeight = 1.0,
                weightStep = 1.0,
                active = true
            ),
            LaundryServiceEntity(
                id = "svc-sepatu",
                name = "Sepatu",
                unit = "pcs",
                rate = 30000L,
                expressRate = 45000L,
                minWeight = 1.0,
                weightStep = 1.0,
                active = true
            )
        )
    }

    override fun observeServices(): Flow<List<LaundryServiceEntity>> {
        return masterDataDao.observeActiveLaundryServices()
    }

    override suspend fun seedInitialServicesIfEmpty() {
        if (masterDataDao.countLaundryServices() == 0) {
            masterDataDao.upsertLaundryServices(INITIAL_LAUNDRY_SERVICES)
        }
    }

    override fun observePaymentMethods(): Flow<List<Pair<String, String>>> =
        transactionManager?.paymentDao?.observeAllPayments()?.map { list -> list.map { it.orderId to it.method } }
            ?: flowOf(emptyList())

    override fun observeOrders(): Flow<List<OrderEntity>> {
        return orderDao.observeOrdersByStatus("open")
    }

    override fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>> {
        return orderDao.observeOrdersByStatus(status)
    }

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        return orderDao.getOrderWithLines(orderId)
    }

    override suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? {
        return orderDao.getOrderByDisplayNumber(displayNumber)
    }

    override suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? {
        return orderDao.getOrderWithLinesByDisplayNumber(displayNumber)
    }

    override suspend fun findOrderByNumberFlexible(query: String): OrderWithLines? {
        val clean = query.trim()
        if (clean.isEmpty()) return null
        return orderDao.findLaundryOrderFlexible(clean)
    }

    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> {
        val existing = orderDao.getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("Order tidak ditemukan"))
        orderDao.updateOrder(existing.copy(status = newStatus))
        return Result.success(Unit)
    }

    override suspend fun advanceOrderStatus(
        orderId: String,
        targetStatus: String,
        rework: Boolean,
        reworkReason: String?,
        reworkApprovedBy: String?,
        userId: String?
    ): Result<OutboxEntity> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        return try {
            val outbox = tm.advanceLaundryOrderStatus(
                orderId = orderId,
                targetStatus = targetStatus,
                rework = rework,
                reworkReason = reworkReason,
                reworkApprovedBy = reworkApprovedBy,
                userId = userId
            )
            Result.success(outbox)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        val outbox = OutboxEntity(
            bundleId = UUID.randomUUID().toString(),
            deviceId = order.deviceId,
            shiftId = order.shiftId,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        return try {
            tm.createOrderWithOutbox(
                order = order,
                lines = lines,
                outbox = outbox
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        val clean = query.trim()
        val localFlow = customerDao.searchCustomers(clean)
        val api = syncApiService
        if (clean.length < 2 || api == null) {
            return localFlow
        }

        return localFlow.onStart {
            try {
                val response = api.searchCustomers(clean)
                if (response.isSuccessful && response.body() != null) {
                    val remoteList = response.body()!!.customers
                    for (remote in remoteList) {
                        val normPhone = PhoneNumberUtil.normalize(remote.phone)
                        val customerId = remote.idString.ifBlank { UUID.randomUUID().toString() }
                        val entity = CustomerEntity(
                            id = customerId,
                            name = remote.name,
                            phone = normPhone
                        )
                        customerDao.insertCustomer(entity)
                    }
                }
            } catch (_: Exception) {
                // Offline fallback: abaikan galat jaringan, tetap memancarkan data lokal dari Room
            }
        }
    }

    override fun observeRecentCustomers(limit: Int): Flow<List<CustomerEntity>> {
        return customerDao.observeRecentCustomers(limit)
    }

    override suspend fun getCustomerById(id: String): CustomerEntity? {
        return customerDao.getCustomerById(id)
    }

    override suspend fun saveCustomerLocally(customer: CustomerEntity): Result<Unit> {
        return try {
            customerDao.insertCustomer(customer)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createLaundryOrder(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        customer: CustomerEntity?,
        isNewCustomer: Boolean,
        deviceId: String,
        shiftId: String?
    ): Result<OutboxEntity> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        return try {
            val outbox = tm.createLaundryOrderWithCustomer(
                order = order,
                lines = lines,
                customer = customer,
                isNewCustomer = isNewCustomer,
                deviceId = deviceId,
                shiftId = shiftId
            )
            Result.success(outbox)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeLaundryOrders(statusFilter: String?): Flow<List<OrderWithLines>> {
        val tm = transactionManager ?: return flowOf(emptyList())
        return tm.observeLaundryOrders(statusFilter)
    }

    override fun observePickups(orderId: String): Flow<List<LaundryPickupEntity>> {
        val tm = transactionManager ?: return flowOf(emptyList())
        return tm.observePickupsForOrder(orderId)
    }

    override suspend fun recordPickups(
        orderId: String,
        items: List<LaundryPickupItem>,
        userId: String?,
        approvedBy: String?,
        deviceId: String
    ): Result<OutboxEntity> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        return try {
            val outbox = tm.recordLaundryPickups(
                orderId = orderId,
                items = items,
                userId = userId,
                approvedBy = approvedBy,
                deviceId = deviceId
            )
            Result.success(outbox)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reworkOrder(
        orderId: String,
        reason: String,
        approvedBy: String,
        deviceId: String
    ): Result<OutboxEntity> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        return try {
            val outbox = tm.reworkLaundryOrder(
                orderId = orderId,
                reason = reason,
                approvedBy = approvedBy,
                deviceId = deviceId
            )
            Result.success(outbox)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordCorrection(
        orderId: String,
        type: String,
        amount: Long,
        reasonCode: String,
        approvedBy: String,
        note: String?
    ): Result<Unit> {
        val tm = transactionManager ?: return Result.failure(IllegalStateException("OrderTransactionManager required"))
        return try {
            tm.recordCorrection(
                orderId = orderId,
                type = type,
                amount = amount,
                reasonCode = reasonCode,
                approvedBy = approvedBy,
                note = note
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNextDisplayNumber(prefix: String): String {
        val totalCount = orderDao.countTotalOrdersForPrefix(prefix)
        return "%s%04d".format(prefix, totalCount + 1)
    }

    override fun observeTodayOrdersCount(): Flow<Int> {
        val zone = java.time.ZoneId.systemDefault()
        val localDate = java.time.LocalDate.now(zone)
        val startOfDay = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return orderDao.observeTodayOrderCount("laundry", startOfDay, endOfDay)
    }

    override fun observeReadyOrdersCount(): Flow<Int> {
        return orderDao.observeLaundryCountByStatus("ready")
    }

    override fun observeUnpaidOrdersCount(): Flow<Int> {
        return orderDao.observeLaundryUnpaidCount()
    }

    override fun observeReadyOrders(limit: Int): Flow<List<OrderWithLines>> {
        return orderDao.observeReadyLaundryOrders(limit)
    }

    override suspend fun getNotificationStatus(orderId: String): Result<OrderNotificationStatusResponse> {
        val api = syncApiService ?: return Result.failure(IllegalStateException("SyncApiService tidak terkonfigurasi"))
        return try {
            val response = api.getOrderNotificationStatus(orderId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun repairMissingOrderLines(orderId: String): List<OrderLineEntity> {
        val tm = transactionManager ?: return emptyList()
        return tm.repairMissingOrderLines(orderId)
    }
}
