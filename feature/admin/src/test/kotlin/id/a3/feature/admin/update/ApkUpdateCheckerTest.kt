package id.a3.feature.admin.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateCheckerTest {
    private val installed = ApkInfo("id.a3coffeeshop", 10003, "1.0.3", setOf("aa"))

    private fun rejected(candidate: ApkInfo?) =
        (ApkUpdateChecker.check(installed, candidate) as ApkCheckResult.Rejected).message

    @Test
    fun `versi lebih baru dengan tanda tangan sama diterima`() {
        val r = ApkUpdateChecker.check(installed, ApkInfo("id.a3coffeeshop", 10004, "1.0.4", setOf("aa")))
        assertEquals(ApkCheckResult.Ok("Terpasang 1.0.3, akan menjadi 1.0.4"), r)
    }

    @Test
    fun `berkas rusak ditolak`() {
        assertTrue(rejected(null).contains("rusak"))
    }

    @Test
    fun `aplikasi lain ditolak dengan petunjuk membuka aplikasi yang benar`() {
        val msg = rejected(ApkInfo("id.athialoundry", 10009, "1.0.9", setOf("aa")))
        assertTrue(msg.contains("Athia Laundry"))
        assertTrue(msg.contains("A3 Coffeeshop"))
    }

    @Test
    fun `versi sama ditolak`() {
        assertTrue(rejected(ApkInfo("id.a3coffeeshop", 10003, "1.0.3", setOf("aa"))).contains("sudah terpasang"))
    }

    @Test
    fun `versi lebih lama ditolak`() {
        assertTrue(rejected(ApkInfo("id.a3coffeeshop", 10002, "1.0.2", setOf("aa"))).contains("lebih lama"))
    }

    @Test
    fun `tanda tangan berbeda ditolak`() {
        assertTrue(rejected(ApkInfo("id.a3coffeeshop", 10004, "1.0.4", setOf("bb"))).contains("tanda tangan"))
    }

    @Test
    fun `tanda tangan tidak terbaca tidak memblokir`() {
        val r = ApkUpdateChecker.check(installed, ApkInfo("id.a3coffeeshop", 10004, "1.0.4", emptySet()))
        assertTrue(r is ApkCheckResult.Ok)
    }
}
