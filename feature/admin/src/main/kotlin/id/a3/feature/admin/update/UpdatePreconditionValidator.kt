package id.a3.feature.admin.update

import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.ShiftDao

/**
 * Hasil evaluasi kelayakan pembaruan aplikasi (OPS-03).
 */
sealed interface UpdateEligibility {
    data object Allowed : UpdateEligibility
    data class Blocked(val reasons: List<UpdateBlockReason>) : UpdateEligibility {
        val userFriendlyMessage: String
            get() = reasons.joinToString(separator = "\n") { it.description }
    }
}

/**
 * Alasan pemblokiran update aplikasi sesuai aturan OPS-03.
 */
sealed interface UpdateBlockReason {
    val description: String

    data class ActiveShiftRunning(
        val shiftId: String,
        val cashierName: String
    ) : UpdateBlockReason {
        override val description: String
            get() = "Shift aktif kasir '$cashierName' masih berjalan. Tutup shift terlebih dahulu sebelum memperbarui aplikasi."
    }

    data class UnsyncedOutboxPending(
        val pendingCount: Int
    ) : UpdateBlockReason {
        override val description: String
            get() = "Terdapat $pendingCount transaksi/event di outbox yang belum tersinkron ke server. Pastikan seluruh data tersinkron terlebih dahulu."
    }
}

/**
 * Penegak aturan OPS-03:
 * "Update hanya saat tidak ada shift aktif dan outbox kosong, di luar jam ramai, dan dirilis bertahap."
 */
class UpdatePreconditionValidator(
    private val shiftDao: ShiftDao,
    private val outboxDao: OutboxDao
) {
    /**
     * Memeriksa apakah tablet dalam kondisi aman untuk menerima dan memasang pembaruan APK.
     */
    suspend fun checkUpdateEligibility(): UpdateEligibility {
        val reasons = mutableListOf<UpdateBlockReason>()

        // 1. Syarat pertama OPS-03: Tidak boleh ada shift aktif
        val activeShift = shiftDao.getActiveShift()
        if (activeShift != null) {
            reasons.add(
                UpdateBlockReason.ActiveShiftRunning(
                    shiftId = activeShift.id,
                    cashierName = activeShift.cashierName
                )
            )
        }

        // 2. Syarat kedua OPS-03: Outbox harus bersih/kosong (tidak ada yang belum tersinkron ke server)
        val unsyncedCount = outboxDao.getUnsyncedCount()
        if (unsyncedCount > 0) {
            reasons.add(
                UpdateBlockReason.UnsyncedOutboxPending(
                    pendingCount = unsyncedCount
                )
            )
        }

        return if (reasons.isEmpty()) {
            UpdateEligibility.Allowed
        } else {
            UpdateEligibility.Blocked(reasons)
        }
    }
}
