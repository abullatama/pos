package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: String,
    val displayNumber: String,
    val businessUnitId: String,
    val shiftId: String?,
    val deviceId: String,
    val cashierId: String,
    val orderType: String,
    val tableLabel: String? = null,
    val customerId: String? = null,
    val status: String = "open",
    val subtotal: Long,
    val discount: Long = 0L,
    val serviceCharge: Long = 0L,
    val tax: Long = 0L,
    val rounding: Long = 0L,
    val total: Long,
    val paidAmount: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val receivedAt: Long? = null
)
