package id.a3.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLockControllerTest {
    private var now = 0L
    private var locks = 0
    private var busy = false

    private fun controller() = IdleLockController(
        timeoutMs = 120_000L,
        clock = { now },
        isBusy = { busy },
        onIdle = { locks++ }
    )

    @Test
    fun `mengunci setelah tanpa sentuhan selama batas waktu`() {
        val c = controller()
        now = 119_999L
        c.tick(isUnlocked = true)
        assertEquals(0, locks)
        now = 120_000L
        c.tick(isUnlocked = true)
        assertEquals(1, locks)
    }

    @Test
    fun `sentuhan mengulang hitungan`() {
        val c = controller()
        now = 100_000L
        c.onUserActivity()
        now = 200_000L
        c.tick(true)
        assertEquals(0, locks)
        now = 220_000L
        c.tick(true)
        assertEquals(1, locks)
    }

    @Test
    fun `tidak mengunci saat sibuk dan hitungan diulang setelah selesai`() {
        val c = controller()
        busy = true
        now = 500_000L
        c.tick(true)
        assertEquals(0, locks)
        busy = false
        now = 500_000L + 119_999L
        c.tick(true)
        assertEquals(0, locks)
        now = 500_000L + 120_000L
        c.tick(true)
        assertEquals(1, locks)
    }

    @Test
    fun `tidak memanggil kunci saat layar sudah terkunci`() {
        val c = controller()
        now = 1_000_000L
        c.tick(isUnlocked = false)
        assertEquals(0, locks)
        now += 119_999L
        c.tick(true)
        assertEquals(0, locks)
    }

    @Test
    fun `waktu berlalu saat aplikasi di latar tetap mengunci begitu kembali`() {
        val c = controller()
        now = 10 * 60_000L
        c.tick(true)
        assertEquals(1, locks)
    }

    @Test
    fun `batas bawaan dua menit`() {
        assertEquals(120_000L, IdleLockController.DEFAULT_TIMEOUT_MS)
        assertFalse(busy)
        assertTrue(true)
    }
}
