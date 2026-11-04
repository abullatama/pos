package id.a3.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.entity.UserEntity
import id.a3.core.domain.security.ApprovalResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vektor PIN 1234 dihitung dengan rumus server (lihat Pbkdf2PinVerifierTest). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DefaultManagerApproverTest {

    private lateinit var db: AppDatabase
    private var now = 1_000_000L
    private var credential: String? = "kredensial-contoh"

    private val salt = "MDEyMzQ1Njc4OWFiY2RlZg=="
    private val verifier = "7yqmVjYV/c2MJ1R8mOnJN22MR46wMe9m1baM3mX3uQw="

    @Before
    fun setUp() {
        db = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = db.close()

    private fun approver() = DefaultManagerApprover(db.userDao(), { credential }, { now })

    private suspend fun addUser(id: Long, role: String, active: Boolean = true, withPin: Boolean = true) {
        db.userDao().upsert(
            UserEntity(
                id = id, name = "User $id", role = role, active = active,
                pinKdfSalt = if (withPin) salt else null,
                pinKdfIterations = if (withPin) 120_000 else null,
                pinVerifier = if (withPin) verifier else null
            )
        )
    }

    @Test
    fun `PIN manager yang benar mengembalikan identitas manager yang sebenarnya`() = runBlocking {
        addUser(2L, "manager")
        val result = approver().verify("1234")
        assertTrue(result is ApprovalResult.Approved)
        val approval = (result as ApprovalResult.Approved).approval
        assertEquals(2L, approval.userId)
        assertEquals("manager", approval.role)
    }

    @Test
    fun `PIN bawaan lama 4444 dan 9999 tidak lagi diterima`() = runBlocking {
        addUser(2L, "manager")
        val approver = approver()
        assertTrue(approver.verify("4444") is ApprovalResult.WrongPin)
        assertTrue(approver.verify("9999") is ApprovalResult.WrongPin)
    }

    @Test
    fun `kasir biasa bukan penyetuju meski PIN-nya cocok`() = runBlocking {
        addUser(3L, "kasir")
        assertEquals(ApprovalResult.NoApproverAvailable, approver().verify("1234"))
    }

    @Test
    fun `akun nonaktif atau tanpa verifier tidak bisa menyetujui`() = runBlocking {
        addUser(2L, "manager", active = false)
        addUser(4L, "manager", withPin = false)
        assertEquals(ApprovalResult.NoApproverAvailable, approver().verify("1234"))
    }

    @Test
    fun `tanpa kredensial perangkat tidak ada persetujuan`() = runBlocking {
        addUser(2L, "manager")
        credential = null
        assertEquals(ApprovalResult.NoApproverAvailable, approver().verify("1234"))
    }

    @Test
    fun `owner dapat menyetujui tetapi pembatasan peran owner menolak manager`() = runBlocking {
        addUser(1L, "owner")
        addUser(2L, "manager")
        val result = approver().verify("1234", setOf("owner"))
        assertEquals(1L, (result as ApprovalResult.Approved).approval.userId)

        db.userDao().deleteById(1L)
        assertEquals(ApprovalResult.NoApproverAvailable, approver().verify("1234", setOf("owner")))
    }

    @Test
    fun `tiga PIN salah mengunci 5 menit lalu terbuka lagi`() = runBlocking {
        addUser(2L, "manager")
        val approver = approver()

        assertEquals(ApprovalResult.WrongPin(attemptsLeft = 2), approver.verify("0000"))
        assertEquals(ApprovalResult.WrongPin(attemptsLeft = 1), approver.verify("0000"))
        assertEquals(ApprovalResult.Locked(retryAfterSeconds = 300), approver.verify("0000"))

        // Saat terkunci, PIN yang benar pun ditolak.
        now += 60_000
        val stillLocked = approver.verify("1234")
        assertTrue(stillLocked is ApprovalResult.Locked)
        assertEquals(240L, (stillLocked as ApprovalResult.Locked).retryAfterSeconds)

        now += 241_000
        assertTrue(approver.verify("1234") is ApprovalResult.Approved)
    }

    @Test
    fun `PIN benar mengatur ulang hitungan salah`() = runBlocking {
        addUser(2L, "manager")
        val approver = approver()
        approver.verify("0000")
        approver.verify("0000")
        assertTrue(approver.verify("1234") is ApprovalResult.Approved)
        assertEquals(ApprovalResult.WrongPin(attemptsLeft = 2), approver.verify("0000"))
    }
}
