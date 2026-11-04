package id.a3.core.sync.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustDnsTest {

    @Test
    fun `fallback lookup resolves a3-api nen asia to official IP 203 175 10 97`() {
        val addresses = RobustDns.fallbackLookup("a3-api.nen.asia")
        assertNotNull(addresses)
        assertEquals(1, addresses!!.size)
        assertEquals("203.175.10.97", addresses[0].hostAddress)
        assertEquals("a3-api.nen.asia", addresses[0].hostName)
    }

    @Test
    fun `fallback lookup is case insensitive`() {
        val addresses = RobustDns.fallbackLookup("A3-API.NEN.ASIA")
        assertNotNull(addresses)
        assertEquals(1, addresses!!.size)
        assertEquals("203.175.10.97", addresses[0].hostAddress)
    }

    @Test
    fun `fallback lookup retains backward compatibility for a3-api sidik cloud`() {
        val addresses = RobustDns.fallbackLookup("a3-api.sidik.cloud")
        assertNotNull(addresses)
        assertEquals(2, addresses!!.size)
        val ips = addresses.map { it.hostAddress }
        assertTrue(ips.contains("104.21.23.205"))
        assertTrue(ips.contains("172.67.182.160"))
    }

    @Test
    fun `fallback lookup returns null for unregistered domains`() {
        assertNull(RobustDns.fallbackLookup("unknown.domain.invalid"))
        assertNull(RobustDns.fallbackLookup("google.com"))
    }

    @Test
    fun `lookup returns valid addresses for a3-api nen asia`() {
        val addresses = RobustDns.lookup("a3-api.nen.asia")
        assertTrue(addresses.isNotEmpty())
    }
}
