package id.a3.core.auth.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import id.a3.core.auth.crypto.PinHasher
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.model.TokenResponse
import id.a3.core.auth.model.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
}
