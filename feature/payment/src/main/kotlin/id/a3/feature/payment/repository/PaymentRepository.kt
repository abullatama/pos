package id.a3.feature.payment.repository

import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.repository.OrderTransactionManager
import java.util.UUID

enum class PaymentMethod {
    CASH,
    STATIC_QRIS
}

data class PaymentTransaction(
    val orderId: String,
    val method: PaymentMethod,
    val totalAmount: Long,
    val cashReceived: Long = 0L,
    val change: Long = 0L,
    val isDp: Boolean = false
)

interface PaymentRepository {
    suspend fun processPayment(
        payment: PaymentTransaction,
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit>
}

class DefaultPaymentRepository(
    private val orderDao: OrderDao,
    private val transactionManager: OrderTransactionManager
) : PaymentRepository {

    override suspend fun processPayment(
        payment: PaymentTransaction,
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        eventsJson: String
    ): Result<Unit> {
        val updatedOrder = order.copy(
            status = if (payment.isDp) "DP" else "PAID"
        )
        val outbox = OutboxEntity(
            bundleId = UUID.randomUUID().toString(),
            deviceId = order.deviceId,
            shiftId = order.shiftId,
            eventsJson = eventsJson,
            status = OutboxStatus.PENDING
        )
        return try {
            transactionManager.createOrderWithOutbox(
                order = updatedOrder,
                lines = lines,
                outbox = outbox
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
