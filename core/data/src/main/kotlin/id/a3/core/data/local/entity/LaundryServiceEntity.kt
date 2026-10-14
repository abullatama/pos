package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "laundry_services")
data class LaundryServiceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val unit: String, // "kg" or "pcs"
    val rate: Long,
    val expressRate: Long = 0L,
    val minWeight: Double = 1.0,
    val weightStep: Double = 0.5,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
