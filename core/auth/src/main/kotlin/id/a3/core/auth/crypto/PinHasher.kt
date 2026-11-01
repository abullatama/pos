package id.a3.core.auth.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
        return try {
            val salt = Base64.getDecoder().decode(pinKdfSaltBase64)
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 32 * 8)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val kdfBytes = skf.generateSecret(spec).encoded

            val sha256 = MessageDigest.getInstance("SHA-256")
            val devCredShaBytes = sha256.digest(deviceCredential.toByteArray(StandardCharsets.UTF_8))
            val hmacKeyAscii = devCredShaBytes.joinToString("") { "%02x".format(it) }

            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(hmacKeyAscii.toByteArray(StandardCharsets.US_ASCII), "HmacSHA256")
            mac.init(keySpec)
            val calculatedHmac = mac.doFinal(kdfBytes)
            val calculatedVerifier = Base64.getEncoder().encodeToString(calculatedHmac)

            val expectedBytes = expectedVerifierBase64.toByteArray(StandardCharsets.UTF_8)
            val calculatedBytes = calculatedVerifier.toByteArray(StandardCharsets.UTF_8)
            MessageDigest.isEqual(expectedBytes, calculatedBytes)
        } catch (_: Exception) {
            false
        }
    }
}
