package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discount_presets")
data class DiscountPresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String,
    val value: Long,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
