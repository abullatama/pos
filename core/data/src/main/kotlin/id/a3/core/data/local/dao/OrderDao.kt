package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId")
    suspend fun getLinesForOrder(orderId: String): List<OrderLineEntity>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderWithLines(orderId: String): OrderWithLines?

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrder(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderById(orderId: String): OrderEntity?

    /** Pesanan yang menunjuk pelanggan yang tidak ada lagi di tabel pelanggan (lihat OrderCustomerRepair). */
    @Query("SELECT * FROM orders WHERE customerId IS NOT NULL AND customerId NOT IN (SELECT id FROM customers)")
    suspend fun getOrdersWithMissingCustomer(): List<OrderEntity>

    /** Hanya menyambung ulang penunjuk pelanggan; tidak menyentuh nilai uang atau status. */
    @Query("UPDATE orders SET customerId = :customerId WHERE id = :orderId")
    suspend fun updateOrderCustomerId(orderId: String, customerId: String)

    @Query("SELECT * FROM orders WHERE displayNumber = :displayNumber ORDER BY createdAt DESC LIMIT 1")
    suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity?

    @Transaction
    @Query("SELECT * FROM orders WHERE displayNumber = :displayNumber ORDER BY createdAt DESC LIMIT 1")
    suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines?

    @Transaction
    @Query("SELECT * FROM orders WHERE (displayNumber = :query OR displayNumber LIKE '%' || :query) ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLaundryOrderFlexible(query: String): OrderWithLines?

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY createdAt DESC")
    fun observeOrdersByStatus(status: String): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status = 'open' ORDER BY createdAt DESC")
    fun observeOpenOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status IN ('paid', 'refunded', 'void', 'partial_refund') ORDER BY createdAt DESC")
    fun observeCompletedOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE status IN ('paid', 'refunded', 'void', 'partial_refund') AND (shiftId = :shiftId OR id IN (SELECT DISTINCT orderId FROM payments WHERE shiftId = :shiftId)) ORDER BY createdAt DESC")
    fun observeCompletedOrdersWithLinesForShift(shiftId: String): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' ORDER BY createdAt DESC")
    fun observeLaundryOrdersWithLines(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' AND status = :status ORDER BY createdAt DESC")
    fun observeLaundryOrdersWithLinesByStatus(status: String): Flow<List<OrderWithLines>>

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET displayNumber = :newDisplayNumber WHERE id = :orderId")
    suspend fun updateDisplayNumber(orderId: String, newDisplayNumber: String)

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

    @Query("UPDATE orders SET status = :status, paidAmount = :paidAmount, shiftId = COALESCE(:shiftId, shiftId) WHERE id = :orderId")
    suspend fun updatePaymentStatus(orderId: String, status: String, paidAmount: Long, shiftId: String? = null)

    @Query("SELECT COUNT(*) FROM orders WHERE displayNumber LIKE :prefix || '%' AND createdAt >= :startOfDayMs AND createdAt < :endOfDayMs")
    suspend fun countOrdersForPrefixAndDate(prefix: String, startOfDayMs: Long, endOfDayMs: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE displayNumber LIKE :prefix || '%'")
    suspend fun countTotalOrdersForPrefix(prefix: String): Int

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = :orderType AND createdAt >= :startOfDayMs AND createdAt < :endOfDayMs")
    fun observeTodayOrderCount(orderType: String, startOfDayMs: Long, endOfDayMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = 'laundry' AND status = :status")
    fun observeLaundryCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE orderType = 'laundry' AND status != 'picked_up' AND paidAmount < total")
    fun observeLaundryUnpaidCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(discount), 0) FROM orders WHERE id IN (SELECT DISTINCT orderId FROM payments WHERE shiftId = :shiftId)")
    suspend fun getDiscountTotalForShift(shiftId: String): Long

    @Query("SELECT COALESCE(SUM(subtotal), 0) FROM orders WHERE id IN (SELECT DISTINCT orderId FROM payments WHERE shiftId = :shiftId)")
    suspend fun getGrossSubtotalForShift(shiftId: String): Long

    @Transaction
    @Query("SELECT * FROM orders WHERE orderType = 'laundry' AND status = 'ready' ORDER BY createdAt DESC LIMIT :limit")
    fun observeReadyLaundryOrders(limit: Int = 10): Flow<List<OrderWithLines>>
}
