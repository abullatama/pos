package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val price: Long,
    val active: Boolean = true,
    val soldOut: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
