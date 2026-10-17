package id.a3.feature.payment.repository

import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.repository.OrderTransactionManager

enum class PaymentMethod(val label: String, val apiKey: String) {
    CASH("Tunai", "cash"),
    STATIC_QRIS("QRIS Statis", "qris_static")
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
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?
    suspend fun processPayment(payment: PaymentTransaction): Result<Unit>
    suspend fun processTakeAwayDirectPayment(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        payment: PaymentTransaction
    ): Result<Unit>
}

class DefaultPaymentRepository(
    private val transactionManager: OrderTransactionManager
) : PaymentRepository {

    override suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        return transactionManager.getOrderWithLines(orderId)
    }

    override suspend fun processPayment(payment: PaymentTransaction): Result<Unit> {
        return try {
            transactionManager.recordPayment(
                orderId = payment.orderId,
                method = payment.method.apiKey,
                amount = payment.totalAmount,
                tendered = if (payment.method == PaymentMethod.CASH) payment.cashReceived else null,
                isDp = payment.isDp
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processTakeAwayDirectPayment(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        payment: PaymentTransaction
    ): Result<Unit> {
        return try {
            transactionManager.createAndPayOrder(
                order = order,
                lines = lines,
                method = payment.method.apiKey,
                amount = payment.totalAmount,
                tendered = if (payment.method == PaymentMethod.CASH) payment.cashReceived else null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
