package id.a3.core.sync

import id.a3.core.sync.model.OrderNotificationItemDto
import id.a3.core.sync.model.OrderNotificationStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderNotificationStatusResponseTest {
    @Test
    fun `tanpa catatan pesan statusnya none`() {
        assertEquals("none", OrderNotificationStatusResponse().effectiveStatus)
    }

    @Test
    fun `status tingkat atas dipakai bila ada`() {
        val r = OrderNotificationStatusResponse(
            status = "failed",
            notifications = listOf(OrderNotificationItemDto("siap", "sent", 1, "2026-11-05T10:00:00Z"))
        )
        assertEquals("failed", r.effectiveStatus)
    }

    @Test
    fun `pesan terbaru menurut percobaan terakhir yang dipakai`() {
        val r = OrderNotificationStatusResponse(
            notifications = listOf(
                OrderNotificationItemDto("dibuat", "sent", 1, "2026-11-05T09:00:00Z"),
                OrderNotificationItemDto("siap", "failed", 5, "2026-11-05T11:00:00Z")
            )
        )
        assertEquals("failed", r.effectiveStatus)
    }

    @Test
    fun `urutan daftar tidak mempengaruhi pesan terbaru`() {
        val r = OrderNotificationStatusResponse(
            notifications = listOf(
                OrderNotificationItemDto("siap", "failed", 5, "2026-11-05T11:00:00Z"),
                OrderNotificationItemDto("dibuat", "sent", 1, "2026-11-05T09:00:00Z")
            )
        )
        assertEquals("failed", r.effectiveStatus)
    }

    @Test
    fun `tanpa waktu percobaan yang terakhir dalam daftar dipakai`() {
        val r = OrderNotificationStatusResponse(
            notifications = listOf(
                OrderNotificationItemDto("dibuat", "sent"),
                OrderNotificationItemDto("siap", "queued")
            )
        )
        assertEquals("queued", r.effectiveStatus)
    }
}
