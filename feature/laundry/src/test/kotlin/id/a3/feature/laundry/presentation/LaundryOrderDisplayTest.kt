package id.a3.feature.laundry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LaundryOrderDisplayTest {

    private val zone = ZoneId.of("Asia/Makassar")
    private fun millis(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `ekspres dikenali dari akhiran nama baris`() {
        assertTrue(isExpressLineName("Kiloan Reguler (Cuci + Setrika) (Express)"))
        assertFalse(isExpressLineName("Kiloan Reguler (Cuci + Setrika)"))
        assertEquals("Bed Cover", plainServiceName("Bed Cover (Express)"))
        assertEquals("Bed Cover", plainServiceName("Bed Cover"))
    }

    @Test
    fun `berat ditulis dengan koma Indonesia tanpa nol berlebih`() {
        assertEquals("2 kg", formatWeightKg(2.0))
        assertEquals("2,5 kg", formatWeightKg(2.5))
        assertEquals("1,25 kg", formatWeightKg(1.25))
    }

    @Test
    fun `umur cucian belum diambil memakai hari ini kemarin dan jumlah hari`() {
        val now = millis(6, 15, 0)
        assertEquals("Hari ini, 09.05", orderAgeLabel(millis(6, 9, 5), now, unfinished = true, zone = zone))
        assertEquals("Kemarin, 20.30", orderAgeLabel(millis(5, 20, 30), now, unfinished = true, zone = zone))
        assertEquals("3 hari lalu", orderAgeLabel(millis(3, 8, 0), now, unfinished = true, zone = zone))
    }

    @Test
    fun `cucian selesai memakai tanggal biasa`() {
        val now = millis(6, 15, 0)
        val label = orderAgeLabel(millis(3, 8, 0), now, unfinished = false, zone = zone)
        assertTrue(label, label.startsWith("3 ") && label.endsWith(", 08.00"))
    }
}
