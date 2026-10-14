package id.a3.core.sync

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.SyncStateEntity
import id.a3.core.sync.api.SyncMasterApiService
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
    }

    /**
     * Menjalankan bootstrap data awal per section/halaman hingga selesai (done == true).
     * Jika terjadi kegagalan jaringan, kursor terakhir tetap tersimpan di Room
     * sehingga pemanggilan berikutnya melanjutkan dari titik tersebut.
     */
    suspend fun syncBootstrap(): BootstrapResult {
        var currentCursor = syncStateDao.get(KEY_BOOTSTRAP_CURSOR)

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

            // Simpan records sesuai section
            when (body.section) {
                "menu_items" -> {
                    val menuItems = body.records.map { record ->
                        val obj = record.jsonObject
                        MenuItemEntity(
                            id = obj["id"]!!.jsonPrimitive.content,
                            name = obj["name"]!!.jsonPrimitive.content,
                            category = obj["category"]?.jsonPrimitive?.content ?: "General",
                            price = obj["price"]!!.jsonPrimitive.long,
                            active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true,
                            soldOut = obj["sold_out"]?.jsonPrimitive?.booleanOrNull ?: false
                        )
                    }
                    masterDataDao.upsertMenuItems(menuItems)
                }
                "laundry_services" -> {
                    val services = body.records.map { record ->
                        val obj = record.jsonObject
                        LaundryServiceEntity(
                            id = obj["id"]!!.jsonPrimitive.content,
                            name = obj["name"]!!.jsonPrimitive.content,
                            unit = obj["unit"]?.jsonPrimitive?.content ?: "kg",
                            rate = obj["rate"]!!.jsonPrimitive.long,
                            expressRate = obj["express_rate"]?.jsonPrimitive?.longOrNull ?: 0L,
                            minWeight = obj["min_weight"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                            weightStep = obj["weight_step"]?.jsonPrimitive?.doubleOrNull ?: 0.5,
                            active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true
                        )
                    }
                    masterDataDao.upsertLaundryServices(services)
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
                when (change.table) {
                    "menu_items" -> {
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
                    "laundry_services" -> {
                        val obj = change.record.jsonObject
                        val id = obj["id"]!!.jsonPrimitive.content
                        if (change.op == "delete") {
                            masterDataDao.deleteLaundryService(id)
                        } else {
                            val entity = LaundryServiceEntity(
                                id = id,
                                name = obj["name"]!!.jsonPrimitive.content,
                                unit = obj["unit"]?.jsonPrimitive?.content ?: "kg",
                                rate = obj["rate"]!!.jsonPrimitive.long,
                                expressRate = obj["express_rate"]?.jsonPrimitive?.longOrNull ?: 0L,
                                minWeight = obj["min_weight"]?.jsonPrimitive?.doubleOrNull ?: 1.0,
                                weightStep = obj["weight_step"]?.jsonPrimitive?.doubleOrNull ?: 0.5,
                                active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true
                            )
                            masterDataDao.upsertLaundryService(entity)
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
