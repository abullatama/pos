package id.a3.core.data.security

import id.a3.core.data.local.dao.UserDao
import id.a3.core.domain.security.ApprovalResult
import id.a3.core.domain.security.ManagerApproval
import id.a3.core.domain.security.ManagerApprover
import id.a3.core.domain.security.Pbkdf2PinVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Memeriksa PIN terhadap akun manager/owner yang tersinkron dari server (spek 3.3.D), tanpa PIN bawaan.
 * Salah [MAX_ATTEMPTS] kali berturut-turut mengunci semua persetujuan selama [LOCK_SECONDS] detik (COR-07).
 */
class DefaultManagerApprover(
    private val userDao: UserDao,
    private val deviceCredentialProvider: suspend () -> String?,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : ManagerApprover {

    private val mutex = Mutex()
    private var failedAttempts = 0
    private var lockedUntilMs = 0L

    override suspend fun verify(pin: String, roles: Set<String>): ApprovalResult = mutex.withLock {
        val now = nowMs()
        if (now < lockedUntilMs) {
            return@withLock ApprovalResult.Locked(retryAfterSeconds = secondsUntil(lockedUntilMs, now))
        }
        if (pin.isBlank()) return@withLock wrongPin(now)

        val credential = deviceCredentialProvider()?.takeIf { it.isNotBlank() }
            ?: return@withLock ApprovalResult.NoApproverAvailable
        val candidates = userDao.getActiveApprovers(roles.toList())
        if (candidates.isEmpty()) return@withLock ApprovalResult.NoApproverAvailable

        // PBKDF2 120.000 iterasi per akun: jangan di thread utama.
        val match = withContext(Dispatchers.Default) {
            candidates.firstOrNull { user ->
                Pbkdf2PinVerifier.verify(
                    pin = pin,
                    pinKdfSaltBase64 = user.pinKdfSalt.orEmpty(),
                    iterations = user.pinKdfIterations ?: 0,
                    deviceCredential = credential,
                    expectedVerifierBase64 = user.pinVerifier.orEmpty()
                )
            }
        }

        if (match != null) {
            failedAttempts = 0
            ApprovalResult.Approved(ManagerApproval(userId = match.id, name = match.name, role = match.role))
        } else {
            wrongPin(now)
        }
    }

    private fun wrongPin(now: Long): ApprovalResult {
        failedAttempts++
        return if (failedAttempts >= MAX_ATTEMPTS) {
            failedAttempts = 0
            lockedUntilMs = now + LOCK_SECONDS * 1000L
            ApprovalResult.Locked(retryAfterSeconds = LOCK_SECONDS)
        } else {
            ApprovalResult.WrongPin(attemptsLeft = MAX_ATTEMPTS - failedAttempts)
        }
    }

    private fun secondsUntil(untilMs: Long, now: Long): Long = ((untilMs - now) + 999L) / 1000L

    companion object {
        const val MAX_ATTEMPTS = 3
        const val LOCK_SECONDS = 300L
    }
}
