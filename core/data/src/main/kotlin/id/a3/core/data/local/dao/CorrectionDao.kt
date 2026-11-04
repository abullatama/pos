package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.CorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrection(correction: CorrectionEntity)

    @Query("SELECT * FROM corrections WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    suspend fun getCorrectionsForShift(shiftId: String): List<CorrectionEntity>

    @Query("SELECT * FROM corrections WHERE shiftId = :shiftId ORDER BY createdAt ASC")
    fun observeCorrectionsForShift(shiftId: String): Flow<List<CorrectionEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM corrections WHERE shiftId = :shiftId AND type = 'refund'")
    suspend fun getCashRefundTotalForShift(shiftId: String): Long

    @Query("SELECT * FROM corrections WHERE orderId = :orderId ORDER BY createdAt ASC")
    suspend fun getCorrectionsForOrder(orderId: String): List<CorrectionEntity>

    /** Jumlah pengurangan tagihan oleh diskon manual atau komplimen yang sudah dikoreksikan ke server. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM corrections WHERE orderId = :orderId AND type IN ('manual_discount', 'comp')")
    suspend fun getManualAdjustmentTotal(orderId: String): Long

    @Query("SELECT * FROM corrections WHERE orderId = :orderId ORDER BY createdAt ASC")
    fun observeCorrectionsForOrder(orderId: String): Flow<List<CorrectionEntity>>

    @Query("SELECT * FROM corrections ORDER BY createdAt DESC")
    suspend fun getAllCorrections(): List<CorrectionEntity>

    @Query("SELECT * FROM corrections ORDER BY createdAt DESC")
    fun observeAllCorrections(): Flow<List<CorrectionEntity>>
}
