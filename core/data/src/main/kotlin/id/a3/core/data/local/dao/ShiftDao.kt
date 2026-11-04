package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.a3.core.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE status = 'open' LIMIT 1")
    suspend fun getActiveShift(): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE status = 'open' LIMIT 1")
    fun observeActiveShift(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE id = :shiftId")
    suspend fun getShiftById(shiftId: String): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC LIMIT 1")
    suspend fun getLatestShift(): ShiftEntity?

    @Query("SELECT * FROM shifts ORDER BY openedAt DESC")
    fun observeAllShifts(): Flow<List<ShiftEntity>>

    @Query("DELETE FROM shifts WHERE id = :shiftId")
    suspend fun deleteShift(shiftId: String)

    @Query("DELETE FROM shifts WHERE status = 'open'")
    suspend fun deleteAllOpenShifts()
}
