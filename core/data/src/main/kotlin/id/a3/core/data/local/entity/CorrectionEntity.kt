package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas catatan koreksi append-only di Room (COR-01 s/d COR-08, F35, F36).
 *
 * Sesuai aturan:
 * - Data transaksi asal tidak pernah dihapus atau diedit.
 * - Mencatat pembalik (refund, void, diskon manual) yang menunjuk ke order_id asal.
 * - Refund selalu tunai (refundMethod = "cash", COR-04), mengurangi kas laci shift berjalan.
 * - ShiftId merekam shift aktif saat koreksi dicatat.
 * - isOldTransactionRefund menandai refund atas transaksi shift sebelumnya (F36).
 */
@Entity(
    tableName = "corrections",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["shiftId"])
    ]
)
data class CorrectionEntity(
    @PrimaryKey
    val id: String,
    val orderId: String,
    val shiftId: String? = null,
    val type: String, // "void", "refund", "manual_discount", "comp", "weight_adjust", "price_adjust"
    val amount: Long,
    val refundMethod: String? = "cash",
    val originalPaymentId: String? = null,
    val orderLineIdsJson: String = "[]",
    val reasonCode: String, // "wrong_input", "customer_cancelled", "quality_complaint", "wrong_made", "promo", "other"
    val note: String? = null,
    val approvedBy: String,
    val isOldTransactionRefund: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
