package id.a3.core.auth

import id.a3.core.auth.api.DeviceApiService
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.auth.model.DeviceHeartbeatRequest
import id.a3.core.auth.model.DeviceRegisterRequest
import id.a3.core.domain.device.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class DeviceRegisterResult {
    data object PendingApproval : DeviceRegisterResult()
    data class Error(val code: String, val message: String) : DeviceRegisterResult()
}

sealed class DeviceCheckResult {
    data class StatusUpdated(val status: DeviceStatus) : DeviceCheckResult()
    data class Error(val code: String, val message: String) : DeviceCheckResult()
}

sealed class DeviceHeartbeatResult {
    data object Success : DeviceHeartbeatResult()
    data object SkippedNotActive : DeviceHeartbeatResult()
    data object DeviceRevoked : DeviceHeartbeatResult()
    data class Error(val code: String, val message: String) : DeviceHeartbeatResult()
}

/**
 * Mengelola siklus hidup pendaftaran perangkat tablet:
 * - Pendaftaran kode sekali pakai ke POST /v1/devices/register.
 * - Polling/cek status persetujuan ke GET /v1/devices/me (PENDING -> ACTIVE).
 * - Penyimpanan kredensial perangkat terenkripsi Tink saat pertama kali Aktif.
 * - Penanganan DEVICE_REVOKED yang mengunci layar tablet seketika.
 */
class DeviceManager(
    private val encryptedDataStore: EncryptedAuthDataStore,
    private val apiService: DeviceApiService,
    private val appId: String = "cafe",
    private val appVersion: String = "1.0.0"
) {

    private val _deviceStatus = MutableStateFlow(DeviceStatus.UNREGISTERED)
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    @Volatile
    var cachedCredential: String? = null
        private set

    @Volatile
    var cachedAuthToken: String? = null
        private set

    @Volatile
    var cachedDeviceId: String? = null
        private set

    @Volatile
    var cachedDeviceCode: String? = null
        private set

    suspend fun initialize() {
        val current = encryptedDataStore.getDeviceStatus()
        _deviceStatus.value = current
        cachedCredential = encryptedDataStore.getDeviceCredential()
        cachedAuthToken = encryptedDataStore.getAccessToken()
        cachedDeviceId = encryptedDataStore.getOrCreateDeviceId()
        cachedDeviceCode = encryptedDataStore.getDeviceServerCode()
        _isInitialized.value = true
    }

    suspend fun getDeviceCode(): String? {
        if (!cachedDeviceCode.isNullOrBlank()) return cachedDeviceCode
        val code = encryptedDataStore.getDeviceServerCode()
        if (!code.isNullOrBlank()) {
            cachedDeviceCode = code
            return code
        }
        return null
    }

    suspend fun saveDeviceCode(code: String) {
        cachedDeviceCode = code
        encryptedDataStore.updateDeviceStatus(
            status = encryptedDataStore.getDeviceStatus(),
            serverCode = code
        )
    }

    fun isDemoMode(): Boolean = cachedCredential == "demo-device-credential-local"

    suspend fun getDeviceId(): String {
        val id = encryptedDataStore.getOrCreateDeviceId()
        cachedDeviceId = id
        return id
    }

    suspend fun regenerateDeviceId(): String {
        val id = encryptedDataStore.regenerateDeviceId()
        cachedDeviceId = id
        return id
    }

    suspend fun getDeviceName(): String? {
        return encryptedDataStore.getDeviceName()
    }

    suspend fun getDeviceCredential(): String? {
        val cred = encryptedDataStore.getDeviceCredential()
        cachedCredential = cred
        return cred
    }

    private fun parseServerErrorMessage(errorBody: String): String? {
        return try {
            val jsonElement = Json.parseToJsonElement(errorBody)
            val obj = jsonElement.jsonObject
            obj["message"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mendaftarkan tablet dengan kode sekali pakai yang diberikan owner.
     */
    suspend fun registerDevice(
        registrationCode: String,
        deviceName: String
    ): DeviceRegisterResult {
        if (registrationCode.isBlank()) {
            return DeviceRegisterResult.Error("INVALID_CODE", "Kode pendaftaran tidak boleh kosong")
        }
        if (deviceName.isBlank()) {
            return DeviceRegisterResult.Error("INVALID_NAME", "Nama tablet tidak boleh kosong")
        }

        var deviceId = encryptedDataStore.getOrCreateDeviceId()
        var request = DeviceRegisterRequest(
            registrationCode = registrationCode.trim(),
            deviceId = deviceId,
            name = deviceName.trim()
        )

        var response = try {
            apiService.registerDevice(
                request = request,
                appId = appId,
                appVersion = appVersion
            )
        } catch (e: Exception) {
            return DeviceRegisterResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
        }

        var code = response.code()
        var errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }

        // Jika server mengembalikan 409 karena device_id sudah terdaftar sebelumnya (DEVICE_ALREADY_REGISTERED),
        // otomatis buat device_id baru dan coba daftarkan sekali lagi dengan kode yang sama
        if (code == 409 && (errBody?.contains("DEVICE_ALREADY_REGISTERED") == true || errBody?.contains("device_id") == true)) {
            deviceId = encryptedDataStore.regenerateDeviceId()
            request = DeviceRegisterRequest(
                registrationCode = registrationCode.trim(),
                deviceId = deviceId,
                name = deviceName.trim()
            )
            val retryResponse = try {
                apiService.registerDevice(
                    request = request,
                    appId = appId,
                    appVersion = appVersion
                )
            } catch (e: Exception) {
                return DeviceRegisterResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
            }
            if (retryResponse.isSuccessful || retryResponse.code() == 202) {
                cachedDeviceId = deviceId
                encryptedDataStore.saveDeviceRegistration(
                    deviceId = deviceId,
                    deviceName = deviceName.trim(),
                    status = DeviceStatus.PENDING,
                    registrationCode = registrationCode.trim()
                )
                _deviceStatus.value = DeviceStatus.PENDING
                return DeviceRegisterResult.PendingApproval
            } else {
                response = retryResponse
                code = retryResponse.code()
                errBody = try { retryResponse.errorBody()?.string() } catch (_: Exception) { null }
            }
        }

        if (response.isSuccessful || code == 202) {
            val body = response.body()
            cachedDeviceId = deviceId
            encryptedDataStore.saveDeviceRegistration(
                deviceId = deviceId,
                deviceName = deviceName.trim(),
                status = DeviceStatus.PENDING,
                registrationCode = registrationCode.trim()
            )
            body?.code?.let { serverCode ->
                cachedDeviceCode = serverCode
                encryptedDataStore.updateDeviceStatus(
                    status = DeviceStatus.PENDING,
                    serverCode = serverCode
                )
            }
            _deviceStatus.value = DeviceStatus.PENDING
            return DeviceRegisterResult.PendingApproval
        }

        val serverMessage = errBody?.let { parseServerErrorMessage(it) }
        val message = when (code) {
            409 -> serverMessage ?: "Perangkat ini sudah terdaftar dalam sistem (Device ID bentrok)"
            422 -> serverMessage ?: "Kode pendaftaran tidak valid atau sudah kedaluwarsa"
            else -> serverMessage ?: "Gagal mendaftarkan perangkat (kode $code): ${response.message()}"
        }
        return DeviceRegisterResult.Error("HTTP_$code", message)
    }

    /**
     * Memeriksa status perangkat ke server:
     * - PENDING: Menunggu persetujuan owner di web.
     * - ACTIVE: Disetujui; menyimpan kredensial perangkat terenkripsi.
     * - REVOKED: Akses dicabut; menghapus kredensial dan mengunci perangkat.
     */
    suspend fun checkStatus(): DeviceCheckResult {
        if (isDemoMode()) {
            _deviceStatus.value = DeviceStatus.ACTIVE
            return DeviceCheckResult.StatusUpdated(DeviceStatus.ACTIVE)
        }

        val deviceId = encryptedDataStore.getOrCreateDeviceId()
        val registrationCode = encryptedDataStore.getRegistrationCode()
        val credential = encryptedDataStore.getDeviceCredential()

        val response = try {
            apiService.getDeviceMe(
                deviceId = deviceId,
                registrationCode = registrationCode,
                deviceCredential = credential,
                appId = appId,
                appVersion = appVersion
            )
        } catch (e: Exception) {
            return DeviceCheckResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
        }

        val code = response.code()
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val newStatus = when (body.status.uppercase()) {
                "ACTIVE" -> DeviceStatus.ACTIVE
                "REVOKED" -> DeviceStatus.REVOKED
                else -> DeviceStatus.PENDING
            }

            if (newStatus == DeviceStatus.REVOKED) {
                encryptedDataStore.markDeviceRevoked()
                cachedCredential = null
            } else {
                if (!body.code.isNullOrBlank()) {
                    cachedDeviceCode = body.code
                }
                encryptedDataStore.updateDeviceStatus(
                    status = newStatus,
                    credential = body.deviceCredential,
                    businessUnitId = body.businessUnitId,
                    serverCode = body.code
                )
                if (!body.deviceCredential.isNullOrBlank()) {
                    cachedCredential = body.deviceCredential
                } else if (newStatus == DeviceStatus.ACTIVE) {
                    cachedCredential = encryptedDataStore.getDeviceCredential()
                }
            }

            _deviceStatus.value = newStatus
            return DeviceCheckResult.StatusUpdated(newStatus)
        }

        val errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
        val isExplicitRevoked = code == 403 || (errBody != null && errBody.contains("DEVICE_REVOKED"))

        return if (isExplicitRevoked) {
            // Perangkat dicabut aksesnya oleh owner
            encryptedDataStore.markDeviceRevoked()
            cachedCredential = null
            _deviceStatus.value = DeviceStatus.REVOKED
            DeviceCheckResult.StatusUpdated(DeviceStatus.REVOKED)
        } else {
            DeviceCheckResult.Error("HTTP_$code", "Gagal memeriksa status (kode $code): ${response.message()}")
        }
    }

    /**
     * Mengirim detak jantung perangkat (Heartbeat) ke server backend:
     * - Hanya dieksekusi jika status perangkat sudah ACTIVE.
     * - Menyertakan statistik outbox, baterai, sinkron terakhir, dan status printer.
     * - Jika server membalas 401/403 (DEVICE_REVOKED), seketika mengunci tablet dan mencabut kredensial.
     */
    suspend fun sendHeartbeat(
        outboxPending: Int = 0,
        outboxProblem: Int = 0,
        batteryPercent: Int? = null,
        lastSyncAt: String? = null,
        printerStatus: String = "unknown"
    ): DeviceHeartbeatResult {
        val currentStatus = encryptedDataStore.getDeviceStatus()
        if (currentStatus != DeviceStatus.ACTIVE) {
            return DeviceHeartbeatResult.SkippedNotActive
        }

        val credential = encryptedDataStore.getDeviceCredential()
        cachedCredential = credential
        val request = DeviceHeartbeatRequest(
            appVersion = appVersion,
            appId = appId,
            outboxPending = outboxPending,
            outboxProblem = outboxProblem,
            batteryPercent = batteryPercent,
            lastSyncAt = lastSyncAt,
            printerStatus = printerStatus
        )

        val response = try {
            apiService.sendHeartbeat(
                request = request,
                deviceCredential = credential,
                appId = appId,
                appVersion = appVersion
            )
        } catch (e: Exception) {
            return DeviceHeartbeatResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
        }

        val code = response.code()
        val errBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
        val isExplicitRevoked = code == 403 || (errBody != null && errBody.contains("DEVICE_REVOKED"))

        return if (response.isSuccessful || code == 204) {
            DeviceHeartbeatResult.Success
        } else if (isExplicitRevoked) {
            encryptedDataStore.markDeviceRevoked()
            cachedCredential = null
            _deviceStatus.value = DeviceStatus.REVOKED
            DeviceHeartbeatResult.DeviceRevoked
        } else {
            val errorMsg = if (!errBody.isNullOrBlank()) errBody else response.message()
            DeviceHeartbeatResult.Error("HTTP_$code", "Gagal mengirim heartbeat (kode $code): $errorMsg")
        }
    }

    /**
     * Mengaktifkan perangkat secara lokal khusus untuk keperluan demo dan peninjauan UI:
     * Menyimpan status ACTIVE dan kredensial tiruan tanpa memerlukan koneksi server backend.
     */
    suspend fun activateForDebug(
        deviceName: String = "Tablet Kasir (Demo)"
    ) {
        val deviceId = encryptedDataStore.getOrCreateDeviceId()
        encryptedDataStore.saveDeviceRegistration(
            deviceId = deviceId,
            deviceName = deviceName,
            status = DeviceStatus.ACTIVE
        )
        encryptedDataStore.updateDeviceStatus(
            status = DeviceStatus.ACTIVE,
            credential = "demo-device-credential-local",
            businessUnitId = if (appId == "laundry") "bu-laundry-01" else "bu-cafe-01"
        )
        cachedCredential = "demo-device-credential-local"
        _deviceStatus.value = DeviceStatus.ACTIVE
    }

    /**
     * Mereset status perangkat kembali ke UNREGISTERED untuk menguji ulang alur pendaftaran.
     */
    suspend fun resetDeviceForDebug() {
        encryptedDataStore.resetDeviceRegistration()
        cachedCredential = null
        cachedAuthToken = null
        _deviceStatus.value = DeviceStatus.UNREGISTERED
    }
}
