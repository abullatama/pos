package id.a3.feature.cafe.presentation

import id.a3.core.domain.security.ApprovalResult
import id.a3.core.domain.security.failureMessage
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PaymentLabelAndPinMessageTest {

    @Test
    fun `label pembayaran dibedakan per jenis dan digabung bila campuran`() {
        val labels = paymentLabelsByOrder(
            listOf("a" to "cash", "b" to "qris_static", "c" to "cash", "c" to "qris_static", "d" to "lain")
        )
        assertEquals("Tunai", labels["a"])
        assertEquals("QRIS", labels["b"])
        assertEquals("Tunai + QRIS", labels["c"])
        assertEquals(null, labels["d"])
    }

    @Test
    fun `pesan PIN berbeda untuk salah, terkunci, dan akun belum tersinkron`() {
        val wrong = ApprovalResult.WrongPin(2).failureMessage()!!
        val locked = ApprovalResult.Locked(300).failureMessage()!!
        val none = ApprovalResult.NoApproverAvailable.failureMessage()!!
        assertTrue(wrong.contains("salah") && wrong.contains("2"))
        assertTrue(locked.contains("5 menit"))
        assertTrue(none.contains("belum tersedia"))
        assertEquals(3, setOf(wrong, locked, none).size)
    }
}
