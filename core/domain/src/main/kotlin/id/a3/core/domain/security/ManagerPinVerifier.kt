package id.a3.core.domain.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Verifikator terpusat untuk otorisasi manajerial (COR-01, COR-07, SHF-06).
 * Memverifikasi input PIN manager menggunakan hash SHA-256 tersimpan atau seed default.
 */
object ManagerPinVerifier {
    private const val SALT = "A3_POS_SALT_V1_"

    /**
     * Menghasilkan hash SHA-256 dari PIN kasir/manager dengan salt tetap.
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((SALT + pin).toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hash SHA-256 dari default seed PIN manager "4444"
     */
    val DEFAULT_MANAGER_PIN_HASH: String by lazy { hashPin("4444") }

    /**
     * Memverifikasi input PIN. Jika [storedHash] null atau blank, mencocokkan dengan [DEFAULT_MANAGER_PIN_HASH].
     */
    fun verify(inputPin: String, storedHash: String? = null): Boolean {
        if (inputPin.isBlank()) return false
        val targetHash = storedHash?.ifBlank { null } ?: DEFAULT_MANAGER_PIN_HASH
        return hashPin(inputPin) == targetHash
    }
}
