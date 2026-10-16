package id.a3.feature.laundry.repository

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.repository.OrderTransactionManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface LaundryOrderRepository {
    fun observeServices(): Flow<List<LaundryServiceEntity>>
    fun observeOrders(): Flow<List<OrderEntity>>
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?
    suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit>
    suspend fun saveOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit>
}

class DefaultLaundryOrderRepository(
    private val masterDataDao: MasterDataDao,
    private val orderDao: OrderDao,
    private val transactionManager: OrderTransactionManager
) : LaundryOrderRepository {

    override fun observeServices(): Flow<List<LaundryServiceEntity>> {
        return masterDataDao.observeActiveLaundryServices()
    }

    override fun observeOrders(): Flow<List<OrderEntity>> {
        return orderDao.observeOrdersByStatus("open")
    }

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        return orderDao.getOrderWithLines(orderId)
    }

    override suspend fun updateOrderStatus(orderId: String, newStatus: String): Result<Unit> {
        val existing = orderDao.getOrderById(orderId)
            ?: return Result.failure(IllegalArgumentException("Order tidak ditemukan"))
        orderDao.updateOrder(existing.copy(status = newStatus))
        return Result.success(Unit)
    }

    override suspend fun saveOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit> {
        val outbox = OutboxEntity(
            bundleId = UUID.randomUUID().toString(),
            deviceId = order.deviceId,
            shiftId = order.shiftId,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        return try {
            transactionManager.createOrderWithOutbox(
                order = order,
                lines = lines,
                outbox = outbox
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
