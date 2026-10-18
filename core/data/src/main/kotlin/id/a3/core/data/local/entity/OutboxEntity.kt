package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey
    val bundleId: String,
    val deviceId: String,
    val shiftId: String?,
    val eventsJson: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val reasonCode: String? = null,
    val flagsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
