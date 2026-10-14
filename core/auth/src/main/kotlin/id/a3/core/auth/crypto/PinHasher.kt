package id.a3.core.auth.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object PinHasher {
    private const val SALT = "A3_POS_SALT_V1_"

    /**
     * Menghasilkan hash SHA-256 dari PIN kasir dengan salt tetap.
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((SALT + pin).toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Memverifikasi apakah [pin] yang diinput cocok dengan [expectedHash].
     */
    fun verify(pin: String, expectedHash: String): Boolean {
        return hashPin(pin) == expectedHash
    }
}
