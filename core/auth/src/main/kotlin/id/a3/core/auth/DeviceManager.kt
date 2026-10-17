package id.a3.core.auth

import id.a3.core.auth.api.DeviceApiService
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.auth.model.DeviceRegisterRequest
import id.a3.core.domain.device.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class DeviceRegisterResult {
    data object PendingApproval : DeviceRegisterResult()
    data class Error(val code: String, val message: String) : DeviceRegisterResult()
}

sealed class DeviceCheckResult {
    data class StatusUpdated(val status: DeviceStatus) : DeviceCheckResult()
    data class Error(val code: String, val message: String) : DeviceCheckResult()
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

    suspend fun initialize() {
        val current = encryptedDataStore.getDeviceStatus()
        _deviceStatus.value = current
    }

    suspend fun getDeviceId(): String {
        return encryptedDataStore.getOrCreateDeviceId()
    }

    suspend fun getDeviceName(): String? {
        return encryptedDataStore.getDeviceName()
    }

    suspend fun getDeviceCredential(): String? {
        return encryptedDataStore.getDeviceCredential()
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

        val deviceId = encryptedDataStore.getOrCreateDeviceId()
        val request = DeviceRegisterRequest(
            registrationCode = registrationCode.trim(),
            deviceId = deviceId,
            name = deviceName.trim()
        )

        val response = try {
            apiService.registerDevice(
                request = request,
                appId = appId,
                appVersion = appVersion
            )
        } catch (e: Exception) {
            return DeviceRegisterResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
        }

        val code = response.code()
        return if (response.isSuccessful || code == 202) {
            encryptedDataStore.saveDeviceRegistration(
                deviceId = deviceId,
                deviceName = deviceName.trim(),
                status = DeviceStatus.PENDING
            )
            _deviceStatus.value = DeviceStatus.PENDING
            DeviceRegisterResult.PendingApproval
        } else {
            val message = when (code) {
                409 -> "Kode pendaftaran sudah digunakan atau tidak valid"
                422 -> "Format data pendaftaran tidak sesuai spesifikasi"
                else -> "Gagal mendaftarkan perangkat (kode $code): ${response.message()}"
            }
            DeviceRegisterResult.Error("HTTP_$code", message)
        }
    }

    /**
     * Memeriksa status perangkat ke server:
     * - PENDING: Menunggu persetujuan owner di web.
     * - ACTIVE: Disetujui; menyimpan kredensial perangkat terenkripsi.
     * - REVOKED: Akses dicabut; menghapus kredensial dan mengunci perangkat.
     */
    suspend fun checkStatus(): DeviceCheckResult {
        val deviceId = encryptedDataStore.getOrCreateDeviceId()

        val response = try {
            apiService.getDeviceMe(
                deviceId = deviceId,
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
            } else {
                encryptedDataStore.updateDeviceStatus(
                    status = newStatus,
                    credential = body.deviceCredential,
                    businessUnitId = body.businessUnitId
                )
            }

            _deviceStatus.value = newStatus
            return DeviceCheckResult.StatusUpdated(newStatus)
        }

        return if (code == 401 || code == 403) {
            // Perangkat tidak dikenal atau akses dicabut
            encryptedDataStore.markDeviceRevoked()
            _deviceStatus.value = DeviceStatus.REVOKED
            DeviceCheckResult.StatusUpdated(DeviceStatus.REVOKED)
        } else {
            DeviceCheckResult.Error("HTTP_$code", "Gagal memeriksa status (kode $code): ${response.message()}")
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
        _deviceStatus.value = DeviceStatus.ACTIVE
    }

    /**
     * Mereset status perangkat kembali ke UNREGISTERED untuk menguji ulang alur pendaftaran.
     */
    suspend fun resetDeviceForDebug() {
        encryptedDataStore.resetDeviceRegistration()
        _deviceStatus.value = DeviceStatus.UNREGISTERED
    }
}
