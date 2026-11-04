package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    suspend fun getPaymentsForShift(shiftId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    fun observePaymentsForShift(shiftId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY createdAt ASC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE shiftId = :shiftId AND method = 'qris_static' ORDER BY createdAt ASC")
    fun observeQrisPaymentsForShift(shiftId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE shiftId = :shiftId AND method = 'qris_static' ORDER BY createdAt ASC")
    suspend fun getQrisPaymentsForShift(shiftId: String): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE shiftId = :shiftId AND method = 'cash'")
    suspend fun getCashTotalForShift(shiftId: String): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE shiftId = :shiftId AND method = 'cash' AND isDp = 0")
    suspend fun getCashSalesForShift(shiftId: String): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE shiftId = :shiftId AND method = 'cash' AND isDp = 1")
    suspend fun getCashDpForShift(shiftId: String): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE shiftId = :shiftId AND method = 'qris_static'")
    suspend fun getQrisTotalForShift(shiftId: String): Long

    @Query("SELECT COUNT(*) FROM payments WHERE shiftId = :shiftId")
    suspend fun getTransactionCountForShift(shiftId: String): Int
}
