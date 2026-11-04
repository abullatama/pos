package id.a3.core.domain.security

/** Manager atau owner yang benar-benar memasukkan PIN. [userId] dikirim sebagai approved_by ke server. */
data class ManagerApproval(
    val userId: Long,
    val name: String,
    val role: String
)

sealed interface ApprovalResult {
    data class Approved(val approval: ManagerApproval) : ApprovalResult

    /** PIN tidak cocok dengan manager mana pun. [attemptsLeft] sebelum terkunci. */
    data class WrongPin(val attemptsLeft: Int) : ApprovalResult

    /** Terkunci setelah salah berulang (COR-07). */
    data class Locked(val retryAfterSeconds: Long) : ApprovalResult

    /** Belum ada manager dengan PIN di tablet ini (data akun belum tersinkron). */
    data object NoApproverAvailable : ApprovalResult
}

/**
 * Persetujuan manager untuk aksi yang menuntut PIN (diskon manual, void, refund, kas keluar besar).
 * Tidak ada PIN bawaan: PIN diperiksa terhadap akun manager/owner dari server.
 */
interface ManagerApprover {
    suspend fun verify(pin: String, roles: Set<String> = DEFAULT_ROLES): ApprovalResult

    companion object {
        val DEFAULT_ROLES: Set<String> = setOf("manager", "owner")
        val OWNER_ONLY: Set<String> = setOf("owner")
    }
}
