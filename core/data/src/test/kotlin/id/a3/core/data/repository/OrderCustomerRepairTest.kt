package id.a3.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderCustomerRepairTest {
    private val created = """
        [{"event_id":"e1","type":"customer.created","occurred_at":"2026-11-05T01:00:00Z",
          "payload":{"customer_id":"cust-1","name":"abul","phone":"081224921695"}},
         {"event_id":"e2","type":"order.created","occurred_at":"2026-11-05T01:00:00Z",
          "payload":{"order_id":"ord-1","customer_id":"cust-1","total":22000}}]
    """.trimIndent()

    private val inline = """
        [{"event_id":"e2","type":"order.created","occurred_at":"2026-11-05T01:00:00Z",
          "payload":{"order_id":"ord-2","customer":{"name":"Sari","phone":"+6281300000001"},"total":10000}}]
    """.trimIndent()

    @Test
    fun `nama dan nomor diambil dari customer created`() {
        val c = OrderCustomerRepair.contactFromEvents(created, "ord-1", "cust-1")
        assertEquals(OrderCustomerRepair.Contact("abul", "081224921695"), c)
    }

    @Test
    fun `pelanggan inline di order created dipakai bila tidak ada customer created`() {
        val c = OrderCustomerRepair.contactFromEvents(inline, "ord-2", "cust-2")
        assertEquals(OrderCustomerRepair.Contact("Sari", "+6281300000001"), c)
    }

    @Test
    fun `pelanggan atau pesanan lain tidak cocok`() {
        assertNull(OrderCustomerRepair.contactFromEvents(created, "ord-1", "cust-lain"))
        assertNull(OrderCustomerRepair.contactFromEvents(inline, "ord-lain", "cust-2"))
    }

    @Test
    fun `json rusak tidak membuat galat`() {
        assertNull(OrderCustomerRepair.contactFromEvents("bukan json", "ord-1", "cust-1"))
        assertNull(OrderCustomerRepair.contactFromEvents("{}", "ord-1", "cust-1"))
    }

    @Test
    fun `nama atau nomor kosong diabaikan`() {
        val json = """[{"type":"customer.created","payload":{"customer_id":"c","name":"","phone":"0812"}}]"""
        assertNull(OrderCustomerRepair.contactFromEvents(json, "o", "c"))
    }
}
