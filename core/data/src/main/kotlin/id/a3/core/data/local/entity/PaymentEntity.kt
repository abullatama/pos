package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas pembayaran transaksi lokal di Room (PAY-01 s/d PAY-07, TUP-04, TUP-05).
 * Bersifat append-only; shiftId merekam shift yang menerima pembayaran kasir.
 */
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["shiftId"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val method: String, // "cash", "qris_static"
    val amount: Long,
    val tendered: Long? = null,
    val idempotencyKey: String,
    val shiftId: String? = null,
    val cashierId: String? = null,
    val isDp: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
