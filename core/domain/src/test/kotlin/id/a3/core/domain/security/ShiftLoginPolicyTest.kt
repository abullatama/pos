package id.a3.core.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLoginPolicyTest {
    @Test
    fun `tanpa shift aktif semua akun boleh`() {
        assertTrue(ShiftLoginPolicy.isAllowed("5", "kasir", null))
    }

    @Test
    fun `pembuka shift boleh`() {
        assertTrue(ShiftLoginPolicy.isAllowed("5", "kasir", "5"))
    }

    @Test
    fun `kasir lain ditolak saat shift berjalan`() {
        assertFalse(ShiftLoginPolicy.isAllowed("6", "kasir", "5"))
    }

    @Test
    fun `manager dan owner boleh`() {
        assertTrue(ShiftLoginPolicy.isAllowed("7", "manager", "5"))
        assertTrue(ShiftLoginPolicy.isAllowed("8", "Owner", "5"))
    }

    @Test
    fun `pembatasan hanya bila daftar dari server dan ada shift`() {
        assertTrue(ShiftLoginPolicy.shouldRestrict(accountsFromServer = true, shiftOwnerId = "5"))
        assertFalse(ShiftLoginPolicy.shouldRestrict(accountsFromServer = true, shiftOwnerId = null))
    }

    @Test
    fun `offline akun cadangan tidak disaring`() {
        assertFalse(ShiftLoginPolicy.shouldRestrict(accountsFromServer = false, shiftOwnerId = "5"))
    }

    @Test
    fun `pesan menyebut pembuka shift`() {
        assertTrue(ShiftLoginPolicy.blockedMessage("Siti").contains("Siti"))
    }
}
