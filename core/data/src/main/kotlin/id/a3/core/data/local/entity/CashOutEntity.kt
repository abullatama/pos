package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas kas keluar operasional kasir / petty cash laci di Room (KAS-01, KAS-02, F37).
 *
 * Sesuai aturan bisnis:
 * - Mengurangi kas seharusnya di shift berjalan (KAS-01, rumus K1).
 * - Di bawah atau sama dengan batas Rp 50.000 (OQ-08): kasir boleh mencatat tanpa PIN.
 * - Di atas Rp 50.000: wajib otorisasi PIN Manager dan foto nota fisik wajib (KAS-02).
 * - receiptFileId menunjuk ke berkas foto yang diunggah ke backend via POST /files.
 */
@Entity(
    tableName = "cash_outs",
    indices = [
        Index(value = ["shiftId"])
    ]
)
data class CashOutEntity(
    @PrimaryKey
    val id: String,
    val shiftId: String,
    val amount: Long,
    val category: String, // "Bahan baku", "Operasional", "Perawatan", "Kemasan", "Lain-lain"
    val reason: String,
    val receiptFileId: String? = null,
    val receiptFilePath: String? = null,
    val approvedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
