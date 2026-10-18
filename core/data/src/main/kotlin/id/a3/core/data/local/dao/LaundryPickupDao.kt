package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.LaundryPickupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LaundryPickupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPickup(pickup: LaundryPickupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPickups(pickups: List<LaundryPickupEntity>)

    @Query("SELECT * FROM laundry_pickups WHERE orderId = :orderId ORDER BY pickedUpAt ASC")
    suspend fun getPickupsByOrderId(orderId: String): List<LaundryPickupEntity>

    @Query("SELECT * FROM laundry_pickups WHERE orderId = :orderId ORDER BY pickedUpAt ASC")
    fun observePickupsByOrderId(orderId: String): Flow<List<LaundryPickupEntity>>

    @Query("SELECT COUNT(*) FROM laundry_pickups WHERE orderId = :orderId AND isFinal = 1")
    suspend fun getFinalPickupsCount(orderId: String): Int

    @Query("DELETE FROM laundry_pickups WHERE orderId = :orderId")
    suspend fun deletePickupsForOrder(orderId: String)

    @Query("DELETE FROM laundry_pickups")
    suspend fun clearPickups()
}
