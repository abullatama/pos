package id.a3.core.domain.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verifikasi PIN offline sesuai Spesifikasi API 3.3.D:
 * 1. kdf = PBKDF2-HMAC-SHA256(pin, salt = base64(pin_kdf_salt), iterasi, 32 byte)
 * 2. kunci = hex huruf kecil SHA-256(kredensial perangkat), dipakai sebagai teks ASCII
 * 3. verifier = Base64(HMAC-SHA256(kunci, kdf))
 * 4. bandingkan dalam waktu konstan dengan pin_verifier dari server.
 */
object Pbkdf2PinVerifier {
    private const val KEY_BITS = 32 * 8

    fun verify(
        pin: String,
        pinKdfSaltBase64: String,
        iterations: Int,
        deviceCredential: String,
        expectedVerifierBase64: String
    ): Boolean {
        if (pin.isEmpty() || iterations <= 0) return false
        return try {
            val salt = Base64.getDecoder().decode(pinKdfSaltBase64)
            val kdfBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS))
                .encoded

            val credentialSha = MessageDigest.getInstance("SHA-256")
                .digest(deviceCredential.toByteArray(StandardCharsets.UTF_8))
            val hmacKey = credentialSha.joinToString("") { "%02x".format(it) }

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKey.toByteArray(StandardCharsets.US_ASCII), "HmacSHA256"))
            val calculated = Base64.getEncoder().encodeToString(mac.doFinal(kdfBytes))

            MessageDigest.isEqual(
                expectedVerifierBase64.toByteArray(StandardCharsets.UTF_8),
                calculated.toByteArray(StandardCharsets.UTF_8)
            )
        } catch (_: Exception) {
            false
        }
    }
}
