package id.a3.core.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaundryStatusMapperTest {

    @Test
    fun `toBackend maps English laundry status to Laravel 12 Indonesian status`() {
        assertEquals("diterima", LaundryStatusMapper.toBackend("received"))
        assertEquals("diterima", LaundryStatusMapper.toBackend("open"))
        assertEquals("diproses", LaundryStatusMapper.toBackend("processing"))
        assertEquals("siap", LaundryStatusMapper.toBackend("ready"))
        assertEquals("diambil", LaundryStatusMapper.toBackend("picked_up"))
    }

    @Test
    fun `toBackend preserves already mapped Indonesian status`() {
        assertEquals("diterima", LaundryStatusMapper.toBackend("diterima"))
        assertEquals("diproses", LaundryStatusMapper.toBackend("diproses"))
        assertEquals("siap", LaundryStatusMapper.toBackend("siap"))
        assertEquals("diambil", LaundryStatusMapper.toBackend("diambil"))
    }

    @Test
    fun `toOpenApi maps Indonesian status to English OpenAPI status`() {
        assertEquals("received", LaundryStatusMapper.toOpenApi("diterima"))
        assertEquals("processing", LaundryStatusMapper.toOpenApi("diproses"))
        assertEquals("ready", LaundryStatusMapper.toOpenApi("siap"))
        assertEquals("picked_up", LaundryStatusMapper.toOpenApi("diambil"))
    }

    @Test
    fun `normalizeNumericUserId parses numeric, manager, owner, and returns null for blank`() {
        assertNull(LaundryStatusMapper.normalizeNumericUserId(null))
        assertNull(LaundryStatusMapper.normalizeNumericUserId(""))
        assertNull(LaundryStatusMapper.normalizeNumericUserId("   "))
        assertNull(LaundryStatusMapper.normalizeNumericUserId("null"))
        assertEquals(12L, LaundryStatusMapper.normalizeNumericUserId("12"))
        assertEquals(4L, LaundryStatusMapper.normalizeNumericUserId("Manager"))
        assertEquals(2L, LaundryStatusMapper.normalizeNumericUserId("Owner"))
        assertEquals(4L, LaundryStatusMapper.normalizeNumericUserId("Kasir 1"))
    }

    @Test
    fun `VALID_STATUSES contains both English and Indonesian status codes`() {
        assertTrue(LaundryStatusMapper.VALID_STATUSES.contains("received"))
        assertTrue(LaundryStatusMapper.VALID_STATUSES.contains("diterima"))
        assertTrue(LaundryStatusMapper.VALID_STATUSES.contains("ready"))
        assertTrue(LaundryStatusMapper.VALID_STATUSES.contains("siap"))
    }
}
