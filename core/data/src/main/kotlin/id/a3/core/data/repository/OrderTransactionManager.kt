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
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.data.local.entity.ShiftEntity
import id.a3.core.data.util.CorrectionReasonMapper
import id.a3.core.data.util.LaundryStatusMapper
import id.a3.core.data.util.PhoneNumberUtil
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
     * Mengamati pesanan yang sudah selesai/lunas (completed orders) khusus untuk shift tertentu.
     */
    fun observeCompletedOrdersForShift(shiftId: String): Flow<List<OrderWithLines>> =
        orderDao.observeCompletedOrdersWithLinesForShift(shiftId)

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
     * Jika pesanan kehilangan baris item (misal akibat anomali transaksi),
     * dilakukan perbaikan otomatis (self-healing) dari outbox / fallback.
     */
    suspend fun getOrderWithLines(orderId: String): OrderWithLines? {
        val orderWithLines = orderDao.getOrderWithLines(orderId) ?: return null
        if (orderWithLines.lines.isEmpty()) {
            val repaired = repairMissingOrderLines(orderId)
            if (repaired.isNotEmpty()) {
                return orderWithLines.copy(lines = repaired)
            }
        }
        return orderWithLines
    }

    /**
     * Memulihkan order_lines yang hilang akibat anomali transaksi / cascade delete lama.
     * Pertama, mencari payload order.created di tabel outbox lokal.
     * Jika tidak ditemukan, membuat 1 baris fallback proporsional sesuai nilai subtotal pesanan.
     */
    suspend fun repairMissingOrderLines(orderId: String): List<OrderLineEntity> {
        val existingLines = orderDao.getLinesForOrder(orderId)
        if (existingLines.isNotEmpty()) {
            return existingLines
        }

        val order = orderDao.getOrderById(orderId) ?: return emptyList()
        val recoveredLines = mutableListOf<OrderLineEntity>()

        // 1. Coba pulihkan dari outbox bundle order.created
        val bundles = outboxDao.findBundlesByOrderId(orderId)
        for (bundle in bundles) {
            try {
                val eventsArray = Json.parseToJsonElement(bundle.eventsJson).jsonArray
                for (event in eventsArray) {
                    val evObj = event.jsonObject
                    val type = evObj["type"]?.jsonPrimitive?.content
                    if (type == "order.created") {
                        val payload = evObj["payload"]?.jsonObject ?: continue
                        val payloadOrderId = payload["order_id"]?.jsonPrimitive?.content
                        if (payloadOrderId == orderId) {
                            val linesArray = payload["lines"]?.jsonArray
                            if (linesArray != null && linesArray.isNotEmpty()) {
                                for (l in linesArray) {
                                    val lineObj = l.jsonObject
                                    val lineId = lineObj["line_id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                                    val itemRef = lineObj["item_ref_id"]?.jsonPrimitive?.content ?: "svc-recovered"
                                    val name = lineObj["name"]?.jsonPrimitive?.content ?: "Layanan Laundry"
                                    val qty = lineObj["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                                    val weightGrams = lineObj["weight_grams"]?.jsonPrimitive?.doubleOrNull
                                    val weightKg = weightGrams?.let { it / 1000.0 } ?: lineObj["weight"]?.jsonPrimitive?.doubleOrNull
                                    val price = lineObj["unit_price"]?.jsonPrimitive?.longOrNull
                                        ?: lineObj["rate"]?.jsonPrimitive?.longOrNull
                                        ?: 0L
                                    val note = lineObj["note"]?.jsonPrimitive?.contentOrNull

                                    recoveredLines.add(
                                        OrderLineEntity(
                                            id = lineId,
                                            orderId = orderId,
                                            itemRefId = itemRef,
                                            nameSnapshot = name,
                                            quantity = qty,
                                            weight = weightKg,
                                            unitPriceSnapshot = price,
                                            note = note
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Abaikan kesalahan parsing bundle individual
            }
            if (recoveredLines.isNotEmpty()) break
        }

        // 2. Jika outbox tidak ditemukan / sudah terhapus, buat baris fallback sintetis
        if (recoveredLines.isEmpty()) {
            val fallbackLine = OrderLineEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                itemRefId = "svc-recovery",
                nameSnapshot = "Layanan Laundry (${order.displayNumber})",
                quantity = 1,
                weight = null,
                unitPriceSnapshot = order.subtotal,
                note = "Dipulihkan otomatis oleh sistem"
            )
            recoveredLines.add(fallbackLine)
        }

        orderDao.insertLines(recoveredLines)
        return recoveredLines
    }

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
            val existingOrder = orderDao.getOrderById(orderId)
            val minInstant = existingOrder?.let { Instant.ofEpochMilli(it.createdAt).plusMillis(1000) }
            val nowInstant = Instant.now()
            val occurredAt = if (minInstant != null && nowInstant.isBefore(minInstant)) {
                minInstant.toString()
            } else {
                nowInstant.toString()
            }

            val payload = buildJsonObject {
                put("order_id", orderId)
                put("from_table", fromTable)
                put("to_table", toTable)
                put("table_label", toTable)
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
        shiftId: String? = null,
        discountPresetName: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val activeShift = shiftDao.getActiveShift()
            val effectiveShiftId = shiftId ?: order.shiftId ?: activeShift?.id
            val finalOrder = if (order.shiftId.isNullOrBlank() && effectiveShiftId != null) {
                order.copy(shiftId = effectiveShiftId)
            } else {
                order
            }
            orderDao.insertOrder(finalOrder.copy(status = "open"))
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
                            put("item_ref_id", line.itemRefId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(line.itemRefId))
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            put("options", buildJsonArray {})
                            line.note?.let { put("note", it) }
                        }
                    )
                }
            }

            val normalizedOrderType = when (order.orderType.lowercase()) {
                "take_away" -> "takeaway"
                else -> order.orderType.lowercase()
            }

            val createdPayload = buildJsonObject {
                put("order_id", order.id)
                put("display_number", order.displayNumber)
                put("order_type", normalizedOrderType)
                val effectiveTable = (order.tableLabel?.trim()?.takeIf { it.isNotEmpty() } ?: if (normalizedOrderType == "takeaway") "Bungkus" else "Meja 01").take(64)
                put("table_label", effectiveTable)
                if (order.discount > 0L) {
                    val resolvedPreset = discountPresetName?.takeIf { it.isNotBlank() } ?: when {
                        order.subtotal > 0L && order.discount == (order.subtotal * 10 / 100) -> "Diskon 10%"
                        order.subtotal > 0L && order.discount == (order.subtotal * 5 / 100) -> "Diskon 5%"
                        order.discount == 5000L -> "Potongan Rp5.000"
                        else -> "Diskon 10%"
                    }
                    put("discount_rule", buildJsonObject {
                        put("preset", resolvedPreset)
                    })
                }
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
                shiftId = effectiveShiftId,
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
        shiftId: String? = null,
        discountPresetName: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val currentOrder = orderDao.getOrderById(orderId)
            val effectiveShiftId = shiftId ?: currentOrder?.shiftId ?: shiftDao.getActiveShift()?.id
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
                            put("item_ref_id", line.itemRefId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(line.itemRefId))
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            put("options", buildJsonArray {})
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
                if (newDiscount > 0L) {
                    val resolvedPreset = discountPresetName?.takeIf { it.isNotBlank() } ?: when {
                        newSubtotal > 0L && newDiscount == (newSubtotal * 10 / 100) -> "Diskon 10%"
                        newSubtotal > 0L && newDiscount == (newSubtotal * 5 / 100) -> "Diskon 5%"
                        newDiscount == 5000L -> "Potongan Rp5.000"
                        else -> "Diskon 10%"
                    }
                    put("discount_rule", buildJsonObject {
                        put("preset", resolvedPreset)
                    })
                }
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
                shiftId = effectiveShiftId,
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

            val activeShift = shiftDao.getActiveShift()
            val effectiveShiftId = shiftId ?: activeShift?.id ?: currentOrder.shiftId

            orderDao.updatePaymentStatus(
                orderId = orderId,
                status = newStatus,
                paidAmount = newPaidAmount,
                shiftId = effectiveShiftId
            )

            val paymentEntity = PaymentEntity(
                id = paymentId,
                orderId = orderId,
                method = method,
                amount = amount,
                tendered = tendered,
                idempotencyKey = idempotencyKey,
                shiftId = effectiveShiftId,
                isDp = isDp
            )
            paymentDao.insertPayment(paymentEntity)

            val bundleId = UUID.randomUUID().toString()
            val occurredAt = Instant.now().toString()

            val isPartialDp = isDp || (newPaidAmount < currentOrder.total)
            val paymentType = when {
                isDp -> "dp"
                currentOrder.paidAmount > 0L -> "settlement"
                newPaidAmount >= currentOrder.total -> "full"
                else -> "dp"
            }

            val paymentPayload = buildJsonObject {
                put("payment_id", paymentId)
                put("order_id", orderId)
                put("method", method)
                put("amount", amount)
                tendered?.let { put("tendered", it) }
                put("idempotency_key", idempotencyKey)
                put("is_dp", isPartialDp)
                put("payment_type", paymentType)
            }

            val paymentEvent = buildJsonObject {
                put("event_id", UUID.randomUUID().toString())
                put("type", "payment.recorded")
                put("occurred_at", occurredAt)
                put("payload", paymentPayload)
            }

            val effectiveDeviceId = currentOrder.deviceId.ifBlank { deviceId }

            // Poin 2: Penyatuan Transaksi Atomik Outbox
            // Jika bundel order.created untuk pesanan ini masih berstatus PENDING di outbox lokal,
            // gabungkan event payment.recorded ke dalam bundel atomik yang sama ([order.created, payment.recorded]).
            val pendingBundles = outboxDao.findBundlesByOrderId(orderId)
                .filter { it.status == OutboxStatus.PENDING }
            val pendingOrderCreatedBundle = pendingBundles.firstOrNull { bundle ->
                bundle.eventsJson.contains(""""type":"order.created"""")
            }

            if (pendingOrderCreatedBundle != null) {
                val existingEventsList = Json.parseToJsonElement(pendingOrderCreatedBundle.eventsJson).jsonArray.toMutableList()
                existingEventsList.add(paymentEvent)
                val updatedEventsJson = buildJsonArray {
                    existingEventsList.forEach { add(it) }
                }.toString()
                outboxDao.updateEventsJson(pendingOrderCreatedBundle.bundleId, updatedEventsJson)
                if (effectiveShiftId != null && effectiveShiftId != pendingOrderCreatedBundle.shiftId) {
                    outboxDao.updateShiftId(pendingOrderCreatedBundle.bundleId, effectiveShiftId)
                }
                pendingOrderCreatedBundle.copy(
                    eventsJson = updatedEventsJson,
                    shiftId = effectiveShiftId ?: pendingOrderCreatedBundle.shiftId
                )
            } else {
                val eventsArray = buildJsonArray {
                    add(paymentEvent)
                }

                val outbox = OutboxEntity(
                    bundleId = bundleId,
                    deviceId = effectiveDeviceId,
                    shiftId = effectiveShiftId,
                    eventsJson = eventsArray.toString()
                )
                outboxDao.insert(outbox)
                outbox
            }
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
        shiftId: String? = null,
        discountPresetName: String? = null
    ): OutboxEntity {
        return database.withTransaction {
            val activeShift = shiftDao.getActiveShift()
            val effectiveShiftId = shiftId ?: activeShift?.id ?: order.shiftId
            val paidOrder = order.copy(
                status = "paid",
                paidAmount = amount,
                shiftId = effectiveShiftId
            )
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
                shiftId = effectiveShiftId,
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
                            put("item_ref_id", line.itemRefId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(line.itemRefId))
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            put("options", buildJsonArray {})
                            line.note?.let { put("note", it) }
                        }
                    )
                }
            }

            val normalizedOrderType = when (paidOrder.orderType.lowercase()) {
                "take_away" -> "takeaway"
                else -> paidOrder.orderType.lowercase()
            }

            val createdPayload = buildJsonObject {
                put("order_id", paidOrder.id)
                put("display_number", paidOrder.displayNumber)
                put("order_type", normalizedOrderType)
                val effectiveTable = (paidOrder.tableLabel?.trim()?.takeIf { it.isNotEmpty() } ?: if (normalizedOrderType == "takeaway") "Bungkus" else "Meja 01").take(64)
                put("table_label", effectiveTable)
                if (paidOrder.discount > 0L) {
                    val resolvedPreset = discountPresetName?.takeIf { it.isNotBlank() } ?: when {
                        paidOrder.subtotal > 0L && paidOrder.discount == (paidOrder.subtotal * 10 / 100) -> "Diskon 10%"
                        paidOrder.subtotal > 0L && paidOrder.discount == (paidOrder.subtotal * 5 / 100) -> "Diskon 5%"
                        paidOrder.discount == 5000L -> "Potongan Rp5.000"
                        else -> "Diskon 10%"
                    }
                    put("discount_rule", buildJsonObject {
                        put("preset", resolvedPreset)
                    })
                }
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
                put("is_dp", false)
                put("payment_type", "full")
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
                shiftId = effectiveShiftId,
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
                        put("phone", PhoneNumberUtil.normalize(customer.phone))
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
                                val weightVal = line.weight
                                val isKg = (weightVal != null && weightVal > 0.0) ||
                                    (!line.nameSnapshot.contains("pcs", ignoreCase = true) && line.nameSnapshot.contains("kg", ignoreCase = true))
                                val numericRef = line.itemRefId.toLongOrNull() ?: when (line.itemRefId) {
                                    "svc-kiloan" -> 1L
                                    "svc-cuci-saja" -> 2L
                                    "svc-setrika-saja" -> 3L
                                    "svc-selimut" -> 4L
                                    "svc-bedcover" -> 5L
                                    "svc-jas" -> 6L
                                    "svc-sepatu" -> 7L
                                    else -> if (isKg) 1L else 4L
                                }
                                put("item_ref_id", numericRef)
                                put("name", line.nameSnapshot)
                                if (isKg) {
                                    val grams = line.weight?.let { Math.round(it * 1000.0) } ?: 1000L
                                    put("unit", "kg")
                                    put("weight_grams", grams)
                                    put("express", line.nameSnapshot.contains("express", ignoreCase = true))
                                    put("rate", line.unitPriceSnapshot)
                                    val calcLineTotal = line.weight?.let { Math.round(it * line.unitPriceSnapshot) } ?: line.unitPriceSnapshot
                                    put("line_total", if (lines.size == 1) finalOrder.subtotal else calcLineTotal)
                                } else {
                                    put("unit", "pcs")
                                    put("rate", line.unitPriceSnapshot)
                                    put("line_total", line.quantity.toLong() * line.unitPriceSnapshot)
                                }
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
                    // Aturan Server: payload.customer dan payload.customer_id TIDAK BOLEH BERSAMAAN.
                    // Pelanggan baru dikirim inline via 'customer', sedangkan pelanggan lama dikirim via 'customer_id'.
                    if (customer != null) {
                        if (isNewCustomer) {
                            put("customer", buildJsonObject {
                                put("name", customer.name)
                                put("phone", PhoneNumberUtil.normalize(customer.phone))
                            })
                        } else {
                            finalOrder.customerId?.let { put("customer_id", it) }
                        }
                    }
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
        reworkApprovedBy: String? = null,
        userId: String? = null
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

            val openApiStatus = LaundryStatusMapper.toOpenApi(targetStatus)
            val backendStatus = LaundryStatusMapper.toBackend(targetStatus)
            val numericUserId = LaundryStatusMapper.normalizeNumericUserId(userId) ?: 4L
            val numericApprovedBy = reworkApprovedBy?.let { LaundryStatusMapper.normalizeNumericUserId(it) }

            val payload = buildJsonObject {
                put("order_id", orderId)
                put("status", openApiStatus)
                put("backend_status", backendStatus)
                put("user_id", JsonPrimitive(numericUserId))
                if (rework) {
                    put("rework", true)
                    reworkReason?.let { put("reason", it) }
                    reworkApprovedBy?.let {
                        put("approved_by", it)
                        if (numericApprovedBy != null) {
                            put("approved_by_id", JsonPrimitive(numericApprovedBy))
                        }
                    }
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
     * - Menyusun event 'laundry.status_changed' (status: diambil) ke antrean outbox secara atomik tanpa shift_id.
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

            // 3. Susun event outbox laundry.status_changed untuk tiap item pengambilan (sesuai Laravel 12 API 4.9)
            val eventsArray = buildJsonArray {
                items.forEach { item ->
                    val numericUserId = LaundryStatusMapper.normalizeNumericUserId(userId) ?: 4L
                    val numericApprovedBy = approvedBy?.let { LaundryStatusMapper.normalizeNumericUserId(it) }
                    val payload = buildJsonObject {
                        put("order_id", orderId)
                        put("status", "diambil")
                        put("backend_status", "diambil")
                        put("user_id", JsonPrimitive(numericUserId))
                        put("line_ids", buildJsonArray { add(JsonPrimitive(item.orderLineId)) })
                        put("order_line_id", item.orderLineId)
                        put("quantity", item.quantity)
                        put("is_final", item.isFinal)
                        item.note?.let { put("note", it) }
                        if (approvedBy != null) {
                            if (numericApprovedBy != null) {
                                put("approved_by", JsonPrimitive(numericApprovedBy))
                                put("approved_by_id", JsonPrimitive(numericApprovedBy))
                            } else {
                                put("approved_by", approvedBy)
                            }
                        }
                    }
                    val eventObj = buildJsonObject {
                        put("event_id", UUID.randomUUID().toString())
                        put("type", "laundry.status_changed")
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
                val numericUserId = shift.cashierId.toLongOrNull() ?: 2L
                put("user_id", JsonPrimitive(numericUserId))
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

            // Hitung kas yang seharusnya di laci (K1) sesuai rumus domain:
            // K1 = opening_cash + cash_sales + cash_dp - cash_refunds - cash_outs
            val payments = paymentDao.getPaymentsForShift(shiftId)
            val cashPayments = payments.filter { it.method == "cash" }.sumOf { it.amount }
            val corrections = correctionDao.getCorrectionsForShift(shiftId)
            val cashRefunds = corrections.filter { it.refundMethod == "cash" }.sumOf { it.amount }
            val cashOuts = cashOutDao.getCashOutsForShift(shiftId).sumOf { it.amount }
            val expectedCash = shift.openingCash + cashPayments - cashRefunds - cashOuts
            val difference = countedCash - expectedCash

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
                put("expected_cash", expectedCash)
                put("difference", difference)
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

            val numericApprovedBy = LaundryStatusMapper.normalizeNumericUserId(approvedBy) ?: 4L

            val payload = buildJsonObject {
                put("shift_id", shiftId)
                put("note", note.trim())
                put("approved_by", JsonPrimitive(numericApprovedBy))
                put("approved_by_label", approvedBy.trim())
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
        val validReasons = CorrectionReasonMapper.VALID_REASONS
        require(reasonCode in validReasons) { "Kode alasan '$reasonCode' tidak valid (COR-01)" }
        val backendReason = CorrectionReasonMapper.toBackend(reasonCode)
        if (backendReason == "lainnya" || reasonCode == "other") {
            require(!note.isNullOrBlank()) { "Alasan 'other' / 'lainnya' wajib menyertakan catatan (note)" }
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

            val backendReason = CorrectionReasonMapper.toBackend(reasonCode)
            val effectiveReasonNote = note?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (backendReason == "lainnya") "Koreksi kasir" else null

            val numericApprovedBy = when {
                approvedBy.toLongOrNull() != null -> approvedBy.toLong()
                approvedBy.contains("manager", ignoreCase = true) -> 4L
                else -> 2L
            }

            val payload = buildJsonObject {
                put("correction_id", correctionId)
                put("type", type)
                put("order_id", orderId)
                if (orderLineIds.isNotEmpty()) {
                    put("order_line_id", orderLineIds.first())
                } else {
                    put("order_line_id", JsonNull)
                }
                put("order_line_ids", lineIdsJsonArray)
                if (originalPaymentId != null) {
                    put("payment_id", originalPaymentId)
                    put("original_payment_id", originalPaymentId)
                } else {
                    put("payment_id", JsonNull)
                    put("original_payment_id", JsonNull)
                }
                put("amount", amount)
                if (type == "refund") {
                    put("refund_method", "cash")
                }
                put("reason_code", backendReason)
                if (effectiveReasonNote != null) {
                    put("reason_note", effectiveReasonNote)
                    put("note", effectiveReasonNote)
                } else {
                    put("reason_note", JsonNull)
                    put("note", JsonNull)
                }
                put("approved_by", JsonPrimitive(numericApprovedBy))
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
        val effectiveDeviceId = if (deviceId != "dev-1") deviceId else activeShift?.deviceId?.takeIf { it.isNotBlank() } ?: deviceId

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
                if (receiptFileId != null) {
                    put("receipt_file_id", receiptFileId)
                } else {
                    put("receipt_file_id", JsonNull)
                }
                if (approvedBy != null) {
                    put("approved_by", approvedBy)
                } else {
                    put("approved_by", JsonNull)
                }
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
                deviceId = effectiveDeviceId,
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
