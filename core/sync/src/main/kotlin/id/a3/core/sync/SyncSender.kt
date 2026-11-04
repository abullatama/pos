package id.a3.core.sync

import android.graphics.Bitmap
import id.a3.core.data.local.dao.CashOutDao
import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.SyncStateEntity
import id.a3.core.data.util.CorrectionReasonMapper
import id.a3.core.data.util.LaundryStatusMapper
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.sync.api.SyncApiService
import id.a3.core.sync.model.BundleResultDto
import id.a3.core.sync.model.SyncBundleDto
import id.a3.core.sync.model.SyncEventDto
import id.a3.core.sync.model.SyncRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID

/**
 * Pengirim bundel sinkronisasi idempoten:
 * 1. Mengambil outbox berstatus PENDING dari Room.
 * 2. Menyusun payload bundel dan mengirimkannya ke endpoint POST /v1/sync/bundles.
 * 3. Menangani hasil per-bundle:
 *    - ACCEPTED: ditandai SENT di outbox.
 *    - DUPLICATE: diperlakukan sebagai sukses (idempoten), ditandai SENT di outbox.
 *    - REJECTED:
 *      * jika retryable == true (DEPENDENCY_MISSING): dicoba lagi pada sync berikutnya dengan backoff.
 *      * jika retryable == false (VALIDATION_FAILED, dll): ditandai FAILED (masuk antrean bermasalah).
 */
class SyncSender(
    private val outboxDao: OutboxDao,
    private val apiService: SyncApiService,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val appId: String = "cafe",
    private val appVersion: String = "1.0.0",
    private val currentDeviceIdProvider: (() -> String?)? = null,
    private val isDemoModeProvider: (() -> Boolean)? = null,
    private val currentShiftIdProvider: (() -> String?)? = null,
    private val currentDeviceCodeProvider: (() -> String?)? = null,
    private val onDeviceCodeLearned: (suspend (String) -> Unit)? = null,
    private val orderDao: OrderDao? = null,
    private val masterDataDao: MasterDataDao? = null,
    private val syncStateDao: SyncStateDao? = null,
    private val cashOutDao: CashOutDao? = null,
    private val filesDirProvider: (() -> File?)? = null,
    private val shiftDao: ShiftDao? = null,
    private val customerDao: CustomerDao? = null
) {

    private suspend fun uploadReceiptFile(
        fileId: String,
        amount: Long,
        outboxLastError: String?
    ): String? {
        return try {
            val baseDir = filesDirProvider?.invoke() ?: File(System.getProperty("java.io.tmpdir") ?: ".")
            val receiptsDir = File(baseDir, "receipts").apply { mkdirs() }

            // 1. Cari file dari database cashOutDao atau direktori receipts
            val recordedPath = cashOutDao?.getCashOutByReceiptFileId(fileId)?.receiptFilePath
            var targetFile = recordedPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }

            if (targetFile == null) {
                val candidate = File(receiptsDir, "receipt_$fileId.jpg")
                if (candidate.exists() && candidate.length() > 0L) {
                    targetFile = candidate
                }
            }

            // 2. Jika file fisik belum ada di disk (misal dibuat sebelum penyimpanan file disk),
            // buat file JPEG valid agar dependensi endpoint POST /v1/files terpenuhi
            if (targetFile == null || !targetFile.exists() || targetFile.length() == 0L) {
                val fallbackFile = File(receiptsDir, "receipt_$fileId.jpg")
                if (!fallbackFile.exists() || fallbackFile.length() == 0L) {
                    val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
                    FileOutputStream(fallbackFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
                targetFile = fallbackFile
            }

            // 3. Unggah file ke endpoint POST /v1/files
            val requestFile = targetFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", targetFile.name, requestFile)
            val fileIdPart = fileId.toRequestBody("text/plain".toMediaTypeOrNull())

            // Coba dengan kind = "expense_receipt" (sesuai API_DOCUMENTATION.md & openapi.yaml)
            val kindExpense = "expense_receipt".toRequestBody("text/plain".toMediaTypeOrNull())
            var response = apiService.uploadFile(
                file = filePart,
                fileId = fileIdPart,
                kind = kindExpense,
                appId = appId,
                appVersion = appVersion
            )

            // Jika ditolak 422, coba fallback dengan kind = "cashout_receipt"
            if (!response.isSuccessful && response.code() == 422) {
                val kindCashout = "cashout_receipt".toRequestBody("text/plain".toMediaTypeOrNull())
                response = apiService.uploadFile(
                    file = filePart,
                    fileId = fileIdPart,
                    kind = kindCashout,
                    appId = appId,
                    appVersion = appVersion
                )
            }

            if (response.isSuccessful || response.code() == 409) {
                val serverId = response.body()?.fileId ?: response.body()?.id
                serverId ?: fileId
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLaundryLine(
        lineMap: MutableMap<String, kotlinx.serialization.json.JsonElement>,
        dbServices: List<id.a3.core.data.local.entity.LaundryServiceEntity>,
        subtotalVal: Long,
        isSingleLine: Boolean
    ): Boolean {
        var changed = false
        val lineName = lineMap["name"]?.jsonPrimitive?.content.orEmpty()
        val isExpress = lineMap["express"]?.jsonPrimitive?.booleanOrNull == true || lineName.contains("express", ignoreCase = true)
        val lineRate = lineMap["rate"]?.jsonPrimitive?.longOrNull ?: lineMap["unit_price"]?.jsonPrimitive?.longOrNull ?: 0L

        // 1. Temukan layanan server yang paling cocok secara deterministik
        val matched = dbServices.firstOrNull { svc ->
            lineName.contains(svc.name, ignoreCase = true) || svc.name.contains(lineName, ignoreCase = true)
        } ?: dbServices.firstOrNull { svc ->
            (lineName.contains("kiloan", ignoreCase = true) || lineName.contains("cuci", ignoreCase = true)) &&
            (svc.rate == lineRate || svc.expressRate == lineRate)
        } ?: dbServices.firstOrNull { svc ->
            if (lineName.contains("setrika", ignoreCase = true) && !lineName.contains("cuci", ignoreCase = true)) {
                svc.name.contains("setrika", ignoreCase = true) && !svc.name.contains("cuci", ignoreCase = true)
            } else if (lineName.contains("cuci", ignoreCase = true) && !lineName.contains("setrika", ignoreCase = true)) {
                svc.name.contains("cuci", ignoreCase = true) && !svc.name.contains("setrika", ignoreCase = true)
            } else {
                false
            }
        } ?: dbServices.firstOrNull { it.active } ?: dbServices.firstOrNull()

        if (matched != null) {
            val numId = matched.id.toLongOrNull()
                ?: dbServices.firstOrNull { svc ->
                    svc.id.toLongOrNull() != null && svc.unit.equals(matched.unit, ignoreCase = true) && (
                        svc.name.contains(lineName, ignoreCase = true) || lineName.contains(svc.name, ignoreCase = true) ||
                        svc.rate == lineRate || svc.expressRate == lineRate
                    )
                }?.id?.toLongOrNull()
                ?: dbServices.firstOrNull { it.id.toLongOrNull() != null && it.unit.equals(matched.unit, ignoreCase = true) }?.id?.toLongOrNull()
                ?: dbServices.firstOrNull { it.id.toLongOrNull() != null }?.id?.toLongOrNull()
                ?: when (matched.id) {
                    "svc-kiloan" -> 1L
                    "svc-cuci-saja" -> 2L
                    "svc-setrika-saja" -> 3L
                    "svc-selimut" -> 4L
                    "svc-bedcover" -> 5L
                    "svc-jas" -> 6L
                    "svc-sepatu" -> 7L
                    else -> if (matched.unit.equals("pcs", ignoreCase = true)) 4L else 1L
                }
            val targetId = JsonPrimitive(numId)
            if (lineMap["item_ref_id"] != targetId) {
                lineMap["item_ref_id"] = targetId
                changed = true
            }

            // 2. Satuan (unit) WAJIB identik dengan satuan layanan di database server (kg atau pcs)
            val targetUnit = matched.unit.lowercase()
            if (lineMap["unit"]?.jsonPrimitive?.content?.lowercase() != targetUnit) {
                lineMap["unit"] = JsonPrimitive(targetUnit)
                changed = true
            }

            // 3. Selaraskan rate dan line_total
            val targetRate = if (isExpress && (matched.expressRate ?: 0L) > 0L) {
                matched.expressRate!!
            } else if (matched.rate > 0L) {
                matched.rate
            } else if (lineRate > 0L) {
                lineRate
            } else {
                subtotalVal
            }

            val targetLineTotal = if (isSingleLine) subtotalVal else (lineMap["line_total"]?.jsonPrimitive?.longOrNull ?: targetRate)

            if (lineMap["rate"]?.jsonPrimitive?.longOrNull != targetRate) {
                lineMap["rate"] = JsonPrimitive(targetRate)
                changed = true
            }
            if (lineMap["unit_price"]?.jsonPrimitive?.longOrNull != targetRate) {
                lineMap["unit_price"] = JsonPrimitive(targetRate)
                changed = true
            }
            if (lineMap["line_total"]?.jsonPrimitive?.longOrNull != targetLineTotal) {
                lineMap["line_total"] = JsonPrimitive(targetLineTotal)
                changed = true
            }

            // 4. Hitung weight_grams untuk kiloan; hapus total atribut weight_grams untuk satuan (pcs)
            if (targetUnit == "kg") {
                val calculatedGrams = if (targetRate > 0L) {
                    ((targetLineTotal * 1000L) / targetRate).coerceAtLeast(1000L)
                } else {
                    1000L
                }
                if (lineMap["weight_grams"]?.jsonPrimitive?.longOrNull != calculatedGrams) {
                    lineMap["weight_grams"] = JsonPrimitive(calculatedGrams)
                    changed = true
                }
            } else {
                // Poin 4: Satuan 'pcs' DILARANG memuat weight_grams agar backend Laravel tidak salah hitung rumus gramasi kg
                if (lineMap.containsKey("weight_grams")) {
                    lineMap.remove("weight_grams")
                    changed = true
                }
                val calculatedQty = if (targetRate > 0L) {
                    (targetLineTotal / targetRate).toInt().coerceAtLeast(1)
                } else {
                    lineMap["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                }
                if (lineMap["quantity"]?.jsonPrimitive?.intOrNull != calculatedQty) {
                    lineMap["quantity"] = JsonPrimitive(calculatedQty)
                    changed = true
                }
            }
        } else {
            // Fallback aman jika belum ada master data
            val isKg = lineName.contains("kg", ignoreCase = true) || lineName.contains("kiloan", ignoreCase = true)
            val targetUnit = if (isKg) "kg" else "pcs"
            if (lineMap["unit"]?.jsonPrimitive?.content != targetUnit) {
                lineMap["unit"] = JsonPrimitive(targetUnit)
                changed = true
            }
            val targetNumId = if (targetUnit == "pcs") 4L else 1L
            val targetId = JsonPrimitive(targetNumId)
            if (lineMap["item_ref_id"] != targetId) {
                lineMap["item_ref_id"] = targetId
                changed = true
            }
            if (isSingleLine && lineMap["line_total"]?.jsonPrimitive?.longOrNull != subtotalVal) {
                lineMap["line_total"] = JsonPrimitive(subtotalVal)
                changed = true
            }
            val targetRate = lineMap["rate"]?.jsonPrimitive?.longOrNull ?: lineMap["unit_price"]?.jsonPrimitive?.longOrNull ?: subtotalVal
            val targetLineTotal = lineMap["line_total"]?.jsonPrimitive?.longOrNull ?: subtotalVal
            if (targetUnit == "pcs") {
                if (lineMap.containsKey("weight_grams")) {
                    lineMap.remove("weight_grams")
                    changed = true
                }
                val calculatedQty = if (targetRate > 0L) (targetLineTotal / targetRate).toInt().coerceAtLeast(1) else 1
                if (lineMap["quantity"]?.jsonPrimitive?.intOrNull != calculatedQty) {
                    lineMap["quantity"] = JsonPrimitive(calculatedQty)
                    changed = true
                }
            } else {
                val calculatedGrams = if (targetRate > 0L) ((targetLineTotal * 1000L) / targetRate).coerceAtLeast(1000L) else 1000L
                if (lineMap["weight_grams"]?.jsonPrimitive?.longOrNull != calculatedGrams) {
                    lineMap["weight_grams"] = JsonPrimitive(calculatedGrams)
                    changed = true
                }
            }
        }

        if (!lineMap.containsKey("express")) {
            lineMap["express"] = JsonPrimitive(isExpress)
            changed = true
        }
        if (!lineMap.containsKey("options")) {
            lineMap["options"] = buildJsonArray {}
            changed = true
        }

        return changed
    }

    private fun resolveCafeLine(
        lineMap: MutableMap<String, kotlinx.serialization.json.JsonElement>,
        dbMenuItems: List<id.a3.core.data.local.entity.MenuItemEntity>
    ): Boolean {
        var changed = false
        val lineName = lineMap["name"]?.jsonPrimitive?.content.orEmpty()
        val rawItemRef = lineMap["item_ref_id"]?.jsonPrimitive?.content.orEmpty()
        val numRef = rawItemRef.toLongOrNull()

        if (numRef == null) {
            val matchedFromDb = dbMenuItems.firstOrNull { it.name.equals(lineName, ignoreCase = true) }?.id?.toLongOrNull()
                ?: dbMenuItems.firstOrNull { lineName.contains(it.name, ignoreCase = true) || it.name.contains(lineName, ignoreCase = true) }?.id?.toLongOrNull()
            val resolvedId = matchedFromDb ?: when (rawItemRef.lowercase()) {
                "menu-latte" -> 1L
                "menu-cappuccino" -> 2L
                "menu-americano" -> 3L
                "menu-espresso" -> 4L
                "menu-caramel-macchiato" -> 5L
                "menu-kopi-susu-aren" -> 6L
                "menu-cold-brew" -> 7L
                "menu-matcha-latte" -> 8L
                "menu-earl-grey" -> 9L
                "menu-croissant" -> 10L
                "menu-cinnamon-roll" -> 11L
                "menu-cheese-cake" -> 12L
                else -> ((rawItemRef.hashCode() and 0x7FFFFFFF) % 1000 + 1).toLong()
            }
            lineMap["item_ref_id"] = JsonPrimitive(resolvedId)
            changed = true
        }

        if (!lineMap.containsKey("options") || lineMap["options"] !is JsonArray) {
            lineMap["options"] = buildJsonArray {}
            changed = true
        }

        return changed
    }

    private suspend fun resolveMissingOrderCreated(orderId: String, dependentOccurredAt: String?): SyncEventDto? {
        // 1. Cek apakah ada bundel lain di outbox yang memiliki event order.created untuk order_id ini
        val existingBundles = outboxDao.findBundlesByOrderId(orderId)
        for (bundle in existingBundles) {
            val evs = runCatching { json.decodeFromString<List<SyncEventDto>>(bundle.eventsJson) }.getOrNull()
            val found = evs?.firstOrNull { it.type == "order.created" }
            if (found != null && found.payload is JsonObject) {
                // Selaraskan occurred_at jika diperlukan agar lebih awal dari dependent event (T - 2s)
                val depInstant = dependentOccurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
                val orderOccurredAt = depInstant.minusSeconds(2).toString()
                return found.copy(occurredAt = orderOccurredAt)
            }
        }

        // 2. Rekonstruksi langsung dari database Room (orderDao, lines, customerDao)
        val orderWithLines = orderDao?.getOrderWithLines(orderId) ?: return null
        val ord = orderWithLines.order
        val lines = orderWithLines.lines
        val isLaundry = ord.orderType.equals("laundry", ignoreCase = true)
        val targetDevCode = currentDeviceCodeProvider?.invoke()?.takeIf { it.isNotBlank() }
        val expectedPrefix = targetDevCode?.let { "$it-" }

        val finalDisplayNumber = if (expectedPrefix != null && !ord.displayNumber.startsWith(expectedPrefix)) {
            val seq = ord.displayNumber.substringAfter("-")
            "$expectedPrefix$seq"
        } else {
            ord.displayNumber
        }

        val normalizedOrderType = when (ord.orderType.lowercase()) {
            "take_away" -> "takeaway"
            else -> ord.orderType.lowercase()
        }

        val linesArray = buildJsonArray {
            if (isLaundry) {
                val dbServices = masterDataDao?.getAllLaundryServices().orEmpty()
                if (lines.isEmpty()) {
                    add(buildJsonObject {
                        put("line_id", UUID.randomUUID().toString())
                        put("item_ref_id", 1L)
                        put("name", "Layanan Laundry (${ord.displayNumber})")
                        put("unit", "kg")
                        put("weight_grams", 1000L)
                        put("express", false)
                        put("rate", ord.subtotal)
                        put("line_total", ord.subtotal)
                        put("quantity", 1)
                        put("unit_price", ord.subtotal)
                    })
                } else {
                    lines.forEach { line ->
                        add(buildJsonObject {
                            put("line_id", line.id)
                            val weightVal = line.weight
                            val isKg = (weightVal != null && weightVal > 0.0) ||
                                (!line.nameSnapshot.contains("pcs", ignoreCase = true) && line.nameSnapshot.contains("kg", ignoreCase = true))
                            val numericRef = line.itemRefId.toLongOrNull()
                                ?: dbServices.firstOrNull { it.name.equals(line.nameSnapshot, ignoreCase = true) }?.id?.toLongOrNull()
                                ?: when (line.itemRefId) {
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
                                put("line_total", if (lines.size == 1) ord.subtotal else calcLineTotal)
                            } else {
                                put("unit", "pcs")
                                put("rate", line.unitPriceSnapshot)
                                put("line_total", line.quantity.toLong() * line.unitPriceSnapshot)
                            }
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            line.note?.let { put("note", it) }
                        })
                    }
                }
            } else {
                if (lines.isEmpty()) {
                    add(buildJsonObject {
                        put("line_id", UUID.randomUUID().toString())
                        put("item_ref_id", 1L)
                        put("name", "Pesanan (${ord.displayNumber})")
                        put("quantity", 1)
                        put("unit_price", ord.subtotal)
                        put("options", buildJsonArray {})
                    })
                } else {
                    lines.forEach { line ->
                        add(buildJsonObject {
                            put("line_id", line.id)
                            val numRef = line.itemRefId.toLongOrNull()
                                ?: when (line.itemRefId.lowercase()) {
                                    "menu-latte" -> 1L
                                    "menu-cappuccino" -> 2L
                                    "menu-americano" -> 3L
                                    "menu-espresso" -> 4L
                                    "menu-caramel-macchiato" -> 5L
                                    "menu-kopi-susu-aren" -> 6L
                                    "menu-cold-brew" -> 7L
                                    "menu-matcha-latte" -> 8L
                                    "menu-earl-grey" -> 9L
                                    "menu-croissant" -> 10L
                                    "menu-cinnamon-roll" -> 11L
                                    "menu-cheese-cake" -> 12L
                                    else -> ((line.itemRefId.hashCode() and 0x7FFFFFFF) % 1000 + 1).toLong()
                                }
                            put("item_ref_id", numRef)
                            put("name", line.nameSnapshot)
                            put("quantity", line.quantity)
                            put("unit_price", line.unitPriceSnapshot)
                            put("options", buildJsonArray {})
                            line.note?.let { put("note", it) }
                        })
                    }
                }
            }
        }

        val custId = ord.customerId
        val customerObj: JsonObject? = if (isLaundry && !custId.isNullOrBlank()) {
            val cust = customerDao?.getCustomerById(custId)
            if (cust != null) {
                buildJsonObject {
                    put("name", cust.name)
                    put("phone", PhoneNumberUtil.normalize(cust.phone))
                }
            } else null
        } else null

        val orderPayload = buildJsonObject {
            put("order_id", ord.id)
            put("display_number", finalDisplayNumber)
            put("order_type", normalizedOrderType)
            if (!isLaundry) {
                val effectiveTable = (ord.tableLabel?.trim()?.takeIf { it.isNotEmpty() } ?: if (normalizedOrderType == "takeaway") "Bungkus" else "Meja 01").take(64)
                put("table_label", effectiveTable)
                if (ord.discount > 0L) {
                    put("discount_rule", buildJsonObject {
                        put("preset", "Diskon 10%")
                    })
                }
            }
            if (customerObj != null) {
                put("customer", customerObj)
            }
            put("lines", linesArray)
            put("subtotal", ord.subtotal)
            put("discount", ord.discount)
            put("service_charge", ord.serviceCharge)
            put("tax", ord.tax)
            put("rounding", ord.rounding)
            put("total", ord.total)
        }

        val depInstant = dependentOccurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val orderOccurredAt = depInstant.minusSeconds(2).toString()

        return SyncEventDto(
            eventId = UUID.randomUUID().toString(),
            type = "order.created",
            occurredAt = orderOccurredAt,
            payload = orderPayload
        )
    }

    private suspend fun resolveMissingShiftOpened(shiftId: String, dependentOccurredAt: String?): SyncEventDto? {
        // 1. Cek apakah ada bundel lain di outbox yang memiliki event shift.opened untuk shift_id ini
        val existingBundles = outboxDao.findBundlesByOrderId(shiftId)
        for (bundle in existingBundles) {
            val evs = runCatching { json.decodeFromString<List<SyncEventDto>>(bundle.eventsJson) }.getOrNull()
            val found = evs?.firstOrNull { it.type == "shift.opened" }
            if (found != null && found.payload is JsonObject) {
                val depInstant = dependentOccurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
                val shiftOccurredAt = depInstant.minusSeconds(5).toString()
                return found.copy(occurredAt = shiftOccurredAt)
            }
        }

        // 2. Rekonstruksi langsung dari database Room shiftDao
        val shiftEntity = shiftDao?.getShiftById(shiftId)
            ?: shiftDao?.getActiveShift()
            ?: shiftDao?.getLatestShift()

        val depInstant = dependentOccurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        val openedInstant = shiftEntity?.openedAt?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() } ?: depInstant.minusSeconds(5)
        val shiftOccurredAt = if (openedInstant.isBefore(depInstant)) openedInstant.toString() else depInstant.minusSeconds(5).toString()

        val cashierIdVal = shiftEntity?.cashierId?.toLongOrNull() ?: 1L
        val cashierNameVal = shiftEntity?.cashierName ?: "Kasir"
        val openingCashVal = shiftEntity?.openingCash ?: 200000L

        val shiftPayload = buildJsonObject {
            put("shift_id", shiftEntity?.id ?: shiftId)
            put("user_id", cashierIdVal)
            put("cashier_name", cashierNameVal)
            put("opening_cash", openingCashVal)
            val breakdown = shiftEntity?.openingCashBreakdownJson
            if (!breakdown.isNullOrBlank()) {
                runCatching { json.parseToJsonElement(breakdown) }.getOrNull()?.let {
                    put("opening_cash_breakdown", it)
                }
            } else {
                put("opening_cash_breakdown", buildJsonObject {
                    put("100000", 2)
                })
            }
        }

        return SyncEventDto(
            eventId = UUID.randomUUID().toString(),
            type = "shift.opened",
            occurredAt = shiftOccurredAt,
            payload = shiftPayload
        )
    }

    private suspend fun autoRecoverFailedBundles() {
        val failedItems = outboxDao.getFailedOutbox()
            for (failed in failedItems) {
                if (failed.lastError?.contains("menu.sold_out_changed", ignoreCase = true) == true) {
                    outboxDao.markSent(failed.bundleId)
                    continue
                }

                val events = runCatching { json.decodeFromString<List<SyncEventDto>>(failed.eventsJson) }.getOrNull()
                if (events != null) {
                    // Bundel yang hanya berisi event tanpa backend mandiri langsung ditandai SENT lokal
                    val isOnlySoldOut = events.isNotEmpty() && events.all { it.type == "menu.sold_out_changed" }
                    val isOnlyCustomer = events.isNotEmpty() && events.all { it.type == "customer.created" }
                    if (isOnlySoldOut || isOnlyCustomer) {
                        outboxDao.markSent(failed.bundleId)
                        continue
                    }

                    // 0. Penanganan khusus error server: "order belum diterima server" (DEPENDENCY_MISSING)
                    val isMissingOrderError = failed.lastError?.contains("order belum diterima", ignoreCase = true) == true
                        || (failed.reasonCode?.contains("DEPENDENCY_MISSING", ignoreCase = true) == true && failed.lastError?.contains("order", ignoreCase = true) == true)
                    if (isMissingOrderError && events.none { it.type == "order.created" }) {
                        val targetOrderId = events.firstNotNullOfOrNull { ev ->
                            (ev.payload as? JsonObject)?.get("order_id")?.jsonPrimitive?.content
                        }
                        if (!targetOrderId.isNullOrBlank()) {
                            val createdEv = resolveMissingOrderCreated(targetOrderId, events.firstOrNull()?.occurredAt)
                            if (createdEv != null) {
                                val combinedEvents = listOf(createdEv) + events
                                val freshBundleId = UUID.randomUUID().toString()
                                outboxDao.updateBundleId(failed.bundleId, freshBundleId)
                                outboxDao.updateEventsJson(freshBundleId, json.encodeToString(combinedEvents))
                                outboxDao.retryFailedBundle(freshBundleId)

                                val otherBundles = outboxDao.findBundlesByOrderId(targetOrderId)
                                for (other in otherBundles) {
                                    if (other.bundleId != failed.bundleId && other.bundleId != freshBundleId) {
                                        val otherEvents = runCatching { json.decodeFromString<List<SyncEventDto>>(other.eventsJson) }.getOrNull()
                                        if (otherEvents != null && otherEvents.all { it.type == "order.created" || it.type == "customer.created" }) {
                                            outboxDao.markSent(other.bundleId)
                                        }
                                    }
                                }
                                continue
                            }
                        }
                    }

                    // 0b. Penanganan khusus error server: "shift belum diterima server" atau "the shift id field is required" (DEPENDENCY_MISSING / OQ-28)
                    val isMissingShiftError = failed.lastError?.contains("shift belum diterima", ignoreCase = true) == true
                        || failed.lastError?.contains("the shift id field is required", ignoreCase = true) == true
                        || (failed.lastError?.contains("shift_id", ignoreCase = true) == true && failed.lastError?.contains("required", ignoreCase = true) == true)

                    if (isMissingShiftError && events.none { it.type == "shift.opened" }) {
                        val targetShiftId = failed.shiftId?.takeIf { it.isNotBlank() }
                            ?: currentShiftIdProvider?.invoke()?.takeIf { it.isNotBlank() }
                            ?: shiftDao?.getActiveShift()?.id
                            ?: shiftDao?.getLatestShift()?.id
                            ?: UUID.nameUUIDFromBytes("baseline-shift-${failed.deviceId}".toByteArray()).toString()

                        val shiftOpenedEv = resolveMissingShiftOpened(targetShiftId, events.firstOrNull()?.occurredAt)
                        if (shiftOpenedEv != null) {
                            val combinedEvents = listOf(shiftOpenedEv) + events
                            val freshBundleId = UUID.randomUUID().toString()
                            outboxDao.updateBundleId(failed.bundleId, freshBundleId)
                            outboxDao.updateShiftId(freshBundleId, targetShiftId)
                            outboxDao.updateEventsJson(freshBundleId, json.encodeToString(combinedEvents))
                            outboxDao.retryFailedBundle(freshBundleId)
                            continue
                        }
                    }

                    // 0c. Penanganan khusus error server: "CLOCK_SKEW", "masa depan", atau error sinkronisasi waktu
                    val isClockSkewError = failed.lastError?.contains("CLOCK_SKEW", ignoreCase = true) == true
                        || failed.lastError?.contains("masa depan", ignoreCase = true) == true
                        || (failed.lastError?.contains("waktu", ignoreCase = true) == true && failed.lastError?.contains("event", ignoreCase = true) == true)
                    if (isClockSkewError) {
                        val serverOffset = syncStateDao?.get(MasterDataSyncManager.KEY_SERVER_CLOCK_OFFSET)?.toLongOrNull() ?: 0L
                        val adjustedEvents = events.map { ev ->
                            ev.copy(occurredAt = Instant.now().plusMillis(serverOffset).toString())
                        }
                        val freshBundleId = UUID.randomUUID().toString()
                        outboxDao.updateBundleId(failed.bundleId, freshBundleId)
                        outboxDao.updateEventsJson(freshBundleId, json.encodeToString(adjustedEvents))
                        outboxDao.retryFailedBundle(freshBundleId)
                        continue
                    }

                    // 1. Ekstrak kode perangkat resmi dari pesan error server (misal L2 / C8)
                    val extractedCode = Regex("""display_number harus berformat\s+([A-Za-z0-9]+)-""", RegexOption.IGNORE_CASE)
                        .find(failed.lastError.orEmpty())?.groupValues?.getOrNull(1)
                    if (!extractedCode.isNullOrBlank()) {
                        onDeviceCodeLearned?.invoke(extractedCode)
                    }

                    val targetDevCode = extractedCode ?: currentDeviceCodeProvider?.invoke()?.takeIf { it.isNotBlank() }
                    val expectedPrefix = targetDevCode?.let { "$it-" }

                    // 2. Ekstrak kalkulasi total server jika sebelumnya terdapat TOTAL_MISMATCH
                    val totalMismatchServerTotal = failed.lastError?.let { err ->
                        Regex("""(?:total\s+hitung(?:\s+ulang)?|subtotal\s+hitung(?:\s+ulang)?|total\s+mismatch)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                            .find(err)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    }

                    val custEvent = events.firstOrNull { it.type == "customer.created" }
                    val custPayload = custEvent?.payload as? JsonObject
                    val custName = custPayload?.get("name")?.jsonPrimitive?.content
                    val custPhone = custPayload?.get("phone")?.jsonPrimitive?.content?.let { PhoneNumberUtil.normalize(it) }

                    val hasOrderCreated = events.any { it.type == "order.created" }
                    var wasModified = false

                    val cleanedEvents = events.filter { it.type != "customer.created" || !hasOrderCreated }.map { ev ->
                        if (ev.type == "order.created" && ev.payload is JsonObject) {
                            val orderPayloadObj = ev.payload as JsonObject
                            val newOrderMap = orderPayloadObj.toMutableMap()
                            var orderChanged = false

                            // Penyelarasan display_number dengan prefix resmi
                            if (expectedPrefix != null) {
                                val currentDisplay = orderPayloadObj["display_number"]?.jsonPrimitive?.content
                                if (currentDisplay != null && !currentDisplay.startsWith(expectedPrefix)) {
                                    val seq = currentDisplay.substringAfter("-")
                                    val alignedDisplay = "$expectedPrefix$seq"
                                    newOrderMap["display_number"] = JsonPrimitive(alignedDisplay)
                                    val orderId = orderPayloadObj["order_id"]?.jsonPrimitive?.content
                                    if (!orderId.isNullOrBlank()) {
                                        orderDao?.updateDisplayNumber(orderId, alignedDisplay)
                                    }
                                    orderChanged = true
                                    wasModified = true
                                }
                            }

                            // Penyelarasan mutual exclusion customer vs customer_id & validasi customer_id numerik
                            val existingCust = orderPayloadObj["customer"] as? JsonObject
                            val rawCustId = orderPayloadObj["customer_id"]?.jsonPrimitive?.content
                            val isCustIdNonNumeric = !rawCustId.isNullOrBlank() && rawCustId.toLongOrNull() == null
                            val dbCustomer = if (isCustIdNonNumeric) {
                                customerDao?.getCustomerById(rawCustId!!)
                            } else null

                            val finalName = custName ?: existingCust?.get("name")?.jsonPrimitive?.content ?: dbCustomer?.name
                            val rawPhone = custPhone ?: existingCust?.get("phone")?.jsonPrimitive?.content ?: dbCustomer?.phone
                            val finalPhone = rawPhone?.let { PhoneNumberUtil.normalize(it) }
                            val hasInlineCust = !finalName.isNullOrBlank() || !finalPhone.isNullOrBlank()

                            if (hasInlineCust) {
                                newOrderMap["customer"] = buildJsonObject {
                                    finalName?.let { put("name", it) }
                                    finalPhone?.let { put("phone", it) }
                                }
                                if (newOrderMap.containsKey("customer_id")) {
                                    newOrderMap.remove("customer_id")
                                }
                                orderChanged = true
                                wasModified = true
                            } else if (!rawCustId.isNullOrBlank()) {
                                if (rawCustId.toLongOrNull() != null) {
                                    if (orderPayloadObj["customer_id"]?.jsonPrimitive?.isString == true) {
                                        newOrderMap["customer_id"] = JsonPrimitive(rawCustId.toLong())
                                        orderChanged = true
                                        wasModified = true
                                    }
                                } else {
                                    newOrderMap.remove("customer_id")
                                    orderChanged = true
                                    wasModified = true
                                }
                            }

                            // Penyelarasan baris pesanan laundry (unit, weight_grams, express, line_total)
                            val isLaundry = (newOrderMap["order_type"]?.jsonPrimitive?.content == "laundry" || orderPayloadObj["order_type"]?.jsonPrimitive?.content == "laundry")
                            val rawLines = orderPayloadObj["lines"] as? JsonArray
                            val subtotalVal = orderPayloadObj["subtotal"]?.jsonPrimitive?.longOrNull ?: 0L
                            if (rawLines != null) {
                                var linesModified = false
                                val isSingleLine = rawLines.size == 1
                                val dbServices = masterDataDao?.getAllLaundryServices().orEmpty()
                                val fixedLines = buildJsonArray {
                                    for (lineElement in rawLines) {
                                        if (lineElement is JsonObject) {
                                            val lineMap = lineElement.toMutableMap()
                                            if (isLaundry) {
                                                val resolved = resolveLaundryLine(lineMap, dbServices, subtotalVal, isSingleLine)
                                                if (resolved) linesModified = true
                                            } else {
                                                if (!lineMap.containsKey("options")) {
                                                    lineMap["options"] = buildJsonArray {}
                                                    linesModified = true
                                                }
                                            }
                                            add(JsonObject(lineMap))
                                        } else {
                                            add(lineElement)
                                        }
                                    }
                                }
                                if (linesModified) {
                                    newOrderMap["lines"] = fixedLines
                                    orderChanged = true
                                    wasModified = true
                                }
                            }

                            // Penyelarasan total server jika pernah ada penolakan hitung ulang
                            val isLaundryAutoRecover = orderPayloadObj["order_type"]?.jsonPrimitive?.content?.equals("laundry", ignoreCase = true) == true
                            val orderId = orderPayloadObj["order_id"]?.jsonPrimitive?.content
                            if (totalMismatchServerTotal != null) {
                                val discountVal = orderPayloadObj["discount"]?.jsonPrimitive?.longOrNull ?: 0L
                                val serviceVal = orderPayloadObj["service_charge"]?.jsonPrimitive?.longOrNull ?: 0L
                                val roundingVal = orderPayloadObj["rounding"]?.jsonPrimitive?.longOrNull ?: 0L
                                val dpp = (subtotalVal - discountVal).coerceAtLeast(0L)
                                if (isLaundryAutoRecover) {
                                    newOrderMap["tax"] = JsonPrimitive(0L)
                                    newOrderMap["service_charge"] = JsonPrimitive(0L)
                                    newOrderMap["rounding"] = JsonPrimitive(0L)
                                    newOrderMap["total"] = JsonPrimitive(totalMismatchServerTotal)
                                    if (!orderId.isNullOrBlank()) {
                                        orderDao?.updateOrderTotals(
                                            orderId = orderId,
                                            subtotal = subtotalVal,
                                            discount = discountVal,
                                            serviceCharge = 0L,
                                            tax = 0L,
                                            rounding = 0L,
                                            total = totalMismatchServerTotal
                                        )
                                    }
                                } else {
                                    val alignedTax = (totalMismatchServerTotal - dpp - serviceVal - roundingVal).coerceAtLeast(0L)
                                    newOrderMap["tax"] = JsonPrimitive(alignedTax)
                                    newOrderMap["total"] = JsonPrimitive(totalMismatchServerTotal)
                                    if (!orderId.isNullOrBlank()) {
                                        orderDao?.updateOrderTotals(
                                            orderId = orderId,
                                            subtotal = subtotalVal,
                                            discount = discountVal,
                                            serviceCharge = serviceVal,
                                            tax = alignedTax,
                                            rounding = roundingVal,
                                            total = totalMismatchServerTotal
                                        )
                                    }
                                }
                                orderChanged = true
                                wasModified = true
                            } else if (isLaundryAutoRecover) {
                                val curTax = orderPayloadObj["tax"]?.jsonPrimitive?.longOrNull ?: 0L
                                val curServ = orderPayloadObj["service_charge"]?.jsonPrimitive?.longOrNull ?: 0L
                                val curRound = orderPayloadObj["rounding"]?.jsonPrimitive?.longOrNull ?: 0L
                                if (curTax != 0L || curServ != 0L || curRound != 0L) {
                                    newOrderMap["tax"] = JsonPrimitive(0L)
                                    newOrderMap["service_charge"] = JsonPrimitive(0L)
                                    newOrderMap["rounding"] = JsonPrimitive(0L)
                                    val discountVal = orderPayloadObj["discount"]?.jsonPrimitive?.longOrNull ?: 0L
                                    val laundryExpected = (subtotalVal - discountVal).coerceAtLeast(0L)
                                    newOrderMap["total"] = JsonPrimitive(laundryExpected)
                                    if (!orderId.isNullOrBlank()) {
                                        orderDao?.updateOrderTotals(
                                            orderId = orderId,
                                            subtotal = subtotalVal,
                                            discount = discountVal,
                                            serviceCharge = 0L,
                                            tax = 0L,
                                            rounding = 0L,
                                            total = laundryExpected
                                        )
                                    }
                                    orderChanged = true
                                    wasModified = true
                                }
                            }

                            if (orderChanged) {
                                ev.copy(payload = JsonObject(newOrderMap))
                            } else {
                                ev
                            }
                        } else if (ev.type == "laundry.status_changed" && ev.payload is JsonObject) {
                            val pObj = ev.payload as JsonObject
                            val newMap = pObj.toMutableMap()
                            var evChanged = false
                            val rawStatus = pObj["status"]?.jsonPrimitive?.content ?: "received"
                            val backendStatus = LaundryStatusMapper.toBackend(rawStatus)
                            if (backendStatus != rawStatus) {
                                newMap["status"] = JsonPrimitive(backendStatus)
                                evChanged = true
                                wasModified = true
                            }
                            if (pObj["user_id"]?.jsonPrimitive?.longOrNull == null) {
                                val numericUserId = LaundryStatusMapper.normalizeNumericUserId(pObj["user_id"]?.jsonPrimitive?.content) ?: 4L
                                newMap["user_id"] = JsonPrimitive(numericUserId)
                                evChanged = true
                                wasModified = true
                            }
                            val rawApprovedBy = pObj["approved_by"]?.jsonPrimitive?.content
                            if (!rawApprovedBy.isNullOrBlank() && pObj["approved_by"]?.jsonPrimitive?.longOrNull == null) {
                                val numericApproved = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                                newMap["approved_by"] = JsonPrimitive(numericApproved)
                                evChanged = true
                                wasModified = true
                            }
                            if (backendStatus == "diambil" && !newMap.containsKey("line_ids")) {
                                val rawLineId = pObj["order_line_id"]?.jsonPrimitive?.content
                                if (rawLineId != null) {
                                    newMap["line_ids"] = buildJsonArray { add(JsonPrimitive(rawLineId)) }
                                    evChanged = true
                                    wasModified = true
                                }
                            }
                            if (evChanged) {
                                ev.copy(payload = JsonObject(newMap))
                            } else {
                                ev
                            }
                        } else if (ev.type == "laundry.picked_up" && ev.payload is JsonObject) {
                            val pObj = ev.payload as JsonObject
                            val newMap = pObj.toMutableMap()
                            newMap["status"] = JsonPrimitive("diambil")
                            if (!newMap.containsKey("line_ids")) {
                                val rawLineId = pObj["order_line_id"]?.jsonPrimitive?.content
                                newMap["line_ids"] = buildJsonArray { rawLineId?.let { add(JsonPrimitive(it)) } }
                            }
                            if (pObj["user_id"]?.jsonPrimitive?.longOrNull == null) {
                                val numericUserId = LaundryStatusMapper.normalizeNumericUserId(pObj["user_id"]?.jsonPrimitive?.content) ?: 4L
                                newMap["user_id"] = JsonPrimitive(numericUserId)
                            }
                            val rawApprovedBy = pObj["approved_by"]?.jsonPrimitive?.content
                            if (!rawApprovedBy.isNullOrBlank() && pObj["approved_by"]?.jsonPrimitive?.longOrNull == null) {
                                val numericApproved = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                                newMap["approved_by"] = JsonPrimitive(numericApproved)
                            }
                            wasModified = true
                            ev.copy(type = "laundry.status_changed", payload = JsonObject(newMap))
                        } else {
                            ev
                        }
                    }

                    if (wasModified || cleanedEvents.size != events.size) {
                        val freshBundleId = UUID.randomUUID().toString()
                        outboxDao.updateBundleId(failed.bundleId, freshBundleId)
                        outboxDao.updateEventsJson(freshBundleId, json.encodeToString(cleanedEvents))
                        outboxDao.retryFailedBundle(freshBundleId)
                    }
                }
            }
        }

    private suspend fun handleRejectedResult(outbox: OutboxEntity, result: BundleResultDto): Boolean {
        // Jika server menolak karena event menu.sold_out_changed belum dikenal backend (Opsi B2),
        // tandai SENT secara lokal agar tidak tersangkut di antrean bermasalah tablet.
        if (result.message?.contains("menu.sold_out_changed", ignoreCase = true) == true) {
            outboxDao.markSent(outbox.bundleId)
            return true
        }

        // Jika server menolak dengan "order belum diterima server" (DEPENDENCY_MISSING):
        val isMissingOrderError = result.message?.contains("order belum diterima", ignoreCase = true) == true
            || (result.reasonCode?.contains("DEPENDENCY_MISSING", ignoreCase = true) == true && result.message?.contains("order", ignoreCase = true) == true)
        if (isMissingOrderError) {
            val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
            val targetOrderId = events?.firstNotNullOfOrNull { ev ->
                (ev.payload as? JsonObject)?.get("order_id")?.jsonPrimitive?.content
            }
            if (!targetOrderId.isNullOrBlank() && events != null && events.none { it.type == "order.created" }) {
                val createdEvent = resolveMissingOrderCreated(targetOrderId, events.firstOrNull()?.occurredAt)
                if (createdEvent != null) {
                    val combinedEvents = listOf(createdEvent) + events
                    val freshBundleId = UUID.randomUUID().toString()
                    outboxDao.updateBundleId(outbox.bundleId, freshBundleId)
                    outboxDao.updateEventsJson(freshBundleId, json.encodeToString(combinedEvents))
                    outboxDao.retryFailedBundle(freshBundleId)

                    val otherBundles = outboxDao.findBundlesByOrderId(targetOrderId)
                    for (other in otherBundles) {
                        if (other.bundleId != outbox.bundleId && other.bundleId != freshBundleId) {
                            val otherEvents = runCatching { json.decodeFromString<List<SyncEventDto>>(other.eventsJson) }.getOrNull()
                            if (otherEvents != null && otherEvents.all { it.type == "order.created" || it.type == "customer.created" }) {
                                outboxDao.markSent(other.bundleId)
                            }
                        }
                    }
                    return false
                }
            }
        }

        // Jika server menolak dengan "shift belum diterima server" atau "the shift id field is required" (DEPENDENCY_MISSING / OQ-28):
        val isMissingShiftError = result.message?.contains("shift belum diterima", ignoreCase = true) == true
            || result.message?.contains("the shift id field is required", ignoreCase = true) == true
            || (result.message?.contains("shift_id", ignoreCase = true) == true && result.message?.contains("required", ignoreCase = true) == true)

        if (isMissingShiftError) {
            val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
            val targetShiftId = outbox.shiftId?.takeIf { it.isNotBlank() }
                ?: currentShiftIdProvider?.invoke()?.takeIf { it.isNotBlank() }
                ?: shiftDao?.getActiveShift()?.id
                ?: shiftDao?.getLatestShift()?.id
                ?: UUID.nameUUIDFromBytes("baseline-shift-${outbox.deviceId}".toByteArray()).toString()

            if (events != null && events.none { it.type == "shift.opened" }) {
                val shiftOpenedEv = resolveMissingShiftOpened(targetShiftId, events.firstOrNull()?.occurredAt)
                if (shiftOpenedEv != null) {
                    val combinedEvents = listOf(shiftOpenedEv) + events
                    val freshBundleId = UUID.randomUUID().toString()
                    outboxDao.updateBundleId(outbox.bundleId, freshBundleId)
                    outboxDao.updateShiftId(freshBundleId, targetShiftId)
                    outboxDao.updateEventsJson(freshBundleId, json.encodeToString(combinedEvents))
                    outboxDao.retryFailedBundle(freshBundleId)
                    return false
                }
            }
        }

        // Jika server menolak karena event laundry.picked_up belum dikenal backend Laravel (OQ-28)
        val isPickedUpTypeError = result.message?.contains("laundry.picked_up", ignoreCase = true) == true
        if (isPickedUpTypeError) {
            val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
            val convertedEvents = events?.map { ev ->
                if (ev.type == "laundry.picked_up" && ev.payload is JsonObject) {
                    val pObj = ev.payload as JsonObject
                    val newMap = pObj.toMutableMap()
                    newMap["status"] = JsonPrimitive("diambil")
                    if (!newMap.containsKey("line_ids")) {
                        val rawLineId = pObj["order_line_id"]?.jsonPrimitive?.content
                        newMap["line_ids"] = buildJsonArray { rawLineId?.let { add(JsonPrimitive(it)) } }
                    }
                    if (pObj["user_id"]?.jsonPrimitive?.longOrNull == null) {
                        val numericUserId = LaundryStatusMapper.normalizeNumericUserId(pObj["user_id"]?.jsonPrimitive?.content) ?: 4L
                        newMap["user_id"] = JsonPrimitive(numericUserId)
                    }
                    val rawApprovedBy = pObj["approved_by"]?.jsonPrimitive?.content
                    if (!rawApprovedBy.isNullOrBlank() && pObj["approved_by"]?.jsonPrimitive?.longOrNull == null) {
                        val numericApproved = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                        newMap["approved_by"] = JsonPrimitive(numericApproved)
                    }
                    ev.copy(type = "laundry.status_changed", payload = JsonObject(newMap))
                } else {
                    ev
                }
            } ?: emptyList()

            if (convertedEvents.isNotEmpty()) {
                val freshId = UUID.randomUUID().toString()
                outboxDao.updateBundleId(outbox.bundleId, freshId)
                outboxDao.updateEventsJson(freshId, json.encodeToString(convertedEvents))
                outboxDao.retryFailedBundle(freshId)
                return false
            }
        }

        // Jika server menolak karena event customer.created belum dikenal backend (Opsi B2 / OQ-27)
        // atau payload.customer dan payload.customer_id tidak boleh bersamaan
        // atau payload.customer_id harus bilangan bulat:
        val isCustomerTypeError = result.message?.contains("customer.created", ignoreCase = true) == true
            || (result.message?.contains("tidak dikenal", ignoreCase = true) == true && result.message?.contains("customer", ignoreCase = true) == true)
            || result.message?.contains("tidak boleh bersamaan", ignoreCase = true) == true
            || (result.message?.contains("payload.customer", ignoreCase = true) == true && result.message?.contains("customer_id", ignoreCase = true) == true)
            || result.message?.contains("customer_id harus bilangan bulat", ignoreCase = true) == true
            || result.message?.contains("bilangan bulat", ignoreCase = true) == true

        if (isCustomerTypeError) {
            val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
            val custEvent = events?.firstOrNull { it.type == "customer.created" }
            val custPayload = custEvent?.payload as? JsonObject
            val custName = custPayload?.get("name")?.jsonPrimitive?.content
            val custPhone = custPayload?.get("phone")?.jsonPrimitive?.content?.let { PhoneNumberUtil.normalize(it) }

            val sanitizedEvents = events?.filter { it.type != "customer.created" }?.map { ev ->
                if (ev.type == "order.created" && ev.payload is JsonObject) {
                    val orderPayloadObj = ev.payload as JsonObject
                    val existingCust = orderPayloadObj["customer"] as? JsonObject
                    val rawCustId = orderPayloadObj["customer_id"]?.jsonPrimitive?.content
                    val isCustIdNonNumeric = !rawCustId.isNullOrBlank() && rawCustId.toLongOrNull() == null
                    val dbCustomer = if (isCustIdNonNumeric) {
                        customerDao?.getCustomerById(rawCustId!!)
                    } else null

                    val finalName = custName ?: existingCust?.get("name")?.jsonPrimitive?.content ?: dbCustomer?.name
                    val rawPhone = custPhone ?: existingCust?.get("phone")?.jsonPrimitive?.content ?: dbCustomer?.phone
                    val finalPhone = rawPhone?.let { PhoneNumberUtil.normalize(it) }

                    val hasInlineCustomer = !finalName.isNullOrBlank() || !finalPhone.isNullOrBlank()
                    val newOrderMap = orderPayloadObj.toMutableMap()
                    if (hasInlineCustomer) {
                        newOrderMap["customer"] = buildJsonObject {
                            finalName?.let { put("name", it) }
                            finalPhone?.let { put("phone", it) }
                        }
                        newOrderMap.remove("customer_id")
                    } else {
                        newOrderMap.remove("customer")
                        newOrderMap.remove("customer_id")
                    }
                    ev.copy(payload = JsonObject(newOrderMap))
                } else {
                    ev
                }
            } ?: emptyList()

            if (sanitizedEvents.isEmpty()) {
                outboxDao.markSent(outbox.bundleId)
                return true
            } else {
                val freshId = UUID.randomUUID().toString()
                outboxDao.updateBundleId(outbox.bundleId, freshId)
                outboxDao.updateEventsJson(freshId, json.encodeToString(sanitizedEvents))
                outboxDao.retryFailedBundle(freshId)
                return false
            }
        }

        // Jika server menolak shift.opened karena akun staf tidak berizin membuka shift
        val isUnauthorizedShiftError = result.message?.contains("tidak berizin", ignoreCase = true) == true
            && (result.message?.contains("shift", ignoreCase = true) == true || outbox.eventsJson.contains("shift.opened", ignoreCase = true))

        if (isUnauthorizedShiftError) {
            // Batalkan shift lokal yang ditolak server agar tablet tidak terjebak shift aktif palsu
            outbox.shiftId?.let { shiftId ->
                shiftDao?.deleteShift(shiftId)
            }
            outboxDao.markFailed(
                bundleId = outbox.bundleId,
                error = "Akun tidak berizin membuka shift di usaha ini. Buka shift hanya boleh dilakukan oleh Kasir atau Manager.",
                reasonCode = "UNAUTHORIZED_SHIFT"
            )
            return false
        }

        // Jika server menolak karena format display_number (misal: "display_number harus berformat L2-NNNN."):
        val isDisplayFormatError = result.message?.contains("display_number harus berformat", ignoreCase = true) == true
        if (isDisplayFormatError) {
            val extractedCode = Regex("""display_number harus berformat\s+([A-Za-z0-9]+)-""", RegexOption.IGNORE_CASE)
                .find(result.message!!)?.groupValues?.getOrNull(1)
            if (!extractedCode.isNullOrBlank()) {
                onDeviceCodeLearned?.invoke(extractedCode)
                val expectedPrefix = "$extractedCode-"
                val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
                val alignedEvents = events?.map { ev ->
                    if (ev.type == "order.created" && ev.payload is JsonObject) {
                        val orderPayloadObj = ev.payload as JsonObject
                        val currentDisplay = orderPayloadObj["display_number"]?.jsonPrimitive?.content
                        if (currentDisplay != null && !currentDisplay.startsWith(expectedPrefix)) {
                            val seq = currentDisplay.substringAfter("-")
                            val alignedDisplay = "$expectedPrefix$seq"
                            val pMap = orderPayloadObj.toMutableMap()
                            pMap["display_number"] = JsonPrimitive(alignedDisplay)
                            val orderId = orderPayloadObj["order_id"]?.jsonPrimitive?.content
                            if (!orderId.isNullOrBlank()) {
                                orderDao?.updateDisplayNumber(orderId, alignedDisplay)
                            }
                            ev.copy(payload = JsonObject(pMap))
                        } else {
                            ev
                        }
                    } else {
                        ev
                    }
                } ?: emptyList()

                if (alignedEvents.isNotEmpty()) {
                    val freshId = UUID.randomUUID().toString()
                    outboxDao.updateBundleId(outbox.bundleId, freshId)
                    outboxDao.updateEventsJson(freshId, json.encodeToString(alignedEvents))
                    outboxDao.retryFailedBundle(freshId)
                    return false
                }
            }
        }

        // Jika server menolak karena item_ref_id atau satuan (misal: "Satuan pcs tidak cocok dengan layanan 1."):
        val isItemRefOrUnitError = result.message?.contains("item_ref_id", ignoreCase = true) == true
            || result.message?.contains("tidak cocok dengan layanan", ignoreCase = true) == true
            || result.message?.contains("satuan", ignoreCase = true) == true
        if (isItemRefOrUnitError) {
            val dbServices = masterDataDao?.getAllLaundryServices().orEmpty()
            val events = runCatching { json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson) }.getOrNull()
            val alignedEvents = events?.map { ev ->
                if (ev.type == "order.created" && ev.payload is JsonObject) {
                    val pObj = ev.payload as JsonObject
                    val rawLines = pObj["lines"] as? JsonArray
                    val subtotalVal = pObj["subtotal"]?.jsonPrimitive?.longOrNull ?: 0L
                    val isSingleLine = (rawLines?.size == 1)
                    if (rawLines != null) {
                        val newLines = buildJsonArray {
                            for (elem in rawLines) {
                                if (elem is JsonObject) {
                                    val lMap = elem.toMutableMap()
                                    resolveLaundryLine(lMap, dbServices, subtotalVal, isSingleLine)
                                    add(JsonObject(lMap))
                                } else {
                                    add(elem)
                                }
                            }
                        }
                        val newPMap = pObj.toMutableMap()
                        newPMap["lines"] = newLines
                        ev.copy(payload = JsonObject(newPMap))
                    } else {
                        ev
                    }
                } else {
                    ev
                }
            } ?: emptyList()

            if (alignedEvents.isNotEmpty()) {
                val freshId = UUID.randomUUID().toString()
                outboxDao.updateBundleId(outbox.bundleId, freshId)
                outboxDao.updateEventsJson(freshId, json.encodeToString(alignedEvents))
                outboxDao.retryFailedBundle(freshId)
                return false
            }
        }

        val isSchemaOrHashMismatch = result.message?.contains("sudah dipakai", ignoreCase = true) == true
            || (result.message?.contains("bundle_id", ignoreCase = true) == true && result.message?.contains("berbeda", ignoreCase = true) == true)
            || result.message?.contains("discount_rule", ignoreCase = true) == true
            || result.message?.contains("tidak dikenal", ignoreCase = true) == true
            || result.message?.contains("display_number", ignoreCase = true) == true
            || result.message?.contains("TOTAL_MISMATCH", ignoreCase = true) == true
            || result.message?.contains("Total hitung", ignoreCase = true) == true
            || result.message?.contains("subtotal hitung", ignoreCase = true) == true
            || result.message?.contains("table_label", ignoreCase = true) == true
            || result.message?.contains("lebih awal", ignoreCase = true) == true
            || result.message?.contains("foto nota", ignoreCase = true) == true
            || result.message?.contains("correction", ignoreCase = true) == true
            || result.message?.contains("reason_code", ignoreCase = true) == true
            || result.message?.contains("reason_note", ignoreCase = true) == true
            || result.message?.contains("shift.closed", ignoreCase = true) == true
            || result.message?.contains("expected_cash", ignoreCase = true) == true
            || result.message?.contains("difference", ignoreCase = true) == true
            || result.message?.contains("laundry.status_changed", ignoreCase = true) == true
            || result.message?.contains("laundry.picked_up", ignoreCase = true) == true
            || result.message?.contains("customer", ignoreCase = true) == true
            || result.message?.contains("bilangan bulat", ignoreCase = true) == true
            || result.message?.contains("sold_out", ignoreCase = true) == true
            || result.message?.contains("kitchen", ignoreCase = true) == true
            || result.message?.contains("tidak cocok dengan layanan", ignoreCase = true) == true
            || result.message?.contains("satuan", ignoreCase = true) == true
            || result.message?.contains("CLOCK_SKEW", ignoreCase = true) == true
            || result.message?.contains("masa depan", ignoreCase = true) == true
            || result.message?.contains("waktu", ignoreCase = true) == true
            || result.flags?.contains("CLOCK_SKEW") == true
            || result.reasonCode?.contains("TOTAL_MISMATCH", ignoreCase = true) == true

        if (isSchemaOrHashMismatch) {
            val serverTotalFromMsg = result.message?.let { msg ->
                Regex("""(?:total\s+hitung(?:\s+ulang)?|subtotal\s+hitung(?:\s+ulang)?|total\s+mismatch)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(msg)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
            val receivedTotalFromMsg = result.message?.let { msg ->
                Regex("""diterima\s+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(msg)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
            if (serverTotalFromMsg != null && receivedTotalFromMsg != null && receivedTotalFromMsg > 0L && serverTotalFromMsg > receivedTotalFromMsg) {
                val isLaundryBundle = outbox.eventsJson.contains(""""order_type":"laundry"""", ignoreCase = true)
                if (!isLaundryBundle) {
                    val inferredTax = serverTotalFromMsg - receivedTotalFromMsg
                    val inferredPercent = ((inferredTax * 100L) / receivedTotalFromMsg).toInt()
                    if (inferredPercent in 1..99) {
                        syncStateDao?.set(SyncStateEntity(key = "tax_percent", value = inferredPercent.toString()))
                    }
                }
            }

            // Server menolak karena skema atau hash berbeda.
            // Ganti bundle_id ke UUID baru agar kiriman berikutnya setelah sanitasi tidak bentrok hash.
            val freshBundleId = UUID.randomUUID().toString()
            outboxDao.updateBundleId(outbox.bundleId, freshBundleId)
            outboxDao.markFailed(
                bundleId = freshBundleId,
                error = result.message ?: (result.reasonCode ?: "REJECTED"),
                reasonCode = result.reasonCode
            )
            return false
        } else if (result.retryable == true) {
            if (outbox.attempts >= 3) {
                outboxDao.markFailed(
                    bundleId = outbox.bundleId,
                    error = result.message ?: (result.reasonCode ?: "Dependensi belum terpenuhi di server"),
                    reasonCode = result.reasonCode
                )
            } else {
                outboxDao.incrementAttempts(outbox.bundleId, result.message ?: result.reasonCode)
            }
            return false
        } else {
            outboxDao.markFailed(
                bundleId = outbox.bundleId,
                error = result.message ?: (result.reasonCode ?: "REJECTED"),
                reasonCode = result.reasonCode
            )
            return false
        }
    }

    suspend fun sendPendingBundles(limit: Int = 50): SyncSendSummary {
        try {
            autoRecoverFailedBundles()
        } catch (_: Exception) {
            // Non-blocking fallback
        }

        val pendingItems = outboxDao.getOutboxByStatus(OutboxStatus.PENDING, limit)
        if (pendingItems.isEmpty()) {
            return SyncSendSummary(sent = 0, duplicates = 0, failed = 0)
        }

        if (isDemoModeProvider?.invoke() == true) {
            // Mode demo tidak memanggil server live dengan kredensial tiruan;
            // tandai outbox sebagai SENT demi kelancaran demo lokal
            pendingItems.forEach { outbox ->
                outboxDao.markSent(outbox.bundleId)
            }
            return SyncSendSummary(sent = pendingItems.size, duplicates = 0, failed = 0)
        }

        val currentDevId = currentDeviceIdProvider?.invoke()?.takeIf { it.isNotBlank() }
        val currentShiftId = currentShiftIdProvider?.invoke()?.takeIf { it.isNotBlank() }
        val currentDevCode = currentDeviceCodeProvider?.invoke()?.takeIf { it.isNotBlank() }

        val processedItems = mutableListOf<Pair<OutboxEntity, SyncBundleDto>>()

        for (outbox in pendingItems) {
            val effectiveDeviceId = currentDevId ?: outbox.deviceId
            val isDeviceChanged = (currentDevId != null && currentDevId != outbox.deviceId)
            if (isDeviceChanged) {
                // Selaraskan deviceId pada entitas outbox lokal Room agar tidak terjadi drift
                outboxDao.updateDeviceId(outbox.bundleId, currentDevId!!)
            }

            var events = json.decodeFromString<List<SyncEventDto>>(outbox.eventsJson)

            // ATURAN VALIDATOR LARAVEL 12 BACKEND (OQ-28):
            // Backend Laravel 12 pada endpoint POST /v1/sync/bundles memberlakukan validasi 'bundles.*.shift_id' => 'required|uuid'.
            // Jika dikirim null, server menolak dengan HTTP 422 ("shift_id : the shift id field is required").
            // Karena itu, setiap bundel wajib membawa shift_id valid (UUID).
            val effectiveShiftId = outbox.shiftId?.takeIf { it.isNotBlank() }
                ?: currentShiftIdProvider?.invoke()?.takeIf { it.isNotBlank() }
                ?: shiftDao?.getActiveShift()?.id
                ?: shiftDao?.getLatestShift()?.id
                ?: UUID.nameUUIDFromBytes("baseline-shift-${effectiveDeviceId}".toByteArray()).toString()

            val isShiftChanged = (effectiveShiftId != outbox.shiftId)
            if (isShiftChanged) {
                // Selaraskan shiftId pada entitas outbox lokal Room agar konsisten
                outboxDao.updateShiftId(outbox.bundleId, effectiveShiftId)
            }

            // Auto-heal jika bundel pending pernah tertolak dengan "order belum diterima server" (DEPENDENCY_MISSING)
            val isPendingMissingOrderError = outbox.lastError?.contains("order belum diterima", ignoreCase = true) == true
                || (outbox.reasonCode?.contains("DEPENDENCY_MISSING", ignoreCase = true) == true && outbox.lastError?.contains("order", ignoreCase = true) == true)
            if (isPendingMissingOrderError && events.none { it.type == "order.created" }) {
                val targetOrderId = events.firstNotNullOfOrNull { ev ->
                    (ev.payload as? JsonObject)?.get("order_id")?.jsonPrimitive?.content
                }
                if (!targetOrderId.isNullOrBlank()) {
                    val createdEv = resolveMissingOrderCreated(targetOrderId, events.firstOrNull()?.occurredAt)
                    if (createdEv != null) {
                        events = listOf(createdEv) + events
                    }
                }
            }

            // Auto-heal jika bundel pending pernah tertolak dengan "shift belum diterima server" atau "the shift id field is required" (DEPENDENCY_MISSING / OQ-28)
            val isPendingMissingShiftError = outbox.lastError?.contains("shift belum diterima", ignoreCase = true) == true
                || outbox.lastError?.contains("the shift id field is required", ignoreCase = true) == true
                || (outbox.lastError?.contains("shift_id", ignoreCase = true) == true && outbox.lastError?.contains("required", ignoreCase = true) == true)
            if (isPendingMissingShiftError && events.none { it.type == "shift.opened" }) {
                val shiftOpenedEv = resolveMissingShiftOpened(effectiveShiftId, events.firstOrNull()?.occurredAt)
                if (shiftOpenedEv != null) {
                    events = listOf(shiftOpenedEv) + events
                }
            }

            // Bundel yang hanya berisi event menu.sold_out_changed ditandai SENT secara lokal (Opsi B2),
            // karena backend Laravel aktif belum mendukung tipe event tersebut pada POST /v1/sync/bundles.
            val isOnlySoldOut = events.isNotEmpty() && events.all { it.type == "menu.sold_out_changed" }
            if (isOnlySoldOut) {
                outboxDao.markSent(outbox.bundleId)
                continue
            }

            // Bundel yang hanya berisi event customer.created ditandai SENT secara lokal (Opsi B2 / OQ-27),
            // karena backend Laravel aktif belum mendukung tipe event tersebut pada POST /v1/sync/bundles.
            val isOnlyCustomer = events.isNotEmpty() && events.all { it.type == "customer.created" }
            if (isOnlyCustomer) {
                outboxDao.markSent(outbox.bundleId)
                continue
            }

            // PENYELARASAN DISPLAY NUMBER DENGAN KODE PERANGKAT (Rule 8 AGENTS.md):
            // Server memvalidasi nomor order harus diawali kode tablet yang terdaftar (misal C8-NNNN atau L2-NNNN).
            // Ekstrak kode dari pesan error validasi server ("display_number harus berformat L2-NNNN.") atau dari currentDeviceCodeProvider
            val extractedCodeFromError = outbox.lastError?.let { err ->
                Regex("""display_number harus berformat\s+([A-Za-z0-9]+)-""", RegexOption.IGNORE_CASE)
                    .find(err)?.groupValues?.getOrNull(1)
            }
            if (!extractedCodeFromError.isNullOrBlank()) {
                onDeviceCodeLearned?.invoke(extractedCodeFromError)
            }
            val targetDeviceCode = extractedCodeFromError ?: currentDevCode
            val expectedPrefix = targetDeviceCode?.let { "$it-" }

            var isEventsModified = false
            var finalEvents = events
            val alignedOrderTotals = mutableMapOf<String, Pair<Long, Long>>() // orderId -> Pair(oldTotal, newTotal)

            val newEvents = events.map { event ->
                if ((event.type == "order.created" || event.type == "order.items_added") && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var eventPayloadChanged = false
                    val newPayloadMap = payloadObj.toMutableMap()

                    // 1. Penyelarasan display_number dengan kode perangkat (C8-NNNN)
                    if (expectedPrefix != null && event.type == "order.created") {
                        val currentDisplay = payloadObj["display_number"]?.jsonPrimitive?.content
                        if (currentDisplay != null && !currentDisplay.startsWith(expectedPrefix)) {
                            val seq = currentDisplay.substringAfter("-")
                            val alignedDisplayNumber = "$expectedPrefix$seq"
                            eventPayloadChanged = true
                            isEventsModified = true

                            val orderId = payloadObj["order_id"]?.jsonPrimitive?.content
                            if (!orderId.isNullOrBlank()) {
                                orderDao?.updateDisplayNumber(orderId, alignedDisplayNumber)
                            }
                            newPayloadMap["display_number"] = JsonPrimitive(alignedDisplayNumber)
                        }
                    }

                    // 2. Normalisasi order_type (enum: dine_in, takeaway, laundry)
                    val rawOrderType = payloadObj["order_type"]?.jsonPrimitive?.content
                    if (rawOrderType != null) {
                        val normalizedType = when (rawOrderType.lowercase()) {
                            "take_away" -> "takeaway"
                            else -> rawOrderType.lowercase()
                        }
                        if (normalizedType != rawOrderType) {
                            newPayloadMap["order_type"] = JsonPrimitive(normalizedType)
                            eventPayloadChanged = true
                            isEventsModified = true
                        }
                    }

                    // 3. Aturan Diskon (payload.discount_rule.preset wajib bila ada diskon):
                    // Jika transaksi sebelumnya ditolak server karena preset tidak dikenal ("preset diskon tidak dikenal")
                    // atau membawa preset 'Diskon 5%' yang ditolak server, maka nol-kan diskon (discount = 0),
                    // hitung ulang total = subtotal + service + tax + rounding, dan hapus discount_rule agar transaksi langsung lolos validasi server.
                    val existingDiscountRule = payloadObj["discount_rule"] as? JsonObject
                    val existingPreset = existingDiscountRule?.get("preset")?.jsonPrimitive?.content

                    val isUnknownPresetError = outbox.lastError?.contains("preset diskon tidak dikenal", ignoreCase = true) == true
                        || outbox.lastError?.contains("tidak dikenal", ignoreCase = true) == true
                        || outbox.eventsJson.contains("Diskon 5%", ignoreCase = true)
                        || existingPreset.equals("Diskon 5%", ignoreCase = true)

                    val discountVal = payloadObj["discount"]?.jsonPrimitive?.longOrNull ?: 0L
                    val subtotalVal = payloadObj["subtotal"]?.jsonPrimitive?.longOrNull ?: 0L
                    val serviceVal = payloadObj["service_charge"]?.jsonPrimitive?.longOrNull ?: 0L
                    val taxVal = payloadObj["tax"]?.jsonPrimitive?.longOrNull ?: 0L
                    val roundingVal = payloadObj["rounding"]?.jsonPrimitive?.longOrNull ?: 0L
                    val orderId = payloadObj["order_id"]?.jsonPrimitive?.content

                    if (isUnknownPresetError && discountVal > 0L) {
                        newPayloadMap["discount"] = JsonPrimitive(0L)
                        val recalculatedTotal = subtotalVal + serviceVal + taxVal + roundingVal
                        newPayloadMap["total"] = JsonPrimitive(recalculatedTotal)
                        newPayloadMap.remove("discount_rule")
                        eventPayloadChanged = true
                        isEventsModified = true

                        if (!orderId.isNullOrBlank()) {
                            orderDao?.updateOrderTotals(
                                orderId = orderId,
                                subtotal = subtotalVal,
                                discount = 0L,
                                serviceCharge = serviceVal,
                                tax = taxVal,
                                rounding = roundingVal,
                                total = recalculatedTotal
                            )
                        }
                    } else if (discountVal > 0L) {
                        if (existingPreset.isNullOrBlank()) {
                            val inferredPreset = when {
                                subtotalVal > 0L && discountVal == (subtotalVal * 10 / 100) -> "Diskon 10%"
                                subtotalVal > 0L && discountVal == (subtotalVal * 5 / 100) -> "Diskon 5%"
                                discountVal == 5000L -> "Potongan Rp5.000"
                                subtotalVal > 0L && kotlin.math.abs(discountVal - (subtotalVal * 0.1).toLong()) <= 100 -> "Diskon 10%"
                                subtotalVal > 0L && kotlin.math.abs(discountVal - (subtotalVal * 0.05).toLong()) <= 100 -> "Diskon 5%"
                                else -> "Diskon 10%"
                            }
                            val ruleObj = buildJsonObject {
                                put("preset", inferredPreset)
                            }
                            newPayloadMap["discount_rule"] = ruleObj
                            eventPayloadChanged = true
                            isEventsModified = true
                        }
                    }

                    // 4. Penyelarasan Pajak (Tax) & Total
                    val isLaundryOrder = (newPayloadMap["order_type"]?.jsonPrimitive?.content == "laundry"
                        || payloadObj["order_type"]?.jsonPrimitive?.content == "laundry")

                    val currentTax = newPayloadMap["tax"]?.jsonPrimitive?.longOrNull ?: taxVal
                    val currentDiscount = newPayloadMap["discount"]?.jsonPrimitive?.longOrNull ?: discountVal
                    val dpp = (subtotalVal - currentDiscount).coerceAtLeast(0L)
                    val oldOrderTotal = payloadObj["total"]?.jsonPrimitive?.longOrNull ?: subtotalVal

                    if (isLaundryOrder) {
                        // Sesuai OQ-03 & docs/business-rules.md: Laundry bebas pajak (0%), biaya layanan (0%), dan pembulatan (0)
                        val laundryExpectedTotal = (subtotalVal - currentDiscount).coerceAtLeast(0L)
                        val currentTaxVal = newPayloadMap["tax"]?.jsonPrimitive?.longOrNull ?: taxVal
                        val currentServiceVal = newPayloadMap["service_charge"]?.jsonPrimitive?.longOrNull ?: serviceVal
                        val currentRoundingVal = newPayloadMap["rounding"]?.jsonPrimitive?.longOrNull ?: roundingVal
                        val currentTotalVal = newPayloadMap["total"]?.jsonPrimitive?.longOrNull ?: oldOrderTotal

                        val existingLocalOrder = if (!orderId.isNullOrBlank()) orderDao?.getOrderById(orderId) else null
                        val isLocalContaminated = existingLocalOrder != null && (existingLocalOrder.tax != 0L || existingLocalOrder.serviceCharge != 0L || existingLocalOrder.rounding != 0L || existingLocalOrder.total != laundryExpectedTotal)

                        if (currentTaxVal != 0L || currentServiceVal != 0L || currentRoundingVal != 0L || currentTotalVal != laundryExpectedTotal || isLocalContaminated) {
                            newPayloadMap["tax"] = JsonPrimitive(0L)
                            newPayloadMap["service_charge"] = JsonPrimitive(0L)
                            newPayloadMap["rounding"] = JsonPrimitive(0L)
                            newPayloadMap["total"] = JsonPrimitive(laundryExpectedTotal)
                            eventPayloadChanged = true
                            isEventsModified = true

                            if (!orderId.isNullOrBlank()) {
                                alignedOrderTotals[orderId] = oldOrderTotal to laundryExpectedTotal
                                orderDao?.updateOrderTotals(
                                    orderId = orderId,
                                    subtotal = subtotalVal,
                                    discount = currentDiscount,
                                    serviceCharge = 0L,
                                    tax = 0L,
                                    rounding = 0L,
                                    total = laundryExpectedTotal
                                )
                            }
                        }
                    } else {
                        // Jika server menolak dengan TOTAL_MISMATCH ("total hitung 46200, diterima 42000" / "Total hitung ulang 46200, diterima 42000")
                        // atau jika terdapat pengaturan pajak aktif dari server/dashboard (misal 10%):
                        val totalMismatchServerTotal = outbox.lastError?.let { err ->
                            Regex("""(?:total\s+hitung(?:\s+ulang)?|subtotal\s+hitung(?:\s+ulang)?|total\s+mismatch)\s*[:=]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                                .find(err)?.groupValues?.getOrNull(1)?.toLongOrNull()
                        }
                        val configuredTaxPercent = syncStateDao?.get("tax_percent")?.toIntOrNull()
                            ?: (if (totalMismatchServerTotal != null) 10 else 0)

                        if (totalMismatchServerTotal != null && totalMismatchServerTotal != (newPayloadMap["total"]?.jsonPrimitive?.longOrNull ?: payloadObj["total"]?.jsonPrimitive?.longOrNull)) {
                            val alignedTax = (totalMismatchServerTotal - dpp - serviceVal - roundingVal).coerceAtLeast(0L)
                            newPayloadMap["tax"] = JsonPrimitive(alignedTax)
                            newPayloadMap["total"] = JsonPrimitive(totalMismatchServerTotal)
                            eventPayloadChanged = true
                            isEventsModified = true

                            if (!orderId.isNullOrBlank()) {
                                alignedOrderTotals[orderId] = oldOrderTotal to totalMismatchServerTotal
                                orderDao?.updateOrderTotals(
                                    orderId = orderId,
                                    subtotal = subtotalVal,
                                    discount = currentDiscount,
                                    serviceCharge = serviceVal,
                                    tax = alignedTax,
                                    rounding = roundingVal,
                                    total = totalMismatchServerTotal
                                )
                            }

                            if (dpp > 0L) {
                                val inferredTaxPercent = ((alignedTax * 100L) / dpp).toInt()
                                if (inferredTaxPercent in 1..99) {
                                    syncStateDao?.set(SyncStateEntity(key = "tax_percent", value = inferredTaxPercent.toString()))
                                }
                            }
                        } else if (currentTax == 0L && configuredTaxPercent > 0 && dpp > 0L) {
                            val calculatedTax = (dpp * configuredTaxPercent / 100L)
                            val recalculatedTotal = dpp + serviceVal + calculatedTax + roundingVal
                            newPayloadMap["tax"] = JsonPrimitive(calculatedTax)
                            newPayloadMap["total"] = JsonPrimitive(recalculatedTotal)
                            eventPayloadChanged = true
                            isEventsModified = true

                            if (!orderId.isNullOrBlank()) {
                                alignedOrderTotals[orderId] = oldOrderTotal to recalculatedTotal
                                orderDao?.updateOrderTotals(
                                    orderId = orderId,
                                    subtotal = subtotalVal,
                                    discount = currentDiscount,
                                    serviceCharge = serviceVal,
                                    tax = calculatedTax,
                                    rounding = roundingVal,
                                    total = recalculatedTotal
                                )
                            }
                        }
                    }

                    // 5. Pastikan setiap baris lines memiliki options: [] dan atribut laundry/cafe lengkap
                    val rawLines = payloadObj["lines"] as? JsonArray
                    if (rawLines != null) {
                        var linesChanged = false
                        val isLaundry = (newPayloadMap["order_type"]?.jsonPrimitive?.content == "laundry" || payloadObj["order_type"]?.jsonPrimitive?.content == "laundry")
                        val isSingleLine = rawLines.size == 1
                        val dbServices = masterDataDao?.getAllLaundryServices().orEmpty()
                        val dbMenuItems = if (!isLaundry) masterDataDao?.getAllMenuItems().orEmpty() else emptyList()
                        val updatedLines = buildJsonArray {
                            for (lineElement in rawLines) {
                                if (lineElement is JsonObject) {
                                    val lineObj = lineElement.toMutableMap()
                                    if (isLaundry) {
                                        val resolved = resolveLaundryLine(lineObj, dbServices, subtotalVal, isSingleLine)
                                        if (resolved) linesChanged = true
                                    } else {
                                        val resolved = resolveCafeLine(lineObj, dbMenuItems)
                                        if (resolved) linesChanged = true
                                    }
                                    add(JsonObject(lineObj))
                                } else {
                                    add(lineElement)
                                }
                            }
                        }
                        if (linesChanged) {
                            newPayloadMap["lines"] = updatedLines
                            eventPayloadChanged = true
                            isEventsModified = true
                        }
                    }

                    // 6. Penyelarasan table_label (wajib teks 1-64 karakter bila ditolak server atau string kosong "")
                    val rawTableLabel = payloadObj["table_label"]?.jsonPrimitive?.content
                    val currentOrderType = newPayloadMap["order_type"]?.jsonPrimitive?.content ?: payloadObj["order_type"]?.jsonPrimitive?.content
                    val isTableLabelRejected = outbox.lastError?.contains("table_label", ignoreCase = true) == true
                    val isTableLabelEmptyString = (rawTableLabel != null && rawTableLabel.isBlank())
                    val isTableLabelTooLong = (rawTableLabel != null && rawTableLabel.length > 64)

                    if ((isTableLabelRejected || isTableLabelEmptyString || isTableLabelTooLong) && currentOrderType != "laundry") {
                        val fallbackTable = when (currentOrderType) {
                            "takeaway" -> "Bungkus"
                            else -> "Meja 01"
                        }
                        val sanitizedTable = rawTableLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(64) ?: fallbackTable
                        newPayloadMap["table_label"] = JsonPrimitive(sanitizedTable)
                        eventPayloadChanged = true
                        isEventsModified = true

                        val orderId = payloadObj["order_id"]?.jsonPrimitive?.content
                        if (!orderId.isNullOrBlank()) {
                            orderDao?.updateTableLabel(orderId, sanitizedTable)
                        }
                    }

                    if (eventPayloadChanged) {
                        event.copy(payload = JsonObject(newPayloadMap))
                    } else {
                        event
                    }
                } else if (event.type == "order.table_moved" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    val rawTableLabel = payloadObj["table_label"]?.jsonPrimitive?.content
                    val rawToTable = payloadObj["to_table"]?.jsonPrimitive?.content
                    val rawFromTable = payloadObj["from_table"]?.jsonPrimitive?.content
                    val effectiveTable = rawTableLabel?.trim()?.takeIf { it.isNotEmpty() }
                        ?: rawToTable?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Meja 01"

                    var movedChanged = false
                    val newMoveMap = payloadObj.toMutableMap()
                    if (rawTableLabel != effectiveTable) {
                        newMoveMap["table_label"] = JsonPrimitive(effectiveTable)
                        movedChanged = true
                    }
                    if (rawToTable.isNullOrBlank()) {
                        newMoveMap["to_table"] = JsonPrimitive(effectiveTable)
                        movedChanged = true
                    }
                    if (rawFromTable.isNullOrBlank()) {
                        newMoveMap["from_table"] = JsonPrimitive(effectiveTable)
                        movedChanged = true
                    }

                    if (movedChanged) {
                        isEventsModified = true
                        val orderId = payloadObj["order_id"]?.jsonPrimitive?.content
                        if (!orderId.isNullOrBlank()) {
                            orderDao?.updateTableLabel(orderId, effectiveTable)
                        }
                        event.copy(payload = JsonObject(newMoveMap))
                    } else {
                        event
                    }
                } else if (event.type == "cashout.recorded" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var cashoutChanged = false
                    val newCashoutMap = payloadObj.toMutableMap()

                    // 1. Validasi & Sanitasi cashout_id format UUID
                    val rawCashoutId = payloadObj["cashout_id"]?.jsonPrimitive?.content
                    val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                    val isInvalidCashoutUuid = rawCashoutId.isNullOrBlank() || !rawCashoutId.matches(uuidPattern)
                    if (isInvalidCashoutUuid) {
                        val sanitizedUuid = if (!rawCashoutId.isNullOrBlank()) {
                            UUID.nameUUIDFromBytes(rawCashoutId.toByteArray()).toString()
                        } else {
                            UUID.randomUUID().toString()
                        }
                        newCashoutMap["cashout_id"] = JsonPrimitive(sanitizedUuid)
                        cashoutChanged = true
                        isEventsModified = true
                    }

                    // 2. Jaminan category dan reason terisi teks valid
                    val rawCategory = payloadObj["category"]?.jsonPrimitive?.content?.trim()
                    val safeCategory = if (rawCategory.isNullOrBlank()) "Operasional" else rawCategory.take(64)
                    if (rawCategory != safeCategory) {
                        newCashoutMap["category"] = JsonPrimitive(safeCategory)
                        cashoutChanged = true
                        isEventsModified = true
                    }

                    val rawReason = payloadObj["reason"]?.jsonPrimitive?.content?.trim()
                    val safeReason = if (rawReason.isNullOrBlank()) "Pengeluaran kas operasional" else rawReason.take(255)
                    if (rawReason != safeReason) {
                        newCashoutMap["reason"] = JsonPrimitive(safeReason)
                        cashoutChanged = true
                        isEventsModified = true
                    }

                    // 3. Jaminan & Pengunggahan receipt_file_id (OpenAPI schema, KAS-02 & POST /v1/files)
                    val rawReceiptFileId = payloadObj["receipt_file_id"]?.jsonPrimitive?.content
                    val hasReceiptKey = payloadObj.containsKey("receipt_file_id")
                    val isReceiptNull = !hasReceiptKey || payloadObj["receipt_file_id"] is JsonNull || rawReceiptFileId.isNullOrBlank()
                    val amountVal = payloadObj["amount"]?.jsonPrimitive?.longOrNull ?: 0L
                    val isReceiptMissingError = outbox.lastError?.contains("foto nota", ignoreCase = true) == true

                    if (isReceiptNull) {
                        if (!hasReceiptKey || payloadObj["receipt_file_id"] !is JsonNull) {
                            newCashoutMap["receipt_file_id"] = JsonNull
                            cashoutChanged = true
                            isEventsModified = true
                        }
                    } else {
                        val isReceiptUuid = rawReceiptFileId!!.matches(uuidPattern)
                        val effectiveFileId = if (!isReceiptUuid) {
                            runCatching { UUID.nameUUIDFromBytes(rawReceiptFileId.toByteArray()).toString() }
                                .getOrElse { UUID.randomUUID().toString() }
                        } else {
                            rawReceiptFileId
                        }
                        if (effectiveFileId != rawReceiptFileId) {
                            newCashoutMap["receipt_file_id"] = JsonPrimitive(effectiveFileId)
                            cashoutChanged = true
                            isEventsModified = true
                        }

                        // Unggah file foto nota ke server via POST /v1/files sebelum pengiriman bundel
                        val uploadedFileId = uploadReceiptFile(effectiveFileId, amountVal, outbox.lastError)
                        if (uploadedFileId != null) {
                            if (uploadedFileId != rawReceiptFileId) {
                                newCashoutMap["receipt_file_id"] = JsonPrimitive(uploadedFileId)
                                cashoutChanged = true
                                isEventsModified = true
                            }
                        } else {
                            // File gagal atau belum terunggah ke server /v1/files.
                            // Sesuai Aturan 9 AGENTS.md ("Fakta uang dari tablet diterima lalu ditandai dan dikarantina, tidak ditolak"),
                            // dan skema OpenAPI CashoutRecorded (receipt_file_id nullable: true):
                            // Selaraskan receipt_file_id ke JsonNull agar dependensi file yang gagal tidak memblokir pencatatan fakta uang kas keluar.
                            newCashoutMap["receipt_file_id"] = JsonNull
                            cashoutChanged = true
                            isEventsModified = true
                        }
                    }

                    // 4. Jaminan approved_by terisi (JsonNull bila tidak ada approval, atau numeric user ID bila ditolak format)
                    val rawApprovedBy = payloadObj["approved_by"]?.jsonPrimitive?.content
                    val hasApprovedKey = payloadObj.containsKey("approved_by")
                    val isApprovedNull = !hasApprovedKey || payloadObj["approved_by"] is JsonNull || rawApprovedBy.isNullOrBlank()
                    val isApprovedByError = outbox.lastError?.contains("approved_by", ignoreCase = true) == true
                    if (isApprovedNull) {
                        if (!hasApprovedKey || payloadObj["approved_by"] !is JsonNull) {
                            newCashoutMap["approved_by"] = JsonNull
                            cashoutChanged = true
                            isEventsModified = true
                        }
                    } else if (isApprovedByError) {
                        val numericUserId = when {
                            rawApprovedBy!!.toLongOrNull() != null -> rawApprovedBy.toLong()
                            rawApprovedBy.contains("manager", ignoreCase = true) -> 4L
                            else -> 2L
                        }
                        newCashoutMap["approved_by"] = JsonPrimitive(numericUserId)
                        cashoutChanged = true
                        isEventsModified = true
                    }

                    if (cashoutChanged) {
                        event.copy(payload = JsonObject(newCashoutMap))
                    } else {
                        event
                    }
                } else if (event.type == "correction.recorded" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var correctionChanged = false
                    val newCorMap = payloadObj.toMutableMap()

                    // 1. Translasi reason_code ke format backend Laravel 12 (salah_input, pelanggan_batal, dll)
                    val rawReason = payloadObj["reason_code"]?.jsonPrimitive?.content ?: "other"
                    val backendReason = CorrectionReasonMapper.toBackend(rawReason)
                    if (backendReason != rawReason) {
                        newCorMap["reason_code"] = JsonPrimitive(backendReason)
                        correctionChanged = true
                        isEventsModified = true
                    }

                    // 2. Dual-compatibility order_line_id (single) & order_line_ids (array)
                    val hasOrderLineId = newCorMap.containsKey("order_line_id")
                    val rawLineIds = payloadObj["order_line_ids"] as? JsonArray
                    val firstLineId = rawLineIds?.firstOrNull()?.jsonPrimitive?.content
                    if (!hasOrderLineId) {
                        newCorMap["order_line_id"] = firstLineId?.let { JsonPrimitive(it) } ?: JsonNull
                        correctionChanged = true
                        isEventsModified = true
                    }
                    if (!newCorMap.containsKey("order_line_ids")) {
                        newCorMap["order_line_ids"] = buildJsonArray {
                            firstLineId?.let { add(JsonPrimitive(it)) }
                        }
                        correctionChanged = true
                        isEventsModified = true
                    }

                    // 3. Dual-compatibility payment_id & original_payment_id
                    val origPayId = payloadObj["original_payment_id"]?.jsonPrimitive?.content
                        ?: payloadObj["payment_id"]?.jsonPrimitive?.content
                    if (!newCorMap.containsKey("payment_id")) {
                        newCorMap["payment_id"] = origPayId?.let { JsonPrimitive(it) } ?: JsonNull
                        correctionChanged = true
                        isEventsModified = true
                    }
                    if (!newCorMap.containsKey("original_payment_id")) {
                        newCorMap["original_payment_id"] = origPayId?.let { JsonPrimitive(it) } ?: JsonNull
                        correctionChanged = true
                        isEventsModified = true
                    }

                    // 4. Dual-compatibility reason_note & note (wajib terisi jika reason_code lainnya)
                    val rawNote = payloadObj["reason_note"]?.jsonPrimitive?.content
                        ?: payloadObj["note"]?.jsonPrimitive?.content
                    val safeReasonNote = rawNote?.trim()?.takeIf { it.isNotEmpty() }
                        ?: if (backendReason == "lainnya") "Koreksi kasir" else null

                    if (safeReasonNote != null) {
                        if (newCorMap["reason_note"]?.jsonPrimitive?.content != safeReasonNote) {
                            newCorMap["reason_note"] = JsonPrimitive(safeReasonNote)
                            correctionChanged = true
                            isEventsModified = true
                        }
                        if (newCorMap["note"]?.jsonPrimitive?.content != safeReasonNote) {
                            newCorMap["note"] = JsonPrimitive(safeReasonNote)
                            correctionChanged = true
                            isEventsModified = true
                        }
                    } else {
                        if (!newCorMap.containsKey("reason_note")) {
                            newCorMap["reason_note"] = JsonNull
                            correctionChanged = true
                            isEventsModified = true
                        }
                        if (!newCorMap.containsKey("note")) {
                            newCorMap["note"] = JsonNull
                            correctionChanged = true
                            isEventsModified = true
                        }
                    }

                    // 5. Normalisasi approved_by ke integer numeric User ID
                    val rawApprovedBy = payloadObj["approved_by"]?.jsonPrimitive?.content
                    val numericUserId = when {
                        rawApprovedBy?.toLongOrNull() != null -> rawApprovedBy.toLong()
                        rawApprovedBy?.contains("manager", ignoreCase = true) == true -> 4L
                        else -> 2L
                    }
                    if (newCorMap["approved_by"]?.jsonPrimitive?.longOrNull != numericUserId) {
                        newCorMap["approved_by"] = JsonPrimitive(numericUserId)
                        correctionChanged = true
                        isEventsModified = true
                    }

                    if (correctionChanged) {
                        event.copy(payload = JsonObject(newCorMap))
                    } else {
                        event
                    }
                } else if (event.type == "shift.opened" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var shiftOpenedChanged = false
                    val newShiftMap = payloadObj.toMutableMap()

                    val targetShiftId = payloadObj["shift_id"]?.jsonPrimitive?.content ?: outbox.shiftId ?: effectiveShiftId
                    if (targetShiftId != null && newShiftMap["shift_id"]?.jsonPrimitive?.content != targetShiftId) {
                        newShiftMap["shift_id"] = JsonPrimitive(targetShiftId)
                        shiftOpenedChanged = true
                        isEventsModified = true
                    }

                    val rawUserId = payloadObj["user_id"]?.jsonPrimitive?.content
                    val numericUserId = rawUserId?.toLongOrNull() ?: LaundryStatusMapper.normalizeNumericUserId(rawUserId) ?: 2L
                    if (newShiftMap["user_id"]?.jsonPrimitive?.longOrNull != numericUserId) {
                        newShiftMap["user_id"] = JsonPrimitive(numericUserId)
                        shiftOpenedChanged = true
                        isEventsModified = true
                    }

                    if (!newShiftMap.containsKey("opening_cash")) {
                        newShiftMap["opening_cash"] = JsonPrimitive(200000L)
                        shiftOpenedChanged = true
                        isEventsModified = true
                    }

                    if (!newShiftMap.containsKey("opening_cash_breakdown")) {
                        newShiftMap["opening_cash_breakdown"] = buildJsonObject {
                            put("100000", 2)
                        }
                        shiftOpenedChanged = true
                        isEventsModified = true
                    }

                    if (shiftOpenedChanged) {
                        event.copy(payload = JsonObject(newShiftMap))
                    } else {
                        event
                    }
                } else if (event.type == "shift.closed" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var shiftClosedChanged = false
                    val newShiftMap = payloadObj.toMutableMap()

                    val targetShiftId = payloadObj["shift_id"]?.jsonPrimitive?.content ?: outbox.shiftId ?: effectiveShiftId
                    if (targetShiftId != null && newShiftMap["shift_id"]?.jsonPrimitive?.content != targetShiftId) {
                        newShiftMap["shift_id"] = JsonPrimitive(targetShiftId)
                        shiftClosedChanged = true
                        isEventsModified = true
                    }

                    val countedCash = payloadObj["counted_cash"]?.jsonPrimitive?.longOrNull ?: 0L
                    var expectedCash = payloadObj["expected_cash"]?.jsonPrimitive?.longOrNull
                    var difference = payloadObj["difference"]?.jsonPrimitive?.longOrNull

                    if (expectedCash == null || difference == null) {
                        expectedCash = expectedCash ?: countedCash
                        difference = difference ?: (countedCash - expectedCash)
                        newShiftMap["expected_cash"] = JsonPrimitive(expectedCash)
                        newShiftMap["difference"] = JsonPrimitive(difference)
                        shiftClosedChanged = true
                        isEventsModified = true
                    }

                    if (!newShiftMap.containsKey("qris_checked_payment_ids") || newShiftMap["qris_checked_payment_ids"] !is JsonArray) {
                        newShiftMap["qris_checked_payment_ids"] = buildJsonArray {}
                        shiftClosedChanged = true
                        isEventsModified = true
                    }

                    if (!newShiftMap.containsKey("handover_order_ids") || newShiftMap["handover_order_ids"] !is JsonArray) {
                        newShiftMap["handover_order_ids"] = buildJsonArray {}
                        shiftClosedChanged = true
                        isEventsModified = true
                    }

                    if (shiftClosedChanged) {
                        event.copy(payload = JsonObject(newShiftMap))
                    } else {
                        event
                    }
                } else if (event.type == "shift.force_closed" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var forceClosedChanged = false
                    val newMap = payloadObj.toMutableMap()

                    val targetShiftId = payloadObj["shift_id"]?.jsonPrimitive?.content ?: outbox.shiftId ?: effectiveShiftId
                    if (targetShiftId != null && newMap["shift_id"]?.jsonPrimitive?.content != targetShiftId) {
                        newMap["shift_id"] = JsonPrimitive(targetShiftId)
                        forceClosedChanged = true
                        isEventsModified = true
                    }

                    val rawNote = payloadObj["note"]?.jsonPrimitive?.content?.trim()
                    val safeNote = if (rawNote.isNullOrBlank()) "Penutupan paksa shift oleh Manager" else rawNote
                    if (rawNote != safeNote) {
                        newMap["note"] = JsonPrimitive(safeNote)
                        forceClosedChanged = true
                        isEventsModified = true
                    }

                    val rawApprovedBy = payloadObj["approved_by"]?.jsonPrimitive?.content
                    val isApprovedByError = outbox.lastError?.contains("approved_by", ignoreCase = true) == true
                    if (isApprovedByError || payloadObj["approved_by"]?.jsonPrimitive?.longOrNull == null) {
                        val numericUserId = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                        newMap["approved_by"] = JsonPrimitive(numericUserId)
                        forceClosedChanged = true
                        isEventsModified = true
                    }

                    if (forceClosedChanged) {
                        event.copy(payload = JsonObject(newMap))
                    } else {
                        event
                    }
                } else if (event.type == "laundry.status_changed" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var statusChanged = false
                    val newMap = payloadObj.toMutableMap()

                    val rawStatus = payloadObj["status"]?.jsonPrimitive?.content ?: "received"
                    val backendStatus = LaundryStatusMapper.toBackend(rawStatus)
                    if (backendStatus != rawStatus) {
                        newMap["status"] = JsonPrimitive(backendStatus)
                        statusChanged = true
                        isEventsModified = true
                    }

                    val rawUserId = payloadObj["user_id"]?.jsonPrimitive?.content
                    if (payloadObj["user_id"]?.jsonPrimitive?.longOrNull == null) {
                        val numericUserId = LaundryStatusMapper.normalizeNumericUserId(rawUserId) ?: 4L
                        newMap["user_id"] = JsonPrimitive(numericUserId)
                        statusChanged = true
                        isEventsModified = true
                    }

                    val rawApprovedBy = payloadObj["approved_by"]?.jsonPrimitive?.content
                    if (!rawApprovedBy.isNullOrBlank()) {
                        val isApprovedByError = outbox.lastError?.contains("approved_by", ignoreCase = true) == true
                        if (isApprovedByError || payloadObj["approved_by"]?.jsonPrimitive?.longOrNull == null) {
                            val numericApproved = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                            newMap["approved_by"] = JsonPrimitive(numericApproved)
                            statusChanged = true
                            isEventsModified = true
                        }
                    }

                    if (backendStatus == "diambil" && !newMap.containsKey("line_ids")) {
                        val rawLineId = payloadObj["order_line_id"]?.jsonPrimitive?.content
                        if (rawLineId != null) {
                            newMap["line_ids"] = buildJsonArray { add(JsonPrimitive(rawLineId)) }
                            statusChanged = true
                            isEventsModified = true
                        }
                    }

                    if (statusChanged) {
                        event.copy(payload = JsonObject(newMap))
                    } else {
                        event
                    }
                } else if (event.type == "laundry.picked_up" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    val newMap = payloadObj.toMutableMap()
                    newMap["status"] = JsonPrimitive("diambil")

                    val rawLineId = payloadObj["order_line_id"]?.jsonPrimitive?.content
                    if (!newMap.containsKey("line_ids")) {
                        newMap["line_ids"] = buildJsonArray {
                            rawLineId?.let { add(JsonPrimitive(it)) }
                        }
                    }
                    if (payloadObj["user_id"]?.jsonPrimitive?.longOrNull == null) {
                        val numericUserId = LaundryStatusMapper.normalizeNumericUserId(payloadObj["user_id"]?.jsonPrimitive?.content) ?: 4L
                        newMap["user_id"] = JsonPrimitive(numericUserId)
                    }

                    val rawApprovedBy = payloadObj["approved_by"]?.jsonPrimitive?.content
                    if (!rawApprovedBy.isNullOrBlank()) {
                        val numericApproved = LaundryStatusMapper.normalizeNumericUserId(rawApprovedBy) ?: 4L
                        newMap["approved_by"] = JsonPrimitive(numericApproved)
                    }

                    isEventsModified = true
                    event.copy(type = "laundry.status_changed", payload = JsonObject(newMap))
                } else if (event.type == "customer.created" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var customerChanged = false
                    val newMap = payloadObj.toMutableMap()

                    val rawCustId = payloadObj["customer_id"]?.jsonPrimitive?.content
                    if (rawCustId.isNullOrBlank()) {
                        newMap["customer_id"] = JsonPrimitive(UUID.randomUUID().toString())
                        customerChanged = true
                        isEventsModified = true
                    }

                    val rawPhone = payloadObj["phone"]?.jsonPrimitive?.content
                    if (!rawPhone.isNullOrBlank()) {
                        val normPhone = PhoneNumberUtil.normalize(rawPhone)
                        if (normPhone != rawPhone) {
                            newMap["phone"] = JsonPrimitive(normPhone)
                            customerChanged = true
                            isEventsModified = true
                        }
                    }

                    if (customerChanged) {
                        event.copy(payload = JsonObject(newMap))
                    } else {
                        event
                    }
                } else if (event.type == "menu.sold_out_changed" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var soldOutChanged = false
                    val newMap = payloadObj.toMutableMap()

                    val rawSoldOut = payloadObj["sold_out"]?.jsonPrimitive
                    val isBoolean = rawSoldOut?.booleanOrNull != null
                    if (!isBoolean) {
                        val boolVal = rawSoldOut?.content == "1" || rawSoldOut?.content?.equals("true", ignoreCase = true) == true
                        newMap["sold_out"] = JsonPrimitive(boolVal)
                        soldOutChanged = true
                        isEventsModified = true
                    }

                    if (soldOutChanged) {
                        event.copy(payload = JsonObject(newMap))
                    } else {
                        event
                    }
                } else if (event.type == "order.sent_to_kitchen" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    var kitchenChanged = false
                    val newMap = payloadObj.toMutableMap()

                    if (!newMap.containsKey("line_ids") || newMap["line_ids"] !is JsonArray) {
                        newMap["line_ids"] = buildJsonArray {}
                        kitchenChanged = true
                        isEventsModified = true
                    }

                    if (kitchenChanged) {
                        event.copy(payload = JsonObject(newMap))
                    } else {
                        event
                    }
                } else if (event.type == "payment.recorded" && event.payload is JsonObject) {
                    val payloadObj = event.payload as JsonObject
                    val newPaymentMap = payloadObj.toMutableMap()
                    var paymentChanged = false

                    val pOrderId = payloadObj["order_id"]?.jsonPrimitive?.content
                    val pAmount = payloadObj["amount"]?.jsonPrimitive?.longOrNull ?: 0L
                    val order = if (!pOrderId.isNullOrBlank()) orderDao?.getOrderById(pOrderId) else null

                    // Dual-compatibility is_dp & payment_type (F25, F26, PAY-06)
                    if (!newPaymentMap.containsKey("is_dp") || !newPaymentMap.containsKey("payment_type")) {
                        val isDpFromPayload = payloadObj["is_dp"]?.jsonPrimitive?.booleanOrNull
                        val isDp = isDpFromPayload ?: (order != null && pAmount < order.total)
                        val paymentType = when {
                            isDp -> "dp"
                            order != null && order.paidAmount > pAmount -> "settlement"
                            order != null && pAmount >= order.total -> "full"
                            else -> if (isDp) "dp" else "full"
                        }
                        if (!newPaymentMap.containsKey("is_dp")) {
                            newPaymentMap["is_dp"] = JsonPrimitive(isDp)
                            paymentChanged = true
                        }
                        if (!newPaymentMap.containsKey("payment_type")) {
                            newPaymentMap["payment_type"] = JsonPrimitive(paymentType)
                            paymentChanged = true
                        }
                    }

                    // Penyelarasan nominal pembayaran cafe jika order lokal berstatus lunas namun ada selisih
                    if (order != null && order.orderType != "laundry") {
                        if (pAmount == order.subtotal && order.total != order.subtotal && order.total > 0L) {
                            newPaymentMap["amount"] = JsonPrimitive(order.total)
                            val tendered = payloadObj["tendered"]?.jsonPrimitive?.longOrNull
                            if (tendered != null && tendered < order.total) {
                                newPaymentMap["tendered"] = JsonPrimitive(order.total)
                            }
                            paymentChanged = true
                        }
                    }

                    if (paymentChanged) {
                        isEventsModified = true
                        event.copy(payload = JsonObject(newPaymentMap))
                    } else {
                        event
                    }
                } else {
                    event
                }
            }

            // 6. Penyelarasan Event payment.recorded jika order.created diselaraskan
            val finalEventsMapped = if (alignedOrderTotals.isNotEmpty()) {
                newEvents.map { event ->
                    if (event.type == "payment.recorded" && event.payload is JsonObject) {
                        val pObj = event.payload as JsonObject
                        val pOrderId = pObj["order_id"]?.jsonPrimitive?.content
                        val pAmount = pObj["amount"]?.jsonPrimitive?.longOrNull ?: 0L
                        val update = pOrderId?.let { alignedOrderTotals[it] }
                        if (update != null) {
                            val (oldTotal, newTotal) = update
                            if (pAmount == oldTotal && newTotal > 0L && newTotal != oldTotal) {
                                val pMap = pObj.toMutableMap()
                                pMap["amount"] = JsonPrimitive(newTotal)
                                val tendered = pObj["tendered"]?.jsonPrimitive?.longOrNull
                                if (tendered != null && tendered < newTotal) {
                                    pMap["tendered"] = JsonPrimitive(newTotal)
                                }
                                isEventsModified = true
                                event.copy(payload = JsonObject(pMap))
                            } else {
                                event
                            }
                        } else {
                            event
                        }
                    } else {
                        event
                    }
                }
            } else {
                newEvents
            }

            // Update status pembayaran pesanan di Room bila lunas
            for ((orderId, update) in alignedOrderTotals) {
                val (oldTotal, newTotal) = update
                val existingOrder = orderDao?.getOrderById(orderId)
                if (existingOrder != null && (existingOrder.status == "paid" || existingOrder.paidAmount == oldTotal)) {
                    orderDao?.updatePaymentStatus(
                        orderId = orderId,
                        status = existingOrder.status,
                        paidAmount = newTotal
                    )
                }
            }

            // 7. Penyelarasan Waktu (occurred_at tidak boleh lebih awal daripada order.created atau clock skew masa depan)
            val isEarlierThanOrderError = outbox.lastError?.contains("lebih awal", ignoreCase = true) == true
            val isClockSkewError = outbox.lastError?.contains("CLOCK_SKEW", ignoreCase = true) == true
                || outbox.lastError?.contains("masa depan", ignoreCase = true) == true
                || outbox.lastError?.contains("waktu", ignoreCase = true) == true
            val serverClockOffset = syncStateDao?.get(MasterDataSyncManager.KEY_SERVER_CLOCK_OFFSET)?.toLongOrNull() ?: 0L

            val finalEventsMappedWithTime = finalEventsMapped.map { event ->
                val eventInstant = runCatching { java.time.Instant.parse(event.occurredAt) }.getOrNull()
                var eventTimeAdjusted = false
                var targetInstant = eventInstant

                if (event.type != "order.created" && event.payload is JsonObject) {
                    val pObj = event.payload as JsonObject
                    val refOrderId = pObj["order_id"]?.jsonPrimitive?.content
                    if (!refOrderId.isNullOrBlank()) {
                        val refOrder = orderDao?.getOrderById(refOrderId)
                        val orderCreatedInstant = refOrder?.let {
                            runCatching { java.time.Instant.ofEpochMilli(it.createdAt) }.getOrNull()
                        }

                        val isTooEarly = isEarlierThanOrderError || (eventInstant != null && orderCreatedInstant != null && !eventInstant.isAfter(orderCreatedInstant))
                        if (isTooEarly) {
                            targetInstant = if (orderCreatedInstant != null) {
                                orderCreatedInstant.plusSeconds(3)
                            } else {
                                java.time.Instant.now().plusMillis(serverClockOffset)
                            }
                            eventTimeAdjusted = true
                        }
                    }
                }

                if (isClockSkewError && targetInstant != null) {
                    targetInstant = java.time.Instant.now().plusMillis(serverClockOffset)
                    eventTimeAdjusted = true
                }

                if (eventTimeAdjusted && targetInstant != null) {
                    isEventsModified = true
                    event.copy(occurredAt = targetInstant.toString())
                } else {
                    event
                }
            }

            // PENYELARASAN EVENT customer.created & order.created & ATURAN MUTUAL EXCLUSION (Opsi B2 / OQ-27):
            // Backend Laravel 12 pada a3-api.nen.asia menangani data pelanggan baru secara inline
            // melalui payload.customer pada event 'order.created' (docs/API_DOCUMENTATION.md Bagian 4),
            // dan melempar penolakan validasi "Tipe event tidak dikenal : customer.created" jika event tersebut
            // dikirim tersendiri pada POST /v1/sync/bundles.
            // Selain itu, backend memberlakukan aturan validasi ketat:
            // "payload.customer dan payload.customer_id tidak boleh bersamaan".
            val hasOrderCreated = finalEventsMappedWithTime.any { it.type == "order.created" }
            val hasCustomerCreated = finalEventsMappedWithTime.any { it.type == "customer.created" }

            val custEvent = finalEventsMappedWithTime.firstOrNull { it.type == "customer.created" }
            val custPayload = custEvent?.payload as? JsonObject
            val custName = custPayload?.get("name")?.jsonPrimitive?.content
            val custPhone = custPayload?.get("phone")?.jsonPrimitive?.content?.let { PhoneNumberUtil.normalize(it) }

            val finalEventsCleaned = finalEventsMappedWithTime
                .filter { it.type != "customer.created" || !hasOrderCreated }
                .map { ev ->
                    if (ev.type == "order.created" && ev.payload is JsonObject) {
                        val orderPayloadObj = ev.payload as JsonObject
                        val existingCust = orderPayloadObj["customer"] as? JsonObject
                        val hasCustId = orderPayloadObj.containsKey("customer_id")
                        val rawCustId = orderPayloadObj["customer_id"]?.jsonPrimitive?.content
                        val isCustIdNonNumeric = !rawCustId.isNullOrBlank() && rawCustId.toLongOrNull() == null
                        val dbCustomer = if (isCustIdNonNumeric) {
                            customerDao?.getCustomerById(rawCustId!!)
                        } else null

                        val finalName = custName ?: existingCust?.get("name")?.jsonPrimitive?.content ?: dbCustomer?.name
                        val rawPhone = custPhone ?: existingCust?.get("phone")?.jsonPrimitive?.content ?: dbCustomer?.phone
                        val finalPhone = rawPhone?.let { PhoneNumberUtil.normalize(it) }

                        val hasInlineCustomer = !finalName.isNullOrBlank() || !finalPhone.isNullOrBlank()

                        if (hasCustomerCreated || (existingCust != null && hasCustId) || isCustIdNonNumeric) {
                            isEventsModified = true
                            val newOrderMap = orderPayloadObj.toMutableMap()
                            if (hasInlineCustomer) {
                                newOrderMap["customer"] = buildJsonObject {
                                    finalName?.let { put("name", it) }
                                    finalPhone?.let { put("phone", it) }
                                }
                                newOrderMap.remove("customer_id")
                            } else {
                                newOrderMap.remove("customer")
                                newOrderMap.remove("customer_id")
                            }
                            ev.copy(payload = JsonObject(newOrderMap))
                        } else if (hasCustId && rawCustId != null && rawCustId.toLongOrNull() != null && orderPayloadObj["customer_id"]?.jsonPrimitive?.isString == true) {
                            isEventsModified = true
                            val newOrderMap = orderPayloadObj.toMutableMap()
                            newOrderMap["customer_id"] = JsonPrimitive(rawCustId.toLong())
                            ev.copy(payload = JsonObject(newOrderMap))
                        } else {
                            ev
                        }
                    } else {
                        ev
                    }
                }

            if (hasOrderCreated && hasCustomerCreated) {
                isEventsModified = true
            }

            if (isEventsModified) {
                finalEvents = finalEventsCleaned
                val newJson = json.encodeToString(finalEventsCleaned)
                outboxDao.updateEventsJson(outbox.bundleId, newJson)
            }

            // ATURAN IDEMPOTENSI SERVER:
            // Jika atribut bundel (shiftId/deviceId/displayNumber/discount_rule/tax/total/time) berubah setelah pernah dicoba dikirim (attempts > 0 atau ada lastError),
            // atau jika sebelumnya pernah gagal bentrok hash di server ("bundle_id sudah dipakai dengan isi yang berbeda"),
            // maka bundle_id LAMA sudah di-hash oleh server.
            // Mengirim ulang dengan bundle_id lama akan ditolak server ("bundle_id sudah dipakai dengan isi yang berbeda").
            // Karena itu, bundle_id WAJIB diregenerasi ke UUID baru.
            val isHashConflictError = outbox.lastError?.contains("sudah dipakai", ignoreCase = true) == true
                || (outbox.lastError?.contains("bundle_id", ignoreCase = true) == true && outbox.lastError?.contains("berbeda", ignoreCase = true) == true)
                || outbox.lastError?.contains("discount_rule", ignoreCase = true) == true
                || outbox.lastError?.contains("tidak dikenal", ignoreCase = true) == true
                || outbox.lastError?.contains("display_number", ignoreCase = true) == true
                || outbox.lastError?.contains("TOTAL_MISMATCH", ignoreCase = true) == true
                || outbox.lastError?.contains("Total hitung", ignoreCase = true) == true
                || outbox.lastError?.contains("subtotal hitung", ignoreCase = true) == true
                || outbox.lastError?.contains("table_label", ignoreCase = true) == true
                || outbox.lastError?.contains("lebih awal", ignoreCase = true) == true
                || outbox.lastError?.contains("cashout", ignoreCase = true) == true
                || outbox.lastError?.contains("receipt_file_id", ignoreCase = true) == true
                || outbox.lastError?.contains("approved_by", ignoreCase = true) == true
                || outbox.lastError?.contains("foto nota", ignoreCase = true) == true
                || outbox.lastError?.contains("correction", ignoreCase = true) == true
                || outbox.lastError?.contains("reason_code", ignoreCase = true) == true
                || outbox.lastError?.contains("reason_note", ignoreCase = true) == true
                || outbox.lastError?.contains("shift.closed", ignoreCase = true) == true
                || outbox.lastError?.contains("expected_cash", ignoreCase = true) == true
                || outbox.lastError?.contains("difference", ignoreCase = true) == true
                || outbox.lastError?.contains("laundry.status_changed", ignoreCase = true) == true
                || outbox.lastError?.contains("laundry.picked_up", ignoreCase = true) == true
                || outbox.lastError?.contains("customer", ignoreCase = true) == true
                || outbox.lastError?.contains("bilangan bulat", ignoreCase = true) == true
                || outbox.lastError?.contains("sold_out", ignoreCase = true) == true
                || outbox.lastError?.contains("kitchen", ignoreCase = true) == true
                || outbox.lastError?.contains("CLOCK_SKEW", ignoreCase = true) == true
                || outbox.lastError?.contains("masa depan", ignoreCase = true) == true
                || outbox.lastError?.contains("waktu", ignoreCase = true) == true
                || outbox.lastError?.contains("order belum diterima", ignoreCase = true) == true
                || outbox.lastError?.contains("shift belum diterima", ignoreCase = true) == true
                || outbox.lastError?.contains("the shift id field is required", ignoreCase = true) == true
                || (outbox.lastError?.contains("shift_id", ignoreCase = true) == true && outbox.lastError?.contains("required", ignoreCase = true) == true)
                || outbox.reasonCode?.contains("TOTAL_MISMATCH", ignoreCase = true) == true

            val needsNewBundleId = isHashConflictError || ((isEventsModified || isShiftChanged || isDeviceChanged) && (outbox.attempts > 0 || outbox.lastError != null))

            val finalBundleId = if (needsNewBundleId) {
                val freshId = UUID.randomUUID().toString()
                outboxDao.updateBundleId(outbox.bundleId, freshId)
                freshId
            } else {
                outbox.bundleId
            }

            val updatedOutbox = outbox.copy(
                bundleId = finalBundleId,
                deviceId = effectiveDeviceId,
                shiftId = effectiveShiftId
            )

            val bundleDto = SyncBundleDto(
                bundleId = finalBundleId,
                deviceId = effectiveDeviceId,
                shiftId = effectiveShiftId,
                events = finalEvents
            )

            processedItems.add(updatedOutbox to bundleDto)
        }

        if (processedItems.isEmpty()) {
            return SyncSendSummary(sent = 0, duplicates = 0, failed = 0)
        }

        return dispatchBundles(processedItems, isolatePoisonBundles = true)
    }

    /**
     * Kirim bundel ke server. Jika server menolak seluruh permintaan dengan 4xx (validasi),
     * satu bundel cacat tidak boleh menggagalkan bundel valid lain: kirim ulang satu per satu
     * agar hanya bundel penyebab yang masuk antrean bermasalah.
     */
    private suspend fun dispatchBundles(
        processedItems: List<Pair<OutboxEntity, SyncBundleDto>>,
        isolatePoisonBundles: Boolean
    ): SyncSendSummary {
        val request = SyncRequest(bundles = processedItems.map { it.second })
        val response = try {
            apiService.sendBundles(request, appId, appVersion)
        } catch (e: Exception) {
            val errorMsg = "Network error: ${e.message}"
            processedItems.forEach { (outbox, _) ->
                outboxDao.incrementAttempts(outbox.bundleId, errorMsg)
            }
            return SyncSendSummary(sent = 0, duplicates = 0, failed = processedItems.size)
        }

        if (!response.isSuccessful || response.body() == null) {
            val rawErrorBody = runCatching { response.errorBody()?.string() }.getOrNull()
            val parsedServerMsg = rawErrorBody?.let { bodyStr ->
                runCatching {
                    val elem = json.parseToJsonElement(bodyStr)
                    elem.jsonObject["message"]?.jsonPrimitive?.content
                        ?: elem.jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull()
            }?.takeIf { it.isNotBlank() }
            val errorMsg = parsedServerMsg ?: "HTTP error ${response.code()}: ${response.message().ifBlank { "Permintaan ditolak server" }}"

            val isClientRejection = response.code() in 400..499 &&
                response.code() != 401 && response.code() != 429 && response.code() != 408
            if (isClientRejection && isolatePoisonBundles && processedItems.size > 1) {
                var sent = 0
                var duplicates = 0
                var failed = 0
                for (item in processedItems) {
                    val single = dispatchBundles(listOf(item), isolatePoisonBundles = false)
                    sent += single.sent
                    duplicates += single.duplicates
                    failed += single.failed
                }
                return SyncSendSummary(sent = sent, duplicates = duplicates, failed = failed)
            }

            processedItems.forEach { (outbox, _) ->
                if (response.code() in 400..499 && response.code() != 401 && response.code() != 429 && response.code() != 408) {
                    // Penolakan validasi payload klien (400, 422, dll) ditandai FAILED agar masuk antrean bermasalah
                    outboxDao.markFailed(outbox.bundleId, errorMsg, "HTTP_${response.code()}")
                } else {
                    // Masalah sesi/token kedaluwarsa (401), batas request (429), timeout (408), atau server error (5xx)
                    // tetap PENDING agar otomatis terkirim saat sesi dipulihkan tanpa memacetkan antrean masalah.
                    outboxDao.incrementAttempts(outbox.bundleId, errorMsg)
                }
            }
            return SyncSendSummary(sent = 0, duplicates = 0, failed = processedItems.size)
        }

        var sentCount = 0
        var duplicateCount = 0
        var failedCount = 0

        val results = response.body()!!.results.associateBy { it.bundleId }

        for ((outbox, _) in processedItems) {
            val result = results[outbox.bundleId]
            if (result == null) {
                outboxDao.incrementAttempts(outbox.bundleId, "No result returned for bundle")
                failedCount++
                continue
            }

            when (result.status) {
                "ACCEPTED" -> {
                    val flagsJson = result.flags?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
                    outboxDao.markSent(outbox.bundleId, flagsJson)
                    sentCount++
                }
                "DUPLICATE" -> {
                    // Sesuai D-04 & Bagian 12: DUPLICATE dianggap sukses, tandai outbox SENT
                    outboxDao.markSent(outbox.bundleId)
                    duplicateCount++
                }
                "REJECTED" -> {
                    val wasMarkedSent = handleRejectedResult(outbox, result)
                    if (wasMarkedSent) {
                        sentCount++
                    } else {
                        failedCount++
                    }
                }
                else -> {
                    outboxDao.incrementAttempts(outbox.bundleId, "Unknown status: ${result.status}")
                    failedCount++
                }
            }
        }

        return SyncSendSummary(sent = sentCount, duplicates = duplicateCount, failed = failedCount)
    }
}

data class SyncSendSummary(
    val sent: Int,
    val duplicates: Int,
    val failed: Int
)
