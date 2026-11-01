package id.a3.core.auth

import id.a3.core.auth.crypto.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PinHasherTest {

    @Test
    fun `legacy hashPin and verify match successfully`() {
        val hash = PinHasher.hashPin("1234")
        assertTrue(PinHasher.verify("1234", hash))
        assertFalse(PinHasher.verify("0000", hash))
    }

    @Test
    fun `verifyOfflinePbkdf2 accurately validates offline cashier PIN per API 3_3_D`() {
        val pin = "1234"
        val rawSalt = "random_salt_for_cashier_3".toByteArray(StandardCharsets.UTF_8)
        val saltBase64 = Base64.getEncoder().encodeToString(rawSalt)
        val iterations = 5000 // Test with 5000 for fast unit test
        val deviceCredential = "sec_dev_98f4e2cafe123456"

        // Compute expected verifier
        val spec = PBEKeySpec(pin.toCharArray(), rawSalt, iterations, 32 * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val kdfBytes = skf.generateSecret(spec).encoded

        val sha256 = MessageDigest.getInstance("SHA-256")
        val devCredShaBytes = sha256.digest(deviceCredential.toByteArray(StandardCharsets.UTF_8))
        val hmacKeyAscii = devCredShaBytes.joinToString("") { "%02x".format(it) }

        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(hmacKeyAscii.toByteArray(StandardCharsets.US_ASCII), "HmacSHA256")
        mac.init(keySpec)
        val expectedVerifierBase64 = Base64.getEncoder().encodeToString(mac.doFinal(kdfBytes))

        // Assert correct PIN matches
        assertTrue(
            "PIN 1234 must verify successfully",
            PinHasher.verifyOfflinePbkdf2(
                pin = "1234",
                pinKdfSaltBase64 = saltBase64,
                iterations = iterations,
                deviceCredential = deviceCredential,
                expectedVerifierBase64 = expectedVerifierBase64
            )
        )

        // Assert wrong PIN fails
        assertFalse(
            "Wrong PIN 4321 must fail verification",
            PinHasher.verifyOfflinePbkdf2(
                pin = "4321",
                pinKdfSaltBase64 = saltBase64,
                iterations = iterations,
                deviceCredential = deviceCredential,
                expectedVerifierBase64 = expectedVerifierBase64
            )
        )

        // Assert wrong device credential fails (per-device binding protection)
        assertFalse(
            "Wrong device credential must fail verification",
            PinHasher.verifyOfflinePbkdf2(
                pin = "1234",
                pinKdfSaltBase64 = saltBase64,
                iterations = iterations,
                deviceCredential = "different_device_credential",
                expectedVerifierBase64 = expectedVerifierBase64
            )
        )
    }
}
