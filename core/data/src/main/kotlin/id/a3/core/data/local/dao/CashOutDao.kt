package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.CashOutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashOutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashOut(cashOut: CashOutEntity)

    @Query("SELECT * FROM cash_outs WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    suspend fun getCashOutsForShift(shiftId: String): List<CashOutEntity>

    @Query("SELECT * FROM cash_outs WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun observeCashOutsForShift(shiftId: String): Flow<List<CashOutEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_outs WHERE shiftId = :shiftId")
    suspend fun getCashOutTotalForShift(shiftId: String): Long

    @Query("SELECT COUNT(*) FROM cash_outs WHERE shiftId = :shiftId")
    suspend fun getCashOutCountForShift(shiftId: String): Int

    @Query("SELECT * FROM cash_outs ORDER BY createdAt DESC")
    suspend fun getAllCashOuts(): List<CashOutEntity>

    @Query("SELECT * FROM cash_outs ORDER BY createdAt DESC")
    fun observeAllCashOuts(): Flow<List<CashOutEntity>>

    @Query("SELECT * FROM cash_outs WHERE receiptFileId = :fileId LIMIT 1")
    suspend fun getCashOutByReceiptFileId(fileId: String): CashOutEntity?

    @Query("SELECT * FROM cash_outs WHERE id = :cashoutId LIMIT 1")
    suspend fun getCashOutById(cashoutId: String): CashOutEntity?
}
