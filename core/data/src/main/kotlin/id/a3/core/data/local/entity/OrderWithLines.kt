package id.a3.core.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class OrderWithLines(
    @Embedded
    val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val lines: List<OrderLineEntity>
)
