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

    /**
     * Memverifikasi PIN kasir offline sesuai Algoritma Spesifikasi API 3.3.D:
     * 1. kdf_bytes = PBKDF2-HMAC-SHA256(pin, salt=base64_decode(pin_kdf_salt), iterations, key_length=32 bytes)
     * 2. hmac_key = LowercaseHex(SHA256(device_credential)) (ASCII 64 karakter)
     * 3. calculated_verifier = Base64(HMAC-SHA256(key=hmac_key, data=kdf_bytes))
     * 4. constant-time compare(calculated_verifier, expectedVerifier)
     */
    fun verifyOfflinePbkdf2(
        pin: String,
        pinKdfSaltBase64: String,
        iterations: Int,
        deviceCredential: String,
        expectedVerifierBase64: String
    ): Boolean {
        return id.a3.core.domain.security.Pbkdf2PinVerifier.verify(
            pin = pin,
            pinKdfSaltBase64 = pinKdfSaltBase64,
            iterations = iterations,
            deviceCredential = deviceCredential,
            expectedVerifierBase64 = expectedVerifierBase64
        )
    }
}
