package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.a3.core.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id IN (:ids)")
    fun observeCustomersByIds(ids: List<String>): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun findCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentCustomers(limit: Int = 20): Flow<List<CustomerEntity>>

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: String)

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()
}
