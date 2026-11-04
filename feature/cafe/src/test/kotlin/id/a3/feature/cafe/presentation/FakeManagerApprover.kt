package id.a3.feature.cafe.presentation

import id.a3.core.domain.security.ApprovalResult
import id.a3.core.domain.security.ManagerApproval
import id.a3.core.domain.security.ManagerApprover

/** Penyetuju tiruan khusus tes: menerima satu PIN dan mengunci setelah 3 salah. Tidak ada di kode produksi. */
class FakeManagerApprover(
    private val validPin: String = "4444",
    private val userId: Long = 2L
) : ManagerApprover {
    private var failed = 0

    override suspend fun verify(pin: String, roles: Set<String>): ApprovalResult {
        if (failed >= 3) return ApprovalResult.Locked(retryAfterSeconds = 300)
        if (pin == validPin) {
            failed = 0
            return ApprovalResult.Approved(ManagerApproval(userId, "Manager Uji", "manager"))
        }
        failed++
        return if (failed >= 3) ApprovalResult.Locked(300) else ApprovalResult.WrongPin(3 - failed)
    }
}
