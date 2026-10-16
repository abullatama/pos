package id.a3.feature.cafe.repository

import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.repository.OrderTransactionManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CafeOrderRepository {
    fun observeOpenOrders(): Flow<List<OrderEntity>>
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?
    suspend fun saveOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit>
}

class DefaultCafeOrderRepository(
    private val orderDao: OrderDao,
    private val transactionManager: OrderTransactionManager
) : CafeOrderRepository {

    override fun observeOpenOrders(): Flow<List<OrderEntity>> {
        return orderDao.observeOrdersByStatus("DRAFT")
    }

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        return orderDao.getOrderWithLines(orderId)
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
