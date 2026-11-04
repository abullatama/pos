package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.DiscountPresetEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.MenuOptionEntity
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

    @Query("SELECT * FROM menu_items")
    suspend fun getAllMenuItems(): List<MenuItemEntity>

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun countMenuItems(): Int

    @Query("UPDATE menu_items SET soldOut = :soldOut, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMenuItemSoldOut(id: String, soldOut: Boolean, updatedAt: Long = System.currentTimeMillis())

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

    @Query("SELECT * FROM laundry_services")
    suspend fun getAllLaundryServices(): List<LaundryServiceEntity>

    @Query("SELECT COUNT(*) FROM laundry_services")
    suspend fun countLaundryServices(): Int

    @Query("DELETE FROM laundry_services")
    suspend fun clearLaundryServices()

    // Menu options (Cafe: varian dan add-on)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenuOptions(options: List<MenuOptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMenuOption(option: MenuOptionEntity)

    @Query("DELETE FROM menu_options WHERE id = :id")
    suspend fun deleteMenuOption(id: String)

    @Query("DELETE FROM menu_options WHERE menuItemId = :menuItemId")
    suspend fun deleteMenuOptionsForMenuItem(menuItemId: String)

    @Query("DELETE FROM menu_options")
    suspend fun clearMenuOptions()

    @Query("SELECT * FROM menu_options WHERE active = 1 ORDER BY name ASC")
    fun observeAllActiveMenuOptions(): Flow<List<MenuOptionEntity>>

    @Query("SELECT * FROM menu_options WHERE menuItemId = :menuItemId AND active = 1 ORDER BY name ASC")
    fun observeActiveOptionsForMenuItem(menuItemId: String): Flow<List<MenuOptionEntity>>

    @Query("SELECT * FROM menu_options WHERE menuItemId = :menuItemId AND active = 1 ORDER BY name ASC")
    suspend fun getActiveOptionsForMenuItem(menuItemId: String): List<MenuOptionEntity>

    // Discount Presets (Cafe & Laundry)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscountPresets(presets: List<DiscountPresetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscountPreset(preset: DiscountPresetEntity)

    @Query("DELETE FROM discount_presets WHERE id = :id")
    suspend fun deleteDiscountPreset(id: String)

    @Query("SELECT * FROM discount_presets WHERE active = 1 ORDER BY value ASC")
    fun observeActiveDiscountPresets(): Flow<List<DiscountPresetEntity>>

    @Query("SELECT * FROM discount_presets WHERE active = 1 ORDER BY value ASC")
    suspend fun getActiveDiscountPresets(): List<DiscountPresetEntity>

    @Query("DELETE FROM discount_presets")
    suspend fun clearDiscountPresets()
}
