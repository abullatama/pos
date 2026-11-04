package id.a3.core.sync

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.dao.UserDao
import id.a3.core.data.local.entity.DiscountPresetEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.MenuOptionEntity
import id.a3.core.data.local.entity.SyncStateEntity
import id.a3.core.data.local.entity.UserEntity
import id.a3.core.sync.api.SyncMasterApiService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
    private val appVersion: String = "1.0.0",
    private val userDao: UserDao? = null
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

    /**
     * Server menyimpan tarif dalam basis poin (1000 = 10%). Tablet memakai persen bulat.
     * Tarif yang bukan kelipatan 100 bp (mis. 1050) tidak bisa diwakili persen bulat: diabaikan
     * agar tidak menyimpan angka keliru; owner perlu memakai persen bulat.
     */
    internal fun basisPointsToPercent(raw: String): String? {
        val bp = raw.trim().toLongOrNull() ?: return null
        return if (bp >= 0 && bp % 100L == 0L) (bp / 100L).toString() else null
    }

    private fun extractElementContent(elem: JsonElement?): String? = when (elem) {
        null, JsonNull -> null // JsonNull juga JsonPrimitive: harus diperiksa lebih dulu
        is JsonPrimitive -> elem.content
        else -> elem.toString()
    }

    private enum class SettingScope { GLOBAL, UNIT, RESOLVED }

    /**
     * Pengaturan dari GET /v1/config (sudah digabung server untuk usaha ini).
     */
    suspend fun saveSettingsMap(settings: Map<String, JsonElement>) {
        for ((key, value) in settings) {
            applyServerSetting(key, value, isDelete = false, scope = SettingScope.RESOLVED)
        }
        recomputeDerivedSettings()
    }

    /**
     * Satu-satunya jalur penerapan pengaturan dari server (config, bootstrap, delta).
     * Nama kunci server: tax_bp, tax_mode, service_charge_enabled, service_charge_bp,
     * rounding_multiple, discount_presets. Pengaturan khusus usaha mengalahkan yang global.
     */
    private suspend fun applyServerSetting(key: String, valueElem: JsonElement?, isDelete: Boolean, scope: SettingScope) {
        val marker = "scope_unit:$key"
        if (scope == SettingScope.GLOBAL && !isDelete && syncStateDao.get(marker) != null) return

        if (isDelete) {
            syncStateDao.delete(legacyKey(key))
            if (scope == SettingScope.UNIT) syncStateDao.delete(marker)
            return
        }
        if (scope == SettingScope.UNIT) syncStateDao.set(SyncStateEntity(key = marker, value = "1"))

        if (key == "discount_presets" && valueElem is JsonArray) {
            applyDiscountPresetList(valueElem)
            return
        }
        val value = extractElementContent(valueElem) ?: return
        syncStateDao.set(SyncStateEntity(key = legacyKey(key), value = value))
    }

    /** Kunci lama (mock server) dipetakan ke kunci yang dibaca aplikasi. */
    private fun legacyKey(key: String): String = when (key) {
        "tax_rate" -> KEY_TAX_PERCENT
        "service_charge_rate" -> KEY_SERVICE_PERCENT
        else -> key
    }

    /**
     * Turunkan nilai yang dipakai aplikasi dari kunci server. Biaya layanan nol bila server
     * menonaktifkannya (service_charge_enabled = false), apa pun nilai service_charge_bp.
     */
    private suspend fun recomputeDerivedSettings() {
        syncStateDao.get("tax_bp")?.let { basisPointsToPercent(it) }?.let {
            syncStateDao.set(SyncStateEntity(key = KEY_TAX_PERCENT, value = it))
        }

        val enabled = syncStateDao.get("service_charge_enabled")?.trim()?.lowercase()?.let { it == "true" || it == "1" }
        val serviceBp = syncStateDao.get("service_charge_bp") ?: syncStateDao.get("service_bp")
        val servicePercent = when {
            enabled == false -> "0"
            serviceBp != null -> basisPointsToPercent(serviceBp)
            else -> null
        }
        if (servicePercent != null) {
            syncStateDao.set(SyncStateEntity(key = KEY_SERVICE_PERCENT, value = servicePercent))
        }

        syncStateDao.get("rounding_multiple")?.let {
            syncStateDao.set(SyncStateEntity(key = KEY_ROUNDING_STEP, value = it))
        }
    }

    /**
     * Server menyimpan preset persen dalam basis poin (1000 = 10%) dan nominal dalam rupiah.
     * Tablet menyimpan persen bulat. Preset persen yang bukan kelipatan 100 bp dibuang karena
     * tablet tidak bisa menghitungnya sama persis dengan server.
     * Daftar dari server menggantikan seluruh daftar lama.
     */
    private suspend fun applyDiscountPresetList(list: JsonArray) {
        val presets = list.mapNotNull { elem ->
            val pObj = elem as? JsonObject ?: return@mapNotNull null
            val name = pObj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val type = pObj["type"]?.jsonPrimitive?.content ?: "percent"
            val raw = pObj["value"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val isPercent = !(type.equals("nominal", true) || type.equals("amount", true))
            val value = if (isPercent) {
                basisPointsToPercent(raw.toString())?.toLongOrNull()?.takeIf { it in 0L..100L } ?: return@mapNotNull null
            } else {
                raw.takeIf { it >= 0L } ?: return@mapNotNull null
            }
            DiscountPresetEntity(
                id = pObj["id"]?.jsonPrimitive?.content ?: java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString(),
                name = name,
                type = if (isPercent) "percent" else "nominal",
                value = value
            )
        }
        masterDataDao.clearDiscountPresets()
        if (presets.isNotEmpty()) masterDataDao.upsertDiscountPresets(presets)
    }

    /** Akun staf beserta verifier PIN khusus perangkat (spek 3.3.D). PIN mentah tidak pernah ada. */
    private fun parseUser(obj: JsonObject): UserEntity? {
        val id = extractElementContent(obj["id"])?.toLongOrNull() ?: return null
        val name = extractElementContent(obj["name"]) ?: return null
        val role = extractElementContent(obj["role"])?.lowercase() ?: return null
        val activeRaw = extractElementContent(obj["active"])?.lowercase()
        return UserEntity(
            id = id,
            name = name,
            role = role,
            active = activeRaw == null || activeRaw == "true" || activeRaw == "1",
            pinKdfSalt = extractElementContent(obj["pin_kdf_salt"]),
            pinKdfIterations = extractElementContent(obj["pin_kdf_iterations"])?.toIntOrNull(),
            pinVerifier = extractElementContent(obj["pin_verifier"])
        )
    }

    private fun scopeOf(obj: JsonObject): SettingScope {
        val unit = obj["business_unit_id"]
        return if (unit == null || unit is JsonNull) SettingScope.GLOBAL else SettingScope.UNIT
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
        var hasClearedUsers = false

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
                sectionNorm == "users" -> {
                    val dao = userDao
                    if (dao != null) {
                        if (!hasClearedUsers) {
                            dao.clear()
                            hasClearedUsers = true
                        }
                        for (record in body.records) {
                            parseUser(record.jsonObject)?.let { dao.upsert(it) }
                        }
                    }
                }
                sectionNorm == "settings" -> {
                    // Urutkan: pengaturan global lebih dulu, lalu khusus usaha (menang).
                    val records = body.records.map { it.jsonObject }.sortedBy { if (scopeOf(it) == SettingScope.GLOBAL) 0 else 1 }
                    for (obj in records) {
                        val key = obj["key"]?.jsonPrimitive?.content ?: continue
                        applyServerSetting(key, obj["value"], isDelete = false, scope = scopeOf(obj))
                    }
                    recomputeDerivedSettings()
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
                    tableNorm == "users" -> {
                        val dao = userDao
                        if (dao != null) {
                            val obj = change.record.jsonObject
                            if (change.op == "delete") {
                                (obj["id"]?.jsonPrimitive?.content?.toLongOrNull())?.let { dao.deleteById(it) }
                            } else {
                                parseUser(obj)?.let { dao.upsert(it) }
                            }
                        }
                    }
                    tableNorm == "settings" -> {
                        val obj = change.record.jsonObject
                        val key = obj["key"]?.jsonPrimitive?.content
                        if (key != null) {
                            applyServerSetting(key, obj["value"], isDelete = change.op == "delete", scope = scopeOf(obj))
                            recomputeDerivedSettings()
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
