package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDataDao {

    // Menu items (Cafe)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenuItems(items: List<MenuItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenuItem(item: MenuItemEntity)

    @Query("DELETE FROM menu_items WHERE id = :id")
    suspend fun deleteMenuItem(id: String)

    @Query("SELECT * FROM menu_items WHERE active = 1 ORDER BY name ASC")
    fun observeActiveMenuItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getMenuItem(id: String): MenuItemEntity?

    @Query("DELETE FROM menu_items")
    suspend fun clearMenuItems()

    // Laundry services (Laundry)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLaundryServices(services: List<LaundryServiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLaundryService(service: LaundryServiceEntity)

    @Query("DELETE FROM laundry_services WHERE id = :id")
    suspend fun deleteLaundryService(id: String)

    @Query("SELECT * FROM laundry_services WHERE active = 1 ORDER BY name ASC")
    fun observeActiveLaundryServices(): Flow<List<LaundryServiceEntity>>

    @Query("SELECT * FROM laundry_services WHERE id = :id")
    suspend fun getLaundryService(id: String): LaundryServiceEntity?

    @Query("DELETE FROM laundry_services")
    suspend fun clearLaundryServices()
}
