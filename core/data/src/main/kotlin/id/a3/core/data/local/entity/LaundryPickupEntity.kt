package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "laundry_pickups",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["orderLineId"])
    ]
)
data class LaundryPickupEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val orderLineId: String,
    val quantity: Int,
    val isFinal: Boolean,
    val note: String? = null,
    val userId: String? = null,
    val approvedBy: String? = null,
    val pickedUpAt: Long = System.currentTimeMillis()
)
