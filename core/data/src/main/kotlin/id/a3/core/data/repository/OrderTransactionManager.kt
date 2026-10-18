package id.a3.core.data.repository

import androidx.room.withTransaction
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.LaundryPickupDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mengelola penulisan transaksi lokal dan outbox secara atomik:
 * Pesanan dan antrean outbox ditulis dalam satu transaksi database Room.
 * Jika salah satu gagal, seluruh transaksi dibatalkan (tidak ada outbox yatim / order tanpa outbox).
 */
class OrderTransactionManager(
    private val database: AppDatabase,
    val orderDao: OrderDao = database.orderDao(),
    val outboxDao: OutboxDao = database.outboxDao(),
    val masterDataDao: MasterDataDao = database.masterDataDao(),
    val customerDao: CustomerDao = database.customerDao(),
    val laundryPickupDao: LaundryPickupDao = database.laundryPickupDao()
) {

    /**
     * Mengamati seluruh pesanan terbuka (open orders) yang sedang aktif di cafe.
     */
    fun observeOpenOrders(): Flow<List<OrderWithLines>> = orderDao.observeOpenOrdersWithLines()

    /**
     * Mengamati seluruh pesanan yang sudah selesai/lunas (completed orders).
     */
    fun observeCompletedOrders(): Flow<List<OrderWithLines>> = orderDao.observeCompletedOrdersWithLines()

    /**
     * Mengamati seluruh pesanan laundry (bisa difilter berdasarkan status).
     */
    fun observeLaundryOrders(statusFilter: String? = null): Flow<List<OrderWithLines>> {
        return if (statusFilter.isNullOrBlank() || statusFilter.lowercase() == "semua") {
            orderDao.observeLaundryOrdersWithLines()
        } else {
            orderDao.observeLaundryOrdersWithLinesByStatus(statusFilter.lowercase())
        }
    }

    /**
     * Mengamati riwayat pengambilan per baris pakaian untuk suatu order.
     */
    fun observePickupsForOrder(orderId: String): Flow<List<LaundryPickupEntity>> =
        laundryPickupDao.observePickupsByOrderId(orderId)

    /**
     * Mengambil satu pesanan beserta baris itemnya berdasarkan ID.
     */
    suspend fun getOrderWithLines(orderId: String): OrderWithLines? = orderDao.getOrderWithLines(orderId)

    /**
     * Mengambil satu pesanan beserta baris itemnya berdasarkan nomor tampil (misal: L1-0012).
     */
    suspend fun getOrderWithLinesByDisplayNumber(displayNumber: String): OrderWithLines? =
        orderDao.getOrderWithLinesByDisplayNumber(displayNumber)

    /**
     * Mengambil satu pesanan berdasarkan nomor tampil.
     */
    suspend fun getOrderByDisplayNumber(displayNumber: String): OrderEntity? =
        orderDao.getOrderByDisplayNumber(displayNumber)

    /**
     * Menyimpan order, rincian baris item, dan antrean outbox secara atomik dalam satu transaksi Room.
     */
    suspend fun createOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        outbox: OutboxEntity
    ) {
        database.withTransaction {
            orderDao.insertOrder(order)
            if (lines.isNotEmpty()) {
                orderDao.insertLines(lines)
            }
            outboxDao.insert(outbox)
        }
    }

    /**
     * Menandai status menu Habis/Tersedia di Room dan mencatat event outbox 'menu.sold_out_changed' secara atomik.
     */
    suspend fun recordSoldOutChanged(
        menuItemId: String,
        soldOut: Boolean,
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            masterDataDao.updateMenuItemSoldOut(menuItemId, soldOut)
            val eventId = UUID.randomUUID().toString()
            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val payload = buildJsonObject {
                put("menu_item_id", menuItemId)
                put("sold_out", soldOut)
            }
            val eventObj = buildJsonObject {
                put("event_id", eventId)
                put("type", "menu.sold_out_changed")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }
            val eventsArray = buildJsonArray {
                add(eventObj)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Memindahkan meja pesanan dine-in di Room dan mencatat event outbox 'order.table_moved' secara atomik.
     */
    suspend fun recordTableMoved(
        orderId: String,
        fromTable: String,
        toTable: String,
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            orderDao.updateTableLabel(orderId, toTable)
            val eventId = UUID.randomUUID().toString()
            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val payload = buildJsonObject {
                put("order_id", orderId)
                put("from_table", fromTable)
                put("to_table", toTable)
            }
            val eventObj = buildJsonObject {
                put("event_id", eventId)
                put("type", "order.table_moved")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }
            val eventsArray = buildJsonArray {
                add(eventObj)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Membuat pesanan terbuka baru (dine-in) dan mencatat event 'order.created' serta 'order.sent_to_kitchen' secara atomik.
     */
    suspend fun createOpenOrderWithKitchenTicket(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            orderDao.insertOrder(order.copy(status = "open"))
            if (lines.isNotEmpty()) {
                orderDao.insertLines(lines)
            }

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            // 1. Event order.created
            val orderLinesArray = buildJsonArray {
                lines.forEach { line ->
                    add(
                        buildJsonObject {
                            put("line_id", line.id)
                            put("item_ref_id", line.itemRefId)
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            line.note?.let { put("note", it) }
                        }
                    )
                }
            }

            val createdPayload = buildJsonObject {
                put("order_id", order.id)
                put("display_number", order.displayNumber)
                put("order_type", order.orderType)
                order.tableLabel?.let { put("table_label", it) }
                put("lines", orderLinesArray)
                put("subtotal", order.subtotal)
                put("discount", order.discount)
                put("service_charge", order.serviceCharge)
                put("tax", order.tax)
                put("rounding", order.rounding)
                put("total", order.total)
            }

            val createdEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "order.created")
                put("occurred_at", occurredAt)
                put("payload", createdPayload)
            }

            // 2. Event order.sent_to_kitchen (hanya membawa line_ids item yang baru dikirim)
            val kitchenLineIds = buildJsonArray {
                lines.forEach { add(JsonPrimitive(it.id)) }
            }
            val kitchenPayload = buildJsonObject {
                put("order_id", order.id)
                put("line_ids", kitchenLineIds)
            }
            val kitchenEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "order.sent_to_kitchen")
                put("occurred_at", occurredAt)
                put("payload", kitchenPayload)
            }

            val eventsArray = buildJsonArray {
                add(createdEvent)
                add(kitchenEvent)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Menambahkan item baru ke pesanan terbuka dan mencatat event 'order.items_added' serta 'order.sent_to_kitchen' secara atomik.
     */
    suspend fun addItemsToOpenOrder(
        orderId: String,
        newLines: List<OrderLineEntity>,
        newSubtotal: Long,
        newDiscount: Long,
        newServiceCharge: Long,
        newTax: Long,
        newRounding: Long,
        newTotal: Long,
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            if (newLines.isNotEmpty()) {
                orderDao.insertLines(newLines)
            }
            orderDao.updateOrderTotals(
                orderId = orderId,
                subtotal = newSubtotal,
                discount = newDiscount,
                serviceCharge = newServiceCharge,
                tax = newTax,
                rounding = newRounding,
                total = newTotal
            )

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            // 1. Event order.items_added (hanya membawa baris baru dan total terbaru)
            val newLinesArray = buildJsonArray {
                newLines.forEach { line ->
                    add(
                        buildJsonObject {
                            put("line_id", line.id)
                            put("item_ref_id", line.itemRefId)
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            line.note?.let { put("note", it) }
                        }
                    )
                }
            }

            val itemsAddedPayload = buildJsonObject {
                put("order_id", orderId)
                put("lines", newLinesArray)
                put("subtotal", newSubtotal)
                put("discount", newDiscount)
                put("service_charge", newServiceCharge)
                put("tax", newTax)
                put("rounding", newRounding)
                put("total", newTotal)
            }

            val itemsAddedEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "order.items_added")
                put("occurred_at", occurredAt)
                put("payload", itemsAddedPayload)
            }

            // 2. Event order.sent_to_kitchen (hanya membawa line_ids dari newLines!)
            val kitchenLineIds = buildJsonArray {
                newLines.forEach { add(JsonPrimitive(it.id)) }
            }
            val kitchenPayload = buildJsonObject {
                put("order_id", orderId)
                put("line_ids", kitchenLineIds)
            }
            val kitchenEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "order.sent_to_kitchen")
                put("occurred_at", occurredAt)
                put("payload", kitchenPayload)
            }

            val eventsArray = buildJsonArray {
                add(itemsAddedEvent)
                add(kitchenEvent)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Mencatat pembayaran pesanan (Tunai atau QRIS Statis) di Room dan menghasilkan event outbox 'payment.recorded'.
     */
    suspend fun recordPayment(
        orderId: String,
        method: String,
        amount: Long,
        tendered: Long? = null,
        isDp: Boolean = false,
        paymentId: String = UUID.randomUUID().toString(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val currentOrder = orderDao.getOrderById(orderId)
                ?: throw IllegalStateException("Pesanan dengan ID $orderId tidak ditemukan")

            val newPaidAmount = currentOrder.paidAmount + amount
            val newStatus = if (isDp) "dp" else "paid"

            orderDao.updatePaymentStatus(
                orderId = orderId,
                status = newStatus,
                paidAmount = newPaidAmount
            )

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val paymentPayload = buildJsonObject {
                put("payment_id", paymentId)
                put("order_id", orderId)
                put("method", method)
                put("amount", amount)
                tendered?.let { put("tendered", it) }
                put("idempotency_key", idempotencyKey)
            }

            val paymentEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "payment.recorded")
                put("occurred_at", occurredAt)
                put("payload", paymentPayload)
            }

            val eventsArray = buildJsonArray {
                add(paymentEvent)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Menyimpan pesanan langsung lunas (misal take-away) dan mencatat event 'order.created' serta 'payment.recorded' secara atomik.
     */
    suspend fun createAndPayOrder(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        method: String,
        amount: Long,
        tendered: Long? = null,
        paymentId: String = UUID.randomUUID().toString(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        deviceId: String = "dev-cafe-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val paidOrder = order.copy(status = "paid", paidAmount = amount)
            orderDao.insertOrder(paidOrder)
            if (lines.isNotEmpty()) {
                orderDao.insertLines(lines)
            }

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            // 1. Event order.created
            val orderLinesArray = buildJsonArray {
                lines.forEach { line ->
                    add(
                        buildJsonObject {
                            put("line_id", line.id)
                            put("item_ref_id", line.itemRefId)
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            line.note?.let { put("note", it) }
                        }
                    )
                }
            }

            val createdPayload = buildJsonObject {
                put("order_id", paidOrder.id)
                put("display_number", paidOrder.displayNumber)
                put("order_type", paidOrder.orderType)
                paidOrder.tableLabel?.let { put("table_label", it) }
                put("lines", orderLinesArray)
                put("subtotal", paidOrder.subtotal)
                put("discount", paidOrder.discount)
                put("service_charge", paidOrder.serviceCharge)
                put("tax", paidOrder.tax)
                put("rounding", paidOrder.rounding)
                put("total", paidOrder.total)
            }

            val createdEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "order.created")
                put("occurred_at", occurredAt)
                put("payload", createdPayload)
            }

            // 2. Event payment.recorded
            val paymentPayload = buildJsonObject {
                put("payment_id", paymentId)
                put("order_id", paidOrder.id)
                put("method", method)
                put("amount", amount)
                tendered?.let { put("tendered", it) }
                put("idempotency_key", idempotencyKey)
            }

            val paymentEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "payment.recorded")
                put("occurred_at", occurredAt)
                put("payload", paymentPayload)
            }

            val eventsArray = buildJsonArray {
                add(createdEvent)
                add(paymentEvent)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Membuat pesanan laundry baru dan mencatat event 'customer.created' (jika pelanggan baru)
     * serta 'order.created' ke dalam outbox secara atomik.
     * Sesuai aturan arsitektur, event customer.created dan laundry.* tidak mensyaratkan shift (shift_id boleh null).
     */
    suspend fun createLaundryOrderWithCustomer(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        customer: CustomerEntity?,
        isNewCustomer: Boolean,
        deviceId: String = "dev-laundry-1",
        shiftId: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            if (customer != null && isNewCustomer) {
                customerDao.insertCustomer(customer)
            }

            val finalOrder = if (customer != null) {
                order.copy(customerId = customer.id)
            } else {
                order
            }

            orderDao.insertOrder(finalOrder)
            if (lines.isNotEmpty()) {
                orderDao.insertLines(lines)
            }

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val eventsArray = buildJsonArray {
                // 1. Event customer.created (jika pelanggan baru dibuat)
                if (customer != null && isNewCustomer) {
                    val customerPayload = buildJsonObject {
                        put("customer_id", customer.id)
                        put("name", customer.name)
                        put("phone", customer.phone)
                    }
                    val customerEvent = buildJsonObject {
                        put("event_id", UUID.randomUUID().toString())
                        put("type", "customer.created")
                        put("occurred_at", occurredAt)
                        put("payload", customerPayload)
                    }
                    add(customerEvent)
                }

                // 2. Event order.created
                val orderLinesArray = buildJsonArray {
                    lines.forEach { line ->
                        add(
                            buildJsonObject {
                                put("line_id", line.id)
                                put("item_ref_id", line.itemRefId)
                                put("name", line.nameSnapshot)
                                put("quantity", line.quantity)
                                put("unit_price", line.unitPriceSnapshot)
                                line.note?.let { put("note", it) }
                            }
                        )
                    }
                }

                val orderPayload = buildJsonObject {
                    put("order_id", finalOrder.id)
                    put("display_number", finalOrder.displayNumber)
                    put("order_type", finalOrder.orderType)
                    finalOrder.customerId?.let { put("customer_id", it) }
                    put("lines", orderLinesArray)
                    put("subtotal", finalOrder.subtotal)
                    put("discount", finalOrder.discount)
                    put("service_charge", finalOrder.serviceCharge)
                    put("tax", finalOrder.tax)
                    put("rounding", finalOrder.rounding)
                    put("total", finalOrder.total)
                }

                val orderEvent = buildJsonObject {
                    put("event_id", UUID.randomUUID().toString())
                    put("type", "order.created")
                    put("occurred_at", occurredAt)
                    put("payload", orderPayload)
                }
                add(orderEvent)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Memperbarui status cucian satu arah (Diterima -> Diproses -> Siap) dan mencatat
     * event outbox 'laundry.status_changed' secara atomik.
     * Sesuai aturan arsitektur, event laundry.* tidak mensyaratkan shift (shift_id = null).
     */
    suspend fun advanceLaundryOrderStatus(
        orderId: String,
        targetStatus: String,
        deviceId: String = "dev-laundry-1",
        rework: Boolean = false,
        reworkReason: String? = null,
        reworkApprovedBy: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val existing = orderDao.getOrderById(orderId)
                ?: throw IllegalArgumentException("Pesanan dengan ID $orderId tidak ditemukan")

            // Validasi transisi status satu arah:
            // received / open -> processing
            // processing -> ready
            // rework: ready -> processing
            if (rework) {
                if (existing.status != "ready") {
                    throw IllegalStateException("Cuci ulang hanya dapat dilakukan dari status Siap (ready), status saat ini: ${existing.status}")
                }
                if (targetStatus != "processing") {
                    throw IllegalArgumentException("Status cuci ulang harus menjadi processing, diberikan: $targetStatus")
                }
            } else {
                val isValidTransition = when (existing.status.lowercase()) {
                    "open", "received" -> targetStatus == "processing"
                    "processing" -> targetStatus == "ready"
                    else -> false
                }
                if (!isValidTransition) {
                    throw IllegalStateException("Transisi status satu arah ilegal dari '${existing.status}' ke '$targetStatus'")
                }
            }

            orderDao.updateOrder(existing.copy(status = targetStatus))

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val payload = buildJsonObject {
                put("order_id", orderId)
                put("status", targetStatus)
                if (rework) {
                    put("rework", true)
                    reworkReason?.let { put("reason", it) }
                    reworkApprovedBy?.let { put("approved_by", it) }
                }
            }

            val eventObj = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "laundry.status_changed")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(eventObj)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = null, // Event laundry.* tidak terikat shift
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Mencatat pengambilan cucian per baris layanan (LDR-11, F22).
     * - Menyimpan entitas pengambilan ke Room (laundry_pickups).
     * - Memvalidasi proteksi belum lunas (LDR-10, F24): jika sisa > 0, wajib menyertakan [approvedBy] (Manager).
     * - Memeriksa apakah semua baris order telah ditandai is_final = true. Jika ya, status order bertransisi ke 'picked_up'.
     * - Menyusun event 'laundry.picked_up' ke antrean outbox secara atomik tanpa shift_id.
     */
    suspend fun recordLaundryPickups(
        orderId: String,
        items: List<LaundryPickupItem>,
        userId: String? = null,
        approvedBy: String? = null,
        deviceId: String = "dev-laundry-1"
    ): OutboxEntity {
        return database.withTransaction {
            val orderWithLines = orderDao.getOrderWithLines(orderId)
                ?: throw IllegalArgumentException("Pesanan dengan ID $orderId tidak ditemukan")

            val order = orderWithLines.order
            val isFullyPaid = order.paidAmount >= order.total

            // F24 & LDR-10: Penyerahan cucian belum lunas wajib persetujuan PIN Manager!
            if (!isFullyPaid && approvedBy.isNullOrBlank()) {
                throw IllegalStateException("Penyerahan cucian belum lunas wajib diotorisasi PIN Manager (F24 / LDR-10)")
            }

            if (items.isEmpty()) {
                throw IllegalArgumentException("Daftar pengambilan pakaian tidak boleh kosong")
            }

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            // 1. Simpan entitas pengambilan ke Room
            val pickupEntities = items.map { item ->
                LaundryPickupEntity(
                    id = UUID.randomUUID().toString(),
                    orderId = orderId,
                    orderLineId = item.orderLineId,
                    quantity = item.quantity,
                    isFinal = item.isFinal,
                    note = item.note,
                    userId = userId,
                    approvedBy = approvedBy
                )
            }
            laundryPickupDao.insertPickups(pickupEntities)

            // 2. Evaluasi apakah seluruh baris pesanan sudah berstatus pengambilan terakhir (is_final)
            val allPastPickups = laundryPickupDao.getPickupsByOrderId(orderId)
            val linesWithFinalPickup = allPastPickups.filter { it.isFinal }.map { it.orderLineId }.toSet()
            val allLinesCompleted = orderWithLines.lines.all { line -> linesWithFinalPickup.contains(line.id) }

            if (allLinesCompleted) {
                // Semua baris sudah diambil tuntas -> status order menjadi 'picked_up'
                orderDao.updateOrder(order.copy(status = "picked_up"))
            }

            // 3. Susun event outbox laundry.picked_up untuk tiap item pengambilan
            val eventsArray = buildJsonArray {
                items.forEach { item ->
                    val payload = buildJsonObject {
                        put("order_id", orderId)
                        put("order_line_id", item.orderLineId)
                        put("quantity", item.quantity)
                        put("is_final", item.isFinal)
                        item.note?.let { put("note", it) }
                        approvedBy?.let { put("approved_by", it) }
                    }
                    val eventObj = buildJsonObject {
                        put("event_id", UUID.randomUUID().toString())
                        put("type", "laundry.picked_up")
                        put("occurred_at", occurredAt)
                        put("payload", payload)
                    }
                    add(eventObj)
                }
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = null, // Event laundry.* tidak terikat shift
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Cuci ulang karena komplain pelanggan (LDR-12, F23).
     * Status Siap kembali ke Diproses dengan alasan dan PIN manager, tanpa tagihan baru.
     */
    suspend fun reworkLaundryOrder(
        orderId: String,
        reason: String,
        approvedBy: String,
        deviceId: String = "dev-laundry-1"
    ): OutboxEntity {
        return advanceLaundryOrderStatus(
            orderId = orderId,
            targetStatus = "processing",
            deviceId = deviceId,
            rework = true,
            reworkReason = reason,
            reworkApprovedBy = approvedBy
        )
    }
}

data class LaundryPickupItem(
    val orderLineId: String,
    val quantity: Int,
    val isFinal: Boolean,
    val note: String? = null
)
