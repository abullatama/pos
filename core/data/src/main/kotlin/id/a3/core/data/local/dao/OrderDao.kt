package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<OrderLineEntity>)

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrder(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE displayNumber = :displayNumber LIMIT 1")
    suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity?

    @Transaction
    @Query("SELECT * FROM orders WHERE displayNumber = :displayNumber LIMIT 1")
    suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines?

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY createdAt DESC")
    fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = 'open' ORDER BY createdAt DESC")
    fun observeOpenOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = 'paid' ORDER BY createdAt DESC")
    fun observeCompletedOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' ORDER BY createdAt DESC")
    fun observeLaundryOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' AND status = :status ORDER BY createdAt DESC")
    fun observeLaundryOrdersWithLinesByStatus(status: String): Flow<List<OrderWithLines>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET tableLabel = :newTableLabel WHERE id = :orderId")
    suspend fun updateTableLabel(orderId: String, newTableLabel: String)

    @Query("UPDATE orders SET subtotal = :subtotal, discount = :discount, serviceCharge = :serviceCharge, tax = :tax, rounding = :rounding, total = :total WHERE id = :orderId")
    suspend fun updateOrderTotals(
        orderId: String,
        subtotal: Long,
        discount: Long,
        serviceCharge: Long,
        tax: Long,
        rounding: Long,
        total: Long
    )

    @Query("UPDATE orders SET status = :status, paidAmount = :paidAmount WHERE id = :orderId")
    suspend fun updatePaymentStatus(orderId: String, status: String, paidAmount: Long)

    @Query("SELECT COUNT(*) FROM orders WHERE displayNumber LIKE :prefix || '%' AND createdAt >= :startOfDayMs AND createdAt < :endOfDayMs")
    suspend fun countOrdersForPrefixAndDate(prefix: String, startOfDayMs: Long, endOfDayMs: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = :orderType AND createdAt >= :startOfDayMs AND createdAt < :endOfDayMs")
    fun observeTodayOrderCount(orderType: String, startOfDayMs: Long, endOfDayMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = 'laundry' AND status = :status")
    fun observeLaundryCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = 'laundry' AND status != 'picked_up' AND paidAmount < total")
    fun observeLaundryUnpaidCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' AND status = 'ready' ORDER BY createdAt DESC LIMIT :limit")
    fun observeReadyLaundryOrders(limit: Int = 10): Flow<List<OrderWithLines>>
}
