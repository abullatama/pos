package id.a3.core.auth.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import id.a3.core.auth.crypto.PinHasher
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.model.TokenResponse
import id.a3.core.auth.model.UserDto
import id.a3.core.domain.device.DeviceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class EncryptedAuthDataStore(
    private val dataStore: DataStore<Preferences>,
    private val cryptoManager: TinkCryptoManager
) {

    companion object {
        val KEY_ACCESS_TOKEN_CIPHER = stringPreferencesKey("access_token_cipher")
        val KEY_REFRESH_TOKEN_CIPHER = stringPreferencesKey("refresh_token_cipher")
        val KEY_PIN_HASH_CIPHER = stringPreferencesKey("pin_hash_cipher")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_ROLE = stringPreferencesKey("user_role")
        val KEY_BUSINESS_UNIT_ID = stringPreferencesKey("business_unit_id")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        val KEY_DEVICE_STATUS = stringPreferencesKey("device_status")
        val KEY_DEVICE_CREDENTIAL_CIPHER = stringPreferencesKey("device_credential_cipher")
    }

    /**
     * Menyimpan data login kasir dengan token dan hash PIN yang dienkripsi secara penuh
     * sebelum ditulis ke penyimpanan disk DataStore.
     */
    suspend fun saveAuth(tokenResponse: TokenResponse, pin: String) {
        val encryptedAccess = cryptoManager.encrypt(tokenResponse.accessToken)
        val encryptedRefresh = cryptoManager.encrypt(tokenResponse.refreshToken)
        val pinHash = PinHasher.hashPin(pin)
        val encryptedPinHash = cryptoManager.encrypt(pinHash)

        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN_CIPHER] = encryptedAccess
            prefs[KEY_REFRESH_TOKEN_CIPHER] = encryptedRefresh
            prefs[KEY_PIN_HASH_CIPHER] = encryptedPinHash
            prefs[KEY_USER_ID] = tokenResponse.user.id
            prefs[KEY_USER_NAME] = tokenResponse.user.name
            prefs[KEY_USER_ROLE] = tokenResponse.user.role
            prefs[KEY_BUSINESS_UNIT_ID] = tokenResponse.businessUnitId
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val encryptedAccess = cryptoManager.encrypt(accessToken)
        val encryptedRefresh = cryptoManager.encrypt(refreshToken)

        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN_CIPHER] = encryptedAccess
            prefs[KEY_REFRESH_TOKEN_CIPHER] = encryptedRefresh
        }
    }

    suspend fun getAccessToken(): String? {
        val prefs = dataStore.data.first()
        val cipher = prefs[KEY_ACCESS_TOKEN_CIPHER] ?: return null
        return try {
            cryptoManager.decrypt(cipher)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getRefreshToken(): String? {
        val prefs = dataStore.data.first()
        val cipher = prefs[KEY_REFRESH_TOKEN_CIPHER] ?: return null
        return try {
            cryptoManager.decrypt(cipher)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUser(): UserDto? {
        val prefs = dataStore.data.first()
        val id = prefs[KEY_USER_ID] ?: return null
        val name = prefs[KEY_USER_NAME] ?: return null
        val role = prefs[KEY_USER_ROLE] ?: return null
        return UserDto(id = id, name = name, role = role)
    }

    suspend fun getBusinessUnitId(): String? {
        val prefs = dataStore.data.first()
        return prefs[KEY_BUSINESS_UNIT_ID]
    }

    /**
     * Memvalidasi PIN kasir secara offline dengan membandingkan hash PIN
     * terhadap hash terenkripsi yang tersimpan di DataStore lokal.
     */
    suspend fun verifyOfflinePin(pin: String): Boolean {
        val prefs = dataStore.data.first()
        val encryptedPinHash = prefs[KEY_PIN_HASH_CIPHER] ?: return false
        val expectedHash = try {
            cryptoManager.decrypt(encryptedPinHash)
        } catch (_: Exception) {
            return false
        }
        return PinHasher.verify(pin, expectedHash)
    }

    suspend fun clearAuth() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    fun observeUser(): Flow<UserDto?> {
        return dataStore.data.map { prefs ->
            val id = prefs[KEY_USER_ID] ?: return@map null
            val name = prefs[KEY_USER_NAME] ?: return@map null
            val role = prefs[KEY_USER_ROLE] ?: return@map null
            UserDto(id = id, name = name, role = role)
        }
    }

    /**
     * Mengambil ID perangkat yang tersimpan atau membuat UUID baru dan menyimpannya permanen.
     */
    suspend fun getOrCreateDeviceId(): String {
        val prefs = dataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        dataStore.edit { p ->
            p[KEY_DEVICE_ID] = generated
        }
        return generated
    }

    suspend fun saveDeviceRegistration(deviceId: String, deviceName: String, status: DeviceStatus) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_DEVICE_NAME] = deviceName
            prefs[KEY_DEVICE_STATUS] = status.name
        }
    }

    suspend fun updateDeviceStatus(
        status: DeviceStatus,
        credential: String? = null,
        businessUnitId: String? = null
    ) {
        val encryptedCredential = credential?.let { cryptoManager.encrypt(it) }
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_STATUS] = status.name
            if (encryptedCredential != null) {
                prefs[KEY_DEVICE_CREDENTIAL_CIPHER] = encryptedCredential
            }
            if (businessUnitId != null) {
                prefs[KEY_BUSINESS_UNIT_ID] = businessUnitId
            }
        }
    }

    suspend fun getDeviceStatus(): DeviceStatus {
        val prefs = dataStore.data.first()
        val raw = prefs[KEY_DEVICE_STATUS] ?: return DeviceStatus.UNREGISTERED
        return try {
            DeviceStatus.valueOf(raw)
        } catch (_: Exception) {
            DeviceStatus.UNREGISTERED
        }
    }

    suspend fun getDeviceName(): String? {
        val prefs = dataStore.data.first()
        return prefs[KEY_DEVICE_NAME]
    }

    suspend fun getDeviceCredential(): String? {
        val prefs = dataStore.data.first()
        val cipher = prefs[KEY_DEVICE_CREDENTIAL_CIPHER] ?: return null
        return try {
            cryptoManager.decrypt(cipher)
        } catch (_: Exception) {
            null
        }
    }

    fun observeDeviceStatus(): Flow<DeviceStatus> {
        return dataStore.data.map { prefs ->
            val raw = prefs[KEY_DEVICE_STATUS] ?: return@map DeviceStatus.UNREGISTERED
            try {
                DeviceStatus.valueOf(raw)
            } catch (_: Exception) {
                DeviceStatus.UNREGISTERED
            }
        }
    }

    /**
     * Mencabut perangkat (DEVICE_REVOKED): menghapus kredensial perangkat dan token kasir,
     * serta mengunci status perangkat menjadi REVOKED.
     */
    suspend fun markDeviceRevoked() {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_STATUS] = DeviceStatus.REVOKED.name
            prefs.remove(KEY_DEVICE_CREDENTIAL_CIPHER)
            prefs.remove(KEY_ACCESS_TOKEN_CIPHER)
            prefs.remove(KEY_REFRESH_TOKEN_CIPHER)
            prefs.remove(KEY_PIN_HASH_CIPHER)
        }
    }
}
