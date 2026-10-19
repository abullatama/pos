package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entitas shift kasir lokal di Room (SHF-01 s/d SHF-06, F30, F31, F32).
 * Menyimpan data pembukaan shift, penghitungan kas awal per pecahan,
 * serta hasil penutupan shift dan rekonsiliasi kas.
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey
    val id: String,
    val businessUnitId: String,
    val deviceId: String,
    val cashierId: String,
    val cashierName: String,
    val status: String, // "open", "closed", "force_closed"
    val openingCash: Long,
    val openingCashBreakdownJson: String, // JSON pecahan ke lembar/keping, mis. {"50000":2,"20000":4,"10000":2}
    val differenceReason: String? = null,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val countedCash: Long? = null,
    val cashLeft: Long? = null,
    val cashDeposited: Long? = null,
    val cashDifferenceReason: String? = null,
    val approvedBy: String? = null
)
