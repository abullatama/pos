package id.a3.core.data.repository

import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.util.PhoneNumberUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Memulihkan pesanan yang kehilangan pelanggannya (customerId menunjuk baris yang sudah tidak ada).
 *
 * Nama dan nomor pelanggan masih tersimpan di antrean sinkron (event customer.created atau order.created
 * dengan pelanggan inline). Pemulihan hanya menyambung ulang penunjuk pelanggan atau membuat ulang baris
 * pelanggan; nilai uang, status, dan antrean sinkron tidak diubah. Aman dijalankan berulang kali.
 */
object OrderCustomerRepair {
    data class Contact(val name: String, val phone: String)

    /** Mengambil nama dan nomor pelanggan dari satu bundel event. customer.created didahulukan. */
    fun contactFromEvents(eventsJson: String, orderId: String, customerId: String): Contact? {
        val events: JsonArray = runCatching { Json.parseToJsonElement(eventsJson).jsonArray }.getOrNull() ?: return null
        var fromOrder: Contact? = null
        for (element in events) {
            val event = element as? JsonObject ?: continue
            val type = event["type"]?.jsonPrimitive?.content
            val payload = event["payload"] as? JsonObject ?: continue
            when (type) {
                "customer.created" -> if (payload["customer_id"]?.jsonPrimitive?.content == customerId) {
                    contact(payload)?.let { return it }
                }
                "order.created" -> if (payload["order_id"]?.jsonPrimitive?.content == orderId) {
                    (payload["customer"] as? JsonObject)?.let { fromOrder = contact(it) }
                }
            }
        }
        return fromOrder
    }

    private fun contact(obj: JsonObject): Contact? {
        val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val phone = obj["phone"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        return Contact(name, phone)
    }

    /** Mengembalikan jumlah pesanan yang berhasil dipulihkan. */
    suspend fun repair(orderDao: OrderDao, customerDao: CustomerDao, outboxDao: OutboxDao): Int {
        var repaired = 0
        for (order in orderDao.getOrdersWithMissingCustomer()) {
            val customerId = order.customerId ?: continue
            val bundles = (outboxDao.findBundlesByOrderId(order.id) + outboxDao.findBundlesByOrderId(customerId))
                .distinctBy { it.bundleId }
            val contact = bundles.firstNotNullOfOrNull { contactFromEvents(it.eventsJson, order.id, customerId) }
                ?: continue
            val phone = PhoneNumberUtil.normalize(contact.phone)
            val existing = customerDao.findCustomerByPhone(phone)
            if (existing != null) {
                orderDao.updateOrderCustomerId(order.id, existing.id)
            } else {
                customerDao.insertCustomer(CustomerEntity(id = customerId, name = contact.name, phone = phone))
            }
            repaired++
        }
        return repaired
    }
}
