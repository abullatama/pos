package id.a3.core.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vektor dihitung dengan rumus server (App\Services\Auth\PinCredentials::derive dan ::verifier):
 * PIN 1234, salt "0123456789abcdef", 120.000 iterasi, kredensial perangkat "kredensial-contoh".
 */
class Pbkdf2PinVerifierTest {
    private val saltBase64 = "MDEyMzQ1Njc4OWFiY2RlZg=="
    private val verifier = "7yqmVjYV/c2MJ1R8mOnJN22MR46wMe9m1baM3mX3uQw="
    private val credential = "kredensial-contoh"

    @Test
    fun `PIN benar cocok dengan verifier buatan server`() {
        assertTrue(Pbkdf2PinVerifier.verify("1234", saltBase64, 120_000, credential, verifier))
    }

    @Test
    fun `PIN salah ditolak`() {
        assertFalse(Pbkdf2PinVerifier.verify("1235", saltBase64, 120_000, credential, verifier))
    }

    @Test
    fun `kredensial perangkat lain ditolak`() {
        assertFalse(Pbkdf2PinVerifier.verify("1234", saltBase64, 120_000, "kredensial-lain", verifier))
    }

    @Test
    fun `jumlah iterasi berbeda ditolak`() {
        assertFalse(Pbkdf2PinVerifier.verify("1234", saltBase64, 1_000, credential, verifier))
    }

    @Test
    fun `masukan rusak ditolak tanpa melempar error`() {
        assertFalse(Pbkdf2PinVerifier.verify("", saltBase64, 120_000, credential, verifier))
        assertFalse(Pbkdf2PinVerifier.verify("1234", "bukan-base64!!", 120_000, credential, verifier))
        assertFalse(Pbkdf2PinVerifier.verify("1234", saltBase64, 0, credential, verifier))
    }
}
