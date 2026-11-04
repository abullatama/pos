package id.a3.core.sync

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.DiscountPresetEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.MenuOptionEntity
import id.a3.core.data.local.entity.SyncStateEntity
import id.a3.core.sync.api.SyncMasterApiService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

sealed class BootstrapResult {
    data class Completed(val deltaCursor: String?) : BootstrapResult()
    data class Interrupted(val lastSavedCursor: String?, val error: String) : BootstrapResult()
}

sealed class DeltaResult {
    data class Success(val newCursor: String, val count: Int) : DeltaResult()
    data class Rebootstrapped(val newDeltaCursor: String?) : DeltaResult()
    data class Error(val code: Int, val message: String) : DeltaResult()
}

/**
 * Mengelola sinkronisasi data master (Menu Cafe, Layanan Laundry) dari server ke Room:
 * 1. Resumable Bootstrap: jika koneksi terputus di tengah, dilanjutkan dari next_cursor terakhir.
 * 2. Delta Sync: mengambil perubahan bertahap (upsert & delete) sejak delta_cursor.
 * 3. Cursor Expired (HTTP 410): jika cursor kedaluwarsa, otomatis melakukan bootstrap ulang dari awal.
 */
class MasterDataSyncManager(
    private val syncStateDao: SyncStateDao,
    private val masterDataDao: MasterDataDao,
    private val apiService: SyncMasterApiService,
    private val appId: String = "cafe",
    private val appVersion: String = "1.0.0"
) {

    companion object {
        const val KEY_BOOTSTRAP_CURSOR = "bootstrap_cursor"
        const val KEY_DELTA_CURSOR = "delta_cursor"
        const val KEY_BOOTSTRAP_DONE = "bootstrap_done"
        const val KEY_TAX_PERCENT = "tax_percent"
        const val KEY_TAX_MODE = "tax_mode"
        const val KEY_SERVICE_PERCENT = "service_charge_percent"
        const val KEY_ROUNDING_STEP = "rounding_step"
        const val KEY_SERVER_CLOCK_OFFSET = "server_clock_offset_ms"
    }

    /**
     * Mengambil waktu server terkini dari GET /v1/time, menghitung selisih waktu (clock skew),
     * dan menyimpannya secara persisten ke tabel sync_state di Room.
     * Offset = serverTimeMs - deviceLocalTimeMs
     */
    suspend fun syncServerTime(): Long? {
        return try {
            val localTimeBefore = System.currentTimeMillis()
            val response = apiService.getTime(appId = appId, appVersion = appVersion)
            val localTimeAfter = System.currentTimeMillis()
            if (response.isSuccessful && response.body() != null) {
                val serverTimeIso = response.body()!!.serverTime
                val serverInstant = java.time.Instant.parse(serverTimeIso)
                val serverTimeMs = serverInstant.toEpochMilli()
                val estimatedLocalMs = (localTimeBefore + localTimeAfter) / 2
                val offsetMs = serverTimeMs - estimatedLocalMs
                syncStateDao.set(SyncStateEntity(key = KEY_SERVER_CLOCK_OFFSET, value = offsetMs.toString()))
                offsetMs
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mengambil konfigurasi remote (tax, service charge, rounding, flags) dari endpoint GET /v1/config
     * dan menyimpannya secara persisten ke tabel sync_state di Room.
     */
    suspend fun syncConfig(): Boolean {
        return try {
            val response = apiService.getConfig(appId = appId, appVersion = appVersion)
            if (response.isSuccessful && response.body() != null) {
                val settings = response.body()!!.settings
                saveSettingsMap(settings)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractElementContent(elem: JsonElement?): String? = when (elem) {
        is JsonPrimitive -> elem.content
        null, JsonNull -> null
        else -> elem.toString()
    }

    suspend fun saveSettingsMap(settings: Map<String, JsonElement>) {
        val taxVal = extractElementContent(settings["tax_percent"])
            ?: extractElementContent(settings["tax_rate"])
        if (taxVal != null) {
            syncStateDao.set(SyncStateEntity(key = KEY_TAX_PERCENT, value = taxVal))
        }

        val modeVal = extractElementContent(settings["tax_mode"])
        if (modeVal != null) {
            syncStateDao.set(SyncStateEntity(key = KEY_TAX_MODE, value = modeVal))
        }

        val serviceVal = extractElementContent(settings["service_charge_percent"])
            ?: extractElementContent(settings["service_charge_rate"])
        if (serviceVal != null) {
            syncStateDao.set(SyncStateEntity(key = KEY_SERVICE_PERCENT, value = serviceVal))
        }

        val roundingVal = extractElementContent(settings["rounding_step"])
        if (roundingVal != null) {
            syncStateDao.set(SyncStateEntity(key = KEY_ROUNDING_STEP, value = roundingVal))
        }
    }

    /**
     * Menjalankan bootstrap data awal per section/halaman hingga selesai (done == true).
     * Jika terjadi kegagalan jaringan, kursor terakhir tetap tersimpan di Room
     * sehingga pemanggilan berikutnya melanjutkan dari titik tersebut.
     */
    suspend fun syncBootstrap(): BootstrapResult {
        var currentCursor = syncStateDao.get(KEY_BOOTSTRAP_CURSOR)
        var hasClearedMenuItems = false
        var hasClearedLaundryServices = false
        var hasClearedMenuOptions = false
        var hasClearedDiscountPresets = false

        while (true) {
            val response = try {
                apiService.getBootstrap(
                    cursor = currentCursor,
                    appId = appId,
                    appVersion = appVersion
                )
            } catch (e: Exception) {
                return BootstrapResult.Interrupted(
                    lastSavedCursor = currentCursor,
                    error = e.message ?: "Koneksi terputus saat bootstrap"
                )
            }

            if (!response.isSuccessful || response.body() == null) {
                return BootstrapResult.Interrupted(
                    lastSavedCursor = currentCursor,
                    error = "HTTP error ${response.code()}: ${response.message()}"
                )
            }

            val body = response.body()!!

            val sectionNorm = body.section?.lowercase()?.trim()?.replace("-", "_")
            val isLaundrySection = sectionNorm in listOf(
                "laundry_services", "services", "laundry_service", "laundryservices", "laundry", "service", "laundryservice"
            ) || (sectionNorm == null && body.records.any { r ->
                val o = r.jsonObject
                o.containsKey("rate") || o.containsKey("unit") || o.containsKey("min_weight") || o.containsKey("express_rate") || o.containsKey("tarif")
            })

            // Simpan records sesuai section
            when {
                sectionNorm == "menu_items" -> {
                    if (!hasClearedMenuItems) {
                        // Bersihkan seluruh katalog lokal lama agar hanya menu server yang tersimpan
                        masterDataDao.clearMenuItems()
                        hasClearedMenuItems = true
                    }
                    val menuItems = body.records.map { record ->
                        val obj = record.jsonObject
                        val price = obj["price"]?.jsonPrimitive?.longOrNull
                            ?: obj["price"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: 0L
                        val active = obj["active"]?.jsonPrimitive?.booleanOrNull
                            ?: (obj["active"]?.jsonPrimitive?.content == "1" || obj["active"]?.jsonPrimitive?.content == "true")
                        val soldOut = obj["sold_out"]?.jsonPrimitive?.booleanOrNull
                            ?: (obj["sold_out"]?.jsonPrimitive?.content == "1" || obj["sold_out"]?.jsonPrimitive?.content == "true")

                        MenuItemEntity(
                            id = obj["id"]!!.jsonPrimitive.content,
                            name = obj["name"]!!.jsonPrimitive.content,
                            category = obj["category"]?.jsonPrimitive?.content ?: "General",
                            price = price,
                            active = active,
                            soldOut = soldOut
                        )
                    }
                    masterDataDao.upsertMenuItems(menuItems)
                }
                isLaundrySection -> {
                    val services = body.records.mapNotNull { record ->
                        val obj = record.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content
                            ?: obj["service_id"]?.jsonPrimitive?.content
                            ?: obj["uuid"]?.jsonPrimitive?.content
                            ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.content
                            ?: obj["title"]?.jsonPrimitive?.content
                            ?: obj["service_name"]?.jsonPrimitive?.content
                            ?: obj["nama"]?.jsonPrimitive?.content
                            ?: "Layanan"
                        val rate = obj["rate"]?.jsonPrimitive?.longOrNull
                            ?: obj["rate"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: obj["price"]?.jsonPrimitive?.longOrNull
                            ?: obj["price"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: obj["tarif"]?.jsonPrimitive?.longOrNull
                            ?: obj["tarif"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: obj["harga"]?.jsonPrimitive?.longOrNull
                            ?: obj["harga"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: 0L
                        val expressRate = obj["express_rate"]?.jsonPrimitive?.longOrNull
                            ?: obj["express_rate"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: obj["express_price"]?.jsonPrimitive?.longOrNull
                            ?: obj["express_price"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: obj["tarif_express"]?.jsonPrimitive?.longOrNull
                            ?: obj["tarif_express"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                            ?: 0L
                        val minWeight = obj["min_weight"]?.jsonPrimitive?.doubleOrNull
                            ?: obj["min_weight"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: 1.0
                        val weightStep = obj["weight_step"]?.jsonPrimitive?.doubleOrNull
                            ?: obj["weight_step"]?.jsonPrimitive?.content?.toDoubleOrNull()
                            ?: 0.5
                        
                        val activeRaw = obj["active"] ?: obj["is_active"] ?: obj["status"]
                        val active = when {
                            activeRaw == null -> true
                            activeRaw.jsonPrimitive.booleanOrNull != null -> activeRaw.jsonPrimitive.booleanOrNull == true
                            activeRaw.jsonPrimitive.content == "1" || activeRaw.jsonPrimitive.content.equals("true", ignoreCase = true) || activeRaw.jsonPrimitive.content.equals("active", ignoreCase = true) -> true
                            activeRaw.jsonPrimitive.content == "0" || activeRaw.jsonPrimitive.content.equals("false", ignoreCase = true) || activeRaw.jsonPrimitive.content.equals("inactive", ignoreCase = true) -> false
                            else -> true
                        }

                        val rawUnit = (obj["unit"]?.jsonPrimitive?.content ?: "kg").lowercase().trim()
                        val normalizedUnit = when (rawUnit) {
                            "pcs", "satuan", "potong", "lembar", "buah", "pc", "piece", "pieces" -> "pcs"
                            else -> "kg"
                        }

                        LaundryServiceEntity(
                            id = id,
                            name = name,
                            unit = normalizedUnit,
                            rate = rate,
                            expressRate = expressRate,
                            minWeight = minWeight,
                            weightStep = weightStep,
                            active = active
                        )
                    }
                    if (services.isNotEmpty()) {
                        if (!hasClearedLaundryServices) {
                            masterDataDao.clearLaundryServices()
                            hasClearedLaundryServices = true
                        }
                        masterDataDao.upsertLaundryServices(services)
                    }
                }
                sectionNorm == "menu_options" -> {
                    if (!hasClearedMenuOptions) {
                        masterDataDao.clearMenuOptions()
                        hasClearedMenuOptions = true
                    }
                    val options = body.records.map { record ->
                        val obj = record.jsonObject
                        val kindRaw = obj["kind"]?.jsonPrimitive?.content ?: "addon"
                        val kind = if (kindRaw.equals("varian", ignoreCase = true) || kindRaw.equals("variant", ignoreCase = true)) {
                            "variant"
                        } else {
                            "addon"
                        }
                        val priceDelta = obj["price_delta"]?.jsonPrimitive?.longOrNull
                            ?: obj["price_delta"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: 0L
                        val active = obj["active"]?.jsonPrimitive?.booleanOrNull
                            ?: (obj["active"]?.jsonPrimitive?.content == "1" || obj["active"]?.jsonPrimitive?.content == "true")

                        MenuOptionEntity(
                            id = obj["id"]!!.jsonPrimitive.content,
                            menuItemId = obj["menu_item_id"]!!.jsonPrimitive.content,
                            kind = kind,
                            name = obj["name"]!!.jsonPrimitive.content,
                            priceDelta = priceDelta,
                            active = active
                        )
                    }
                    masterDataDao.upsertMenuOptions(options)
                }
                sectionNorm == "discount_presets" -> {
                    if (!hasClearedDiscountPresets) {
                        masterDataDao.clearDiscountPresets()
                        hasClearedDiscountPresets = true
                    }
                    val presets = body.records.map { record ->
                        val obj = record.jsonObject
                        val value = obj["value"]?.jsonPrimitive?.longOrNull
                            ?: obj["value"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: 0L
                        val active = obj["active"]?.jsonPrimitive?.booleanOrNull
                            ?: (obj["active"]?.jsonPrimitive?.content == "1" || obj["active"]?.jsonPrimitive?.content == "true")
                        val type = obj["type"]?.jsonPrimitive?.content ?: "percent"

                        DiscountPresetEntity(
                            id = obj["id"]!!.jsonPrimitive.content,
                            name = obj["name"]!!.jsonPrimitive.content,
                            type = type,
                            value = value,
                            active = active
                        )
                    }
                    masterDataDao.upsertDiscountPresets(presets)
                }
                sectionNorm == "settings" -> {
                    for (record in body.records) {
                        val obj = record.jsonObject
                        val key = obj["key"]?.jsonPrimitive?.content
                        val valueElem = obj["value"]
                        val value = extractElementContent(valueElem)
                        if (key != null && value != null) {
                            val mappedKey = when (key) {
                                "tax_rate" -> KEY_TAX_PERCENT
                                "service_charge_rate" -> KEY_SERVICE_PERCENT
                                else -> key
                            }
                            syncStateDao.set(SyncStateEntity(key = mappedKey, value = value))
                        }
                        if (key == "discount_presets" && valueElem is JsonArray) {
                            val presets = valueElem.mapNotNull { elem ->
                                val pObj = elem.jsonObject
                                val pName = pObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                val pType = pObj["type"]?.jsonPrimitive?.content ?: "percent"
                                val pVal = pObj["value"]?.jsonPrimitive?.longOrNull ?: 0L
                                DiscountPresetEntity(
                                    id = pObj["id"]?.jsonPrimitive?.content ?: java.util.UUID.nameUUIDFromBytes(pName.toByteArray()).toString(),
                                    name = pName,
                                    type = pType,
                                    value = pVal
                                )
                            }
                            if (presets.isNotEmpty()) {
                                masterDataDao.upsertDiscountPresets(presets)
                            }
                        }
                    }
                }
            }

            if (body.done) {
                // Bootstrap selesai tuntas
                if (body.deltaCursor != null) {
                    syncStateDao.set(SyncStateEntity(key = KEY_DELTA_CURSOR, value = body.deltaCursor))
                }
                syncStateDao.set(SyncStateEntity(key = KEY_BOOTSTRAP_DONE, value = "true"))
                syncStateDao.delete(KEY_BOOTSTRAP_CURSOR)
                return BootstrapResult.Completed(deltaCursor = body.deltaCursor)
            } else {
                // Simpan next_cursor secara persisten agar dapat dilanjutkan jika terputus
                currentCursor = body.nextCursor
                if (currentCursor != null) {
                    syncStateDao.set(SyncStateEntity(key = KEY_BOOTSTRAP_CURSOR, value = currentCursor))
                }
            }
        }
    }

    /**
     * Menarik perubahan inkremental master data.
     * Bila server merespons 410 (CURSOR_EXPIRED), memicu pembersihan dan bootstrap ulang otomatis.
     */
    suspend fun syncDelta(): DeltaResult {
        val isDone = syncStateDao.get(KEY_BOOTSTRAP_DONE) == "true"
        var deltaCursor = syncStateDao.get(KEY_DELTA_CURSOR)

        if (!isDone || deltaCursor == null) {
            val bootResult = syncBootstrap()
            if (bootResult is BootstrapResult.Completed) {
                deltaCursor = bootResult.deltaCursor ?: return DeltaResult.Success(newCursor = "", count = 0)
            } else {
                return DeltaResult.Error(code = -1, message = "Bootstrap belum selesai")
            }
        }

        var totalChanges = 0
        var currentCursor: String = deltaCursor

        while (true) {
            val response = try {
                apiService.getChanges(
                    cursor = currentCursor,
                    appId = appId,
                    appVersion = appVersion
                )
            } catch (e: Exception) {
                return DeltaResult.Error(code = -1, message = e.message ?: "Koneksi terputus saat delta sync")
            }

            // Penanganan kursor kedaluwarsa (HTTP 410)
            if (response.code() == 410) {
                syncStateDao.delete(KEY_DELTA_CURSOR)
                syncStateDao.delete(KEY_BOOTSTRAP_DONE)
                val bootResult = syncBootstrap()
                val newCursor = if (bootResult is BootstrapResult.Completed) bootResult.deltaCursor else null
                return DeltaResult.Rebootstrapped(newDeltaCursor = newCursor)
            }

            if (!response.isSuccessful || response.body() == null) {
                return DeltaResult.Error(code = response.code(), message = response.message())
            }

            val body = response.body()!!

            for (change in body.changes) {
                val tableNorm = change.table.lowercase().trim().replace("-", "_")
                when {
                    tableNorm == "menu_items" -> {
                        val obj = change.record.jsonObject
                        val id = obj["id"]!!.jsonPrimitive.content
                        if (change.op == "delete") {
                            masterDataDao.deleteMenuItem(id)
                        } else {
                            val entity = MenuItemEntity(
                                id = id,
                                name = obj["name"]!!.jsonPrimitive.content,
                                category = obj["category"]?.jsonPrimitive?.content ?: "General",
                                price = obj["price"]!!.jsonPrimitive.long,
                                active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true,
                                soldOut = obj["sold_out"]?.jsonPrimitive?.booleanOrNull ?: false
                            )
                            masterDataDao.upsertMenuItem(entity)
                        }
                    }
                    tableNorm in listOf("laundry_services", "services", "laundry_service", "laundryservices", "laundry", "service", "laundryservice") -> {
                        val obj = change.record.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content
                            ?: obj["service_id"]?.jsonPrimitive?.content
                            ?: obj["uuid"]?.jsonPrimitive?.content
                        if (id != null) {
                            if (change.op == "delete") {
                                masterDataDao.deleteLaundryService(id)
                            } else {
                                val name = obj["name"]?.jsonPrimitive?.content
                                    ?: obj["title"]?.jsonPrimitive?.content
                                    ?: obj["service_name"]?.jsonPrimitive?.content
                                    ?: obj["nama"]?.jsonPrimitive?.content
                                    ?: "Layanan"
                                val rate = obj["rate"]?.jsonPrimitive?.longOrNull
                                    ?: obj["rate"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: obj["price"]?.jsonPrimitive?.longOrNull
                                    ?: obj["price"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: obj["tarif"]?.jsonPrimitive?.longOrNull
                                    ?: obj["tarif"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: obj["harga"]?.jsonPrimitive?.longOrNull
                                    ?: obj["harga"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: 0L
                                val expressRate = obj["express_rate"]?.jsonPrimitive?.longOrNull
                                    ?: obj["express_rate"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: obj["express_price"]?.jsonPrimitive?.longOrNull
                                    ?: obj["express_price"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: obj["tarif_express"]?.jsonPrimitive?.longOrNull
                                    ?: obj["tarif_express"]?.jsonPrimitive?.content?.replace(".", "")?.replace(",", "")?.toLongOrNull()
                                    ?: 0L
                                val minWeight = obj["min_weight"]?.jsonPrimitive?.doubleOrNull
                                    ?: obj["min_weight"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                    ?: 1.0
                                val weightStep = obj["weight_step"]?.jsonPrimitive?.doubleOrNull
                                    ?: obj["weight_step"]?.jsonPrimitive?.content?.toDoubleOrNull()
                                    ?: 0.5
                                
                                val activeRaw = obj["active"] ?: obj["is_active"] ?: obj["status"]
                                val active = when {
                                    activeRaw == null -> true
                                    activeRaw.jsonPrimitive.booleanOrNull != null -> activeRaw.jsonPrimitive.booleanOrNull == true
                                    activeRaw.jsonPrimitive.content == "1" || activeRaw.jsonPrimitive.content.equals("true", ignoreCase = true) || activeRaw.jsonPrimitive.content.equals("active", ignoreCase = true) -> true
                                    activeRaw.jsonPrimitive.content == "0" || activeRaw.jsonPrimitive.content.equals("false", ignoreCase = true) || activeRaw.jsonPrimitive.content.equals("inactive", ignoreCase = true) -> false
                                    else -> true
                                }

                                val rawUnit = (obj["unit"]?.jsonPrimitive?.content ?: "kg").lowercase().trim()
                                val normalizedUnit = when (rawUnit) {
                                    "pcs", "satuan", "potong", "lembar", "buah", "pc", "piece", "pieces" -> "pcs"
                                    else -> "kg"
                                }

                                val entity = LaundryServiceEntity(
                                    id = id,
                                    name = name,
                                    unit = normalizedUnit,
                                    rate = rate,
                                    expressRate = expressRate,
                                    minWeight = minWeight,
                                    weightStep = weightStep,
                                    active = active
                                )
                                masterDataDao.upsertLaundryService(entity)
                            }
                        }
                    }
                    tableNorm == "menu_options" -> {
                        val obj = change.record.jsonObject
                        val id = obj["id"]!!.jsonPrimitive.content
                        if (change.op == "delete") {
                            masterDataDao.deleteMenuOption(id)
                        } else {
                            val kindRaw = obj["kind"]?.jsonPrimitive?.content ?: "addon"
                            val kind = if (kindRaw.equals("varian", ignoreCase = true) || kindRaw.equals("variant", ignoreCase = true)) {
                                "variant"
                            } else {
                                "addon"
                            }
                            val priceDelta = obj["price_delta"]?.jsonPrimitive?.longOrNull
                                ?: obj["price_delta"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: 0L
                            val active = obj["active"]?.jsonPrimitive?.booleanOrNull
                                ?: (obj["active"]?.jsonPrimitive?.content == "1" || obj["active"]?.jsonPrimitive?.content == "true")

                            val entity = MenuOptionEntity(
                                id = id,
                                menuItemId = obj["menu_item_id"]!!.jsonPrimitive.content,
                                kind = kind,
                                name = obj["name"]!!.jsonPrimitive.content,
                                priceDelta = priceDelta,
                                active = active
                            )
                            masterDataDao.upsertMenuOption(entity)
                        }
                    }
                    tableNorm == "discount_presets" -> {
                        val obj = change.record.jsonObject
                        val id = obj["id"]!!.jsonPrimitive.content
                        if (change.op == "delete") {
                            masterDataDao.deleteDiscountPreset(id)
                        } else {
                            val value = obj["value"]?.jsonPrimitive?.longOrNull
                                ?: obj["value"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: 0L
                            val active = obj["active"]?.jsonPrimitive?.booleanOrNull
                                ?: (obj["active"]?.jsonPrimitive?.content == "1" || obj["active"]?.jsonPrimitive?.content == "true")
                            val type = obj["type"]?.jsonPrimitive?.content ?: "percent"

                            val entity = DiscountPresetEntity(
                                id = id,
                                name = obj["name"]!!.jsonPrimitive.content,
                                type = type,
                                value = value,
                                active = active
                            )
                            masterDataDao.upsertDiscountPreset(entity)
                        }
                    }
                    tableNorm == "settings" -> {
                        val obj = change.record.jsonObject
                        val key = obj["key"]?.jsonPrimitive?.content
                        val valueElem = obj["value"]
                        val value = extractElementContent(valueElem)
                        if (key != null) {
                            val mappedKey = when (key) {
                                "tax_rate" -> KEY_TAX_PERCENT
                                "service_charge_rate" -> KEY_SERVICE_PERCENT
                                else -> key
                            }
                            if (change.op == "delete") {
                                syncStateDao.delete(mappedKey)
                            } else if (value != null) {
                                syncStateDao.set(SyncStateEntity(key = mappedKey, value = value))
                            }
                        }
                    }
                }
                totalChanges++
            }

            currentCursor = body.nextCursor
            syncStateDao.set(SyncStateEntity(key = KEY_DELTA_CURSOR, value = currentCursor))

            if (!body.hasMore) {
                return DeltaResult.Success(newCursor = currentCursor, count = totalChanges)
            }
        }
    }
}
