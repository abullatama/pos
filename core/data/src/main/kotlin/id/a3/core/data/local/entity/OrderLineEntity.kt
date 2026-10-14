package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_lines",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["orderId"])]
)
data class OrderLineEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val itemRefId: String,
    val nameSnapshot: String,
    val quantity: Int,
    val weight: Double? = null,
    val unitPriceSnapshot: Long,
    val costSnapshot: Long = 0L,
    val note: String? = null
)
