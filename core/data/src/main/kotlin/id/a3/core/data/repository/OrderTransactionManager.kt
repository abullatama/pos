package id.a3.core.data.repository

import androidx.room.withTransaction
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.dao.CashOutDao
import id.a3.core.data.local.dao.CorrectionDao
import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.LaundryPickupDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.PaymentDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.data.local.entity.CorrectionEntity
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.data.local.entity.ShiftEntity
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
    val laundryPickupDao: LaundryPickupDao = database.laundryPickupDao(),
    val shiftDao: ShiftDao = database.shiftDao(),
    val paymentDao: PaymentDao = database.paymentDao(),
    val correctionDao: CorrectionDao = database.correctionDao(),
    val cashOutDao: CashOutDao = database.cashOutDao()
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
            val newStatus = if (currentOrder.orderType == "laundry") {
                currentOrder.status
            } else {
                if (isDp) "dp" else "paid"
            }

            orderDao.updatePaymentStatus(
                orderId = orderId,
                status = newStatus,
                paidAmount = newPaidAmount
            )

            val paymentEntity = PaymentEntity(
                id = paymentId,
                orderId = orderId,
                method = method,
                amount = amount,
                tendered = tendered,
                idempotencyKey = idempotencyKey,
                shiftId = shiftId ?: currentOrder.shiftId,
                isDp = isDp
            )
            paymentDao.insertPayment(paymentEntity)

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

            val paymentEntity = PaymentEntity(
                id = paymentId,
                orderId = paidOrder.id,
                method = method,
                amount = amount,
                tendered = tendered,
                idempotencyKey = idempotencyKey,
                shiftId = shiftId ?: paidOrder.shiftId,
                isDp = false
            )
            paymentDao.insertPayment(paymentEntity)

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

    /**
     * Mengamati shift aktif pada tablet ini secara reaktif.
     */
    fun observeActiveShift(): Flow<ShiftEntity?> = shiftDao.observeActiveShift()

    /**
     * Mengambil data shift aktif saat ini.
     */
    suspend fun getActiveShift(): ShiftEntity? = shiftDao.getActiveShift()

    /**
     * Membuka shift baru dengan kas awal per pecahan (F30, SHF-01 s/d SHF-04).
     * Transaksi atomik:
     * 1. Memeriksa aturan SHF-03: Hanya boleh ada 1 shift berstatus 'open' per tablet.
     * 2. Menyimpan ShiftEntity ke Room.
     * 3. Menyusun event outbox 'shift.opened' dengan breakdown pecahan uang.
     */
    suspend fun openShift(
        shift: ShiftEntity,
        breakdownMap: Map<String, Int>,
        differenceReason: String? = null,
        approvedBy: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val existingActive = shiftDao.getActiveShift()
            if (existingActive != null) {
                throw IllegalStateException("Sudah ada shift aktif (${existingActive.id}) pada tablet ini (SHF-03)")
            }

            shiftDao.insertShift(shift)

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.ofEpochMilli(shift.openedAt).toString()

            val breakdownJsonObj = buildJsonObject {
                breakdownMap.forEach { (denom, count) ->
                    if (count > 0) {
                        put(denom, count)
                    }
                }
            }

            val payload = buildJsonObject {
                put("shift_id", shift.id)
                put("opening_cash", shift.openingCash)
                put("opening_cash_breakdown", breakdownJsonObj)
                differenceReason?.let { put("difference_reason", it) }
                approvedBy?.let { put("approved_by", it) }
            }

            val event = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "shift.opened")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(event)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = shift.deviceId,
                shiftId = shift.id,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Menutup shift aktif dan mencatat event outbox 'shift.closed' secara atomik (TUP-01 s/d TUP-07, F31, F32, F34).
     */
    suspend fun closeShift(
        shiftId: String,
        countedCash: Long,
        differenceReason: String? = null,
        cashLeft: Long,
        cashDeposited: Long,
        cashTakenBy: String? = null,
        qrisCheckedPaymentIds: List<String> = emptyList(),
        handoverOrderIds: List<String> = emptyList(),
        closedAt: Long = System.currentTimeMillis()
    ): OutboxEntity {
        return database.withTransaction {
            val shift = shiftDao.getShiftById(shiftId)
                ?: throw IllegalArgumentException("Shift dengan ID $shiftId tidak ditemukan")

            if (shift.status != "open") {
                throw IllegalStateException("Shift ${shift.id} tidak berstatus open (status: ${shift.status})")
            }

            // Validasi TUP-06: Kas ditinggal + kas disetor harus sama dengan kas dihitung fisik, atau selisih beralasan
            val totalAllocated = cashLeft + cashDeposited
            if (totalAllocated != countedCash && differenceReason.isNullOrBlank()) {
                throw IllegalStateException("Alokasi kas ($totalAllocated) tidak sama dengan kas dihitung ($countedCash) dan tidak ada alasan selisih (TUP-06)")
            }

            // Perbarui entitas shift ke status closed
            val closedShift = shift.copy(
                status = "closed",
                closedAt = closedAt,
                countedCash = countedCash,
                cashLeft = cashLeft,
                cashDeposited = cashDeposited,
                cashDifferenceReason = differenceReason
            )
            shiftDao.updateShift(closedShift)

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.ofEpochMilli(closedAt).toString()

            val qrisCheckedArray = buildJsonArray {
                qrisCheckedPaymentIds.forEach { add(JsonPrimitive(it)) }
            }

            val handoverOrdersArray = buildJsonArray {
                handoverOrderIds.forEach { add(JsonPrimitive(it)) }
            }

            val payload = buildJsonObject {
                put("shift_id", shiftId)
                put("counted_cash", countedCash)
                differenceReason?.let { put("difference_reason", it) }
                put("cash_left", cashLeft)
                put("cash_deposited", cashDeposited)
                cashTakenBy?.let { put("cash_taken_by", it) }
                put("qris_checked_payment_ids", qrisCheckedArray)
                put("handover_order_ids", handoverOrdersArray)
            }

            val event = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "shift.closed")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(event)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = shift.deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Menutup paksa shift kasir yang tidak hadir atau tertinggal terbuka oleh Manager (F38, TUP-08, D-01).
     *
     * Sesuai kontrak OpenAPI ShiftForceClosed:
     * - Wajib diotorisasi Manager (approvedBy wajib).
     * - Wajib mencatat alasan / catatan penutupan paksa (note wajib).
     * - Status shift berubah menjadi 'force_closed', membebaskan tablet agar kasir berikutnya bisa membuka shift.
     * - Menghasilkan event outbox atomik 'shift.force_closed'.
     */
    suspend fun forceCloseShift(
        shiftId: String,
        note: String,
        approvedBy: String,
        closedAt: Long = System.currentTimeMillis()
    ): OutboxEntity {
        require(note.isNotBlank()) { "Catatan alasan tutup paksa shift wajib diisi (ShiftForceClosed)" }
        require(approvedBy.isNotBlank()) { "Otorisasi Manager (approved_by) wajib diisi (ShiftForceClosed)" }

        return database.withTransaction {
            val shift = shiftDao.getShiftById(shiftId)
                ?: throw IllegalArgumentException("Shift dengan ID $shiftId tidak ditemukan")

            if (shift.status != "open") {
                throw IllegalStateException("Shift ${shift.id} tidak berstatus open (status: ${shift.status})")
            }

            // Perbarui entitas shift ke status force_closed
            val forceClosedShift = shift.copy(
                status = "force_closed",
                closedAt = closedAt,
                approvedBy = approvedBy,
                differenceReason = note.trim()
            )
            shiftDao.updateShift(forceClosedShift)

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.ofEpochMilli(closedAt).toString()

            val payload = buildJsonObject {
                put("shift_id", shiftId)
                put("note", note.trim())
                put("approved_by", approvedBy.trim())
            }

            val event = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "shift.force_closed")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(event)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = shift.deviceId,
                shiftId = shiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Mencatat catatan koreksi (void, refund, manual_discount) di Room dan menghasilkan event outbox 'correction.recorded' secara atomik.
     *
     * Mengimplementasikan aturan F35, F36, dan COR-01 s/d COR-08:
     * - Bersifat append-only: data transaksi asal tidak pernah dihapus/diedit.
     * - Refund selalu tunai (refundMethod = "cash", COR-04), mengurangi kas laci shift berjalan.
     * - Atas transaksi shift sebelumnya (komplain datang belakangan, F36 / COR-05 / OQ-22):
     *   - Batas nilai: <= Rp 100.000. Jika di atas Rp 100.000, ditolak dan diarahkan ke Owner Web Dashboard.
     *   - Batas umur: <= 14 hari. Jika lebih dari 14 hari, ditolak dan diarahkan ke Owner Web Dashboard.
     *   - Ditandai isOldTransactionRefund = true.
     * - Status transaksi diperbarui ke 'refunded', 'partial_refund', atau 'void'.
     */
    suspend fun recordCorrection(
        orderId: String,
        type: String, // "void", "refund", "manual_discount", "comp"
        amount: Long,
        reasonCode: String, // "wrong_input", "customer_cancelled", "quality_complaint", "wrong_made", "promo", "other"
        approvedBy: String,
        note: String? = null,
        orderLineIds: List<String> = emptyList(),
        originalPaymentId: String? = null,
        correctionId: String = UUID.randomUUID().toString(),
        deviceId: String = "dev-1",
        currentShiftId: String? = null,
        occurredAtMs: Long = System.currentTimeMillis()
    ): OutboxEntity {
        val validReasons = setOf("wrong_input", "customer_cancelled", "quality_complaint", "wrong_made", "promo", "other")
        require(reasonCode in validReasons) { "Kode alasan '$reasonCode' tidak valid (COR-01)" }
        if (reasonCode == "other") {
            require(!note.isNullOrBlank()) { "Alasan 'other' wajib menyertakan catatan (note)" }
        }
        require(approvedBy.isNotBlank()) { "Otorisasi PIN Manager (approved_by) wajib diisi (COR-01, COR-07)" }
        require(amount > 0L) { "Nominal koreksi harus lebih besar dari 0" }

        val order = orderDao.getOrderById(orderId)
            ?: throw IllegalArgumentException("Pesanan dengan ID $orderId tidak ditemukan")

        val activeShift = shiftDao.getActiveShift()
        val effectiveShiftId = currentShiftId ?: activeShift?.id

        var isOldTx = false
        if (type == "refund") {
            if (effectiveShiftId == null) {
                throw IllegalStateException("Refund tunai memerlukan shift yang sedang aktif (COR-04)")
            }

            val orderShiftId = order.shiftId
            val orderCreatedBeforeCurrentShift = activeShift != null && order.createdAt < activeShift.openedAt
            val orderFromDifferentShift = orderShiftId != null && orderShiftId != effectiveShiftId

            if (orderCreatedBeforeCurrentShift || orderFromDifferentShift) {
                isOldTx = true
                // Penegakan aturan F36 & OQ-22:
                // 1. Batas nilai: <= Rp 100.000
                if (amount > 100_000L) {
                    throw IllegalArgumentException("Refund transaksi shift lalu melebihi Rp 100.000 hanya dapat diproses oleh Owner melalui Web Dashboard (F36, COR-05)")
                }
                // 2. Batas umur: <= 14 hari
                val ageMs = occurredAtMs - order.createdAt
                val maxAgeMs = 14L * 24 * 3600 * 1000L
                if (ageMs > maxAgeMs) {
                    throw IllegalArgumentException("Transaksi lebih dari 14 hari tidak dapat di-refund di kasir. Silakan hubungi Owner melalui Web Dashboard (F36, COR-05)")
                }
            }

            // Validasi nominal refund tidak melebihi nilai bayar
            if (amount > order.paidAmount) {
                throw IllegalArgumentException("Nominal refund (Rp$amount) melebihi nilai bayar (Rp${order.paidAmount})")
            }
        } else if (type == "void") {
            if (amount > order.total) {
                throw IllegalArgumentException("Nominal void (Rp$amount) melebihi total pesanan (Rp${order.total})")
            }
        }

        return database.withTransaction {
            // 1. Perbarui status order
            val updatedOrder = when (type) {
                "refund" -> {
                    if (amount >= order.paidAmount) {
                        order.copy(status = "refunded")
                    } else {
                        order.copy(status = "partial_refund")
                    }
                }
                "void" -> order.copy(status = "void")
                else -> order
            }
            if (updatedOrder != order) {
                orderDao.updateOrder(updatedOrder)
            }

            // 2. Simpan CorrectionEntity
            val orderLineIdsJson = buildJsonArray {
                orderLineIds.forEach { add(JsonPrimitive(it)) }
            }.toString()

            val correctionEntity = CorrectionEntity(
                id = correctionId,
                orderId = orderId,
                shiftId = effectiveShiftId,
                type = type,
                amount = amount,
                refundMethod = if (type == "refund") "cash" else null,
                originalPaymentId = originalPaymentId,
                orderLineIdsJson = orderLineIdsJson,
                reasonCode = reasonCode,
                note = note,
                approvedBy = approvedBy,
                isOldTransactionRefund = isOldTx,
                createdAt = occurredAtMs
            )
            correctionDao.insertCorrection(correctionEntity)

            // 3. Catat event outbox 'correction.recorded'
            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.ofEpochMilli(occurredAtMs).toString()

            val lineIdsJsonArray = buildJsonArray {
                orderLineIds.forEach { add(JsonPrimitive(it)) }
            }

            val payload = buildJsonObject {
                put("correction_id", correctionId)
                put("type", type)
                put("order_id", orderId)
                put("order_line_ids", lineIdsJsonArray)
                originalPaymentId?.let { put("original_payment_id", it) }
                put("amount", amount)
                if (type == "refund") {
                    put("refund_method", "cash")
                }
                put("reason_code", reasonCode)
                note?.let { put("note", it) }
                put("approved_by", approvedBy)
            }

            val event = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "correction.recorded")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(event)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = effectiveShiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }

    /**
     * Mencatat pengeluaran kas laci (kas keluar / petty cash) di Room dan menghasilkan event outbox 'cashout.recorded' secara atomik.
     *
     * Mengimplementasikan aturan F37, KAS-01, KAS-02, dan OQ-08:
     * - Mewajibkan shift sedang aktif (open).
     * - Mengurangi kas seharusnya di shift berjalan (KAS-01, rumus K1).
     * - Nominal <= Rp 50.000: kasir boleh mencatat tanpa PIN Manager (OQ-08).
     * - Nominal > Rp 50.000: wajib otorisasi PIN Manager dan foto bukti nota fisik wajib (KAS-02).
     * - Menyusun event outbox 'cashout.recorded' sesuai skema OpenAPI CashoutRecorded.
     */
    suspend fun recordCashOut(
        amount: Long,
        category: String,
        reason: String,
        receiptFileId: String? = null,
        receiptFilePath: String? = null,
        approvedBy: String? = null,
        cashoutId: String = UUID.randomUUID().toString(),
        deviceId: String = "dev-1",
        shiftId: String? = null,
        occurredAtMs: Long = System.currentTimeMillis()
    ): OutboxEntity {
        require(amount > 0L) { "Nominal kas keluar harus lebih besar dari 0" }
        require(category.isNotBlank()) { "Kategori pengeluaran kas wajib diisi" }
        require(reason.isNotBlank()) { "Alasan pengeluaran kas wajib diisi" }

        val activeShift = shiftDao.getActiveShift()
        val effectiveShiftId = shiftId ?: activeShift?.id
            ?: throw IllegalStateException("Kas keluar memerlukan shift yang sedang aktif (KAS-01)")

        val threshold = 50_000L
        if (amount > threshold) {
            require(!approvedBy.isNullOrBlank()) {
                "Pengeluaran kas di atas Rp 50.000 wajib disetujui oleh Manager (KAS-02, OQ-08)"
            }
            require(!receiptFileId.isNullOrBlank() || !receiptFilePath.isNullOrBlank()) {
                "Pengeluaran kas di atas Rp 50.000 wajib menyertakan foto nota (KAS-02)"
            }
        }

        return database.withTransaction {
            // 1. Simpan CashOutEntity ke Room
            val entity = CashOutEntity(
                id = cashoutId,
                shiftId = effectiveShiftId,
                amount = amount,
                category = category,
                reason = reason,
                receiptFileId = receiptFileId,
                receiptFilePath = receiptFilePath,
                approvedBy = approvedBy,
                createdAt = occurredAtMs
            )
            cashOutDao.insertCashOut(entity)

            // 2. Buat event outbox 'cashout.recorded'
            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.ofEpochMilli(occurredAtMs).toString()

            val payload = buildJsonObject {
                put("cashout_id", cashoutId)
                put("amount", amount)
                put("category", category)
                put("reason", reason)
                receiptFileId?.let { put("receipt_file_id", it) }
                approvedBy?.let { put("approved_by", it) }
            }

            val event = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "cashout.recorded")
                put("occurred_at", occurredAt)
                put("payload", payload)
            }

            val eventsArray = buildJsonArray {
                add(event)
            }

            val outbox = OutboxEntity(
                bundleId = bundleId,
                deviceId = deviceId,
                shiftId = effectiveShiftId,
                eventsJson = eventsArray.toString()
            )
            outboxDao.insert(outbox)
            outbox
        }
    }
}

data class LaundryPickupItem(
    val orderLineId: String,
    val quantity: Int,
    val isFinal: Boolean,
    val note: String? = null
)
