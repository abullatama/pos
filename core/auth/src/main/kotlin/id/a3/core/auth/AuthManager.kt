package id.a3.core.auth

import id.a3.core.auth.api.AuthApiService
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.auth.model.PinLoginRequest
import id.a3.core.auth.model.RefreshTokenRequest
import id.a3.core.auth.model.UserDto
import id.a3.core.domain.security.ManagerPinVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthResult {
    data class Success(val user: UserDto) : AuthResult()
    data class Error(val code: String, val message: String) : AuthResult()
    data class AccountLocked(val retryAfterSeconds: Int, val message: String) : AuthResult()
}

/**
 * Mengelola alur autentikasi kasir:
 * - Login pertama secara online ke server backend.
 * - Penyimpanan token JWT terenkripsi via Google Tink AEAD di DataStore.
 * - Buka kunci harian (unlock screen) secara offline menggunakan PIN lokal.
 */
class AuthManager(
    private val encryptedDataStore: EncryptedAuthDataStore,
    private val apiService: AuthApiService,
    private val appId: String = "cafe",
    private val appVersion: String = "1.0.0"
) {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    suspend fun initialize() {
        val token = encryptedDataStore.getAccessToken()
        val user = encryptedDataStore.getUser()
        _isUnlocked.value = user != null && !token.isNullOrBlank()
    }

    /**
     * Mengambil daftar akun staf yang diizinkan pada unit usaha tablet ini dari server.
     */
    suspend fun getAccounts(deviceCredential: String? = null): List<UserDto> {
        val response = try {
            apiService.getAccounts(
                deviceCredential = deviceCredential,
                appId = appId,
                appVersion = appVersion
            )
        } catch (_: Exception) {
            return emptyList()
        }
        return if (response.isSuccessful && response.body() != null) {
            response.body()!!.accounts
        } else {
            emptyList()
        }
    }

    /**
     * Login pertama online menggunakan PIN:
     * Mengirim user_id dan pin ke POST /v1/auth/pin-login.
     * Jika sukses, token dienkripsi dan disimpan di DataStore lokal.
     */
    suspend fun loginWithPinOnline(
        userId: String,
        pin: String,
        deviceCredential: String? = null
    ): AuthResult {
        val response = try {
            apiService.pinLogin(
                request = PinLoginRequest(userId = userId, pin = pin),
                deviceCredential = deviceCredential,
                appId = appId,
                appVersion = appVersion
            )
        } catch (e: Exception) {
            return AuthResult.Error("NETWORK_ERROR", e.message ?: "Koneksi ke server gagal")
        }

        if (response.isSuccessful && response.body() != null) {
            val tokenResponse = response.body()!!
            encryptedDataStore.saveAuth(tokenResponse, pin)
            _isUnlocked.value = true
            return AuthResult.Success(tokenResponse.user)
        }

        val code = response.code()
        return when (code) {
            401 -> AuthResult.Error("PIN_INVALID", "PIN yang Anda masukkan salah")
            403 -> AuthResult.Error("FORBIDDEN", "Pengguna tidak memiliki izin akses pada unit usaha ini")
            423 -> AuthResult.AccountLocked(300, "Akun terkunci sementara karena terlalu banyak percobaan salah")
            else -> AuthResult.Error("HTTP_$code", "Gagal login (kode $code): ${response.message()}")
        }
    }

    /**
     * Buka kunci kasir secara offline dengan mencocokkan PIN lokal.
     * Tidak membutuhkan koneksi internet.
     */
    suspend fun unlockWithPinOffline(pin: String): Boolean {
        val isValid = encryptedDataStore.verifyOfflinePin(pin) || ManagerPinVerifier.verify(pin)
        if (isValid) {
            _isUnlocked.value = true
        }
        return isValid
    }

    /**
     * Mengunci layar kasir (misal saat jeda atau selesai transaksi).
     */
    fun lock() {
        _isUnlocked.value = false
    }

    /**
     * Keluar dari akun kasir dan menghapus token terenkripsi dari DataStore.
     */
    suspend fun logout() {
        encryptedDataStore.clearAuth()
        _isUnlocked.value = false
    }

    suspend fun getAccessToken(): String? {
        return encryptedDataStore.getAccessToken()
    }

    suspend fun getRefreshToken(): String? {
        return encryptedDataStore.getRefreshToken()
    }

    suspend fun getCurrentUser(): UserDto? {
        return encryptedDataStore.getUser()
    }

    suspend fun getBusinessUnitId(): String? {
        return encryptedDataStore.getBusinessUnitId()
    }

    /**
     * Memperpanjang token menggunakan refresh token.
     */
    suspend fun refreshToken(deviceCredential: String? = null): Boolean {
        val refreshToken = encryptedDataStore.getRefreshToken() ?: return false
        val response = try {
            apiService.refreshToken(
                request = RefreshTokenRequest(refreshToken = refreshToken),
                deviceCredential = deviceCredential
            )
        } catch (_: Exception) {
            return false
        }

        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            encryptedDataStore.updateTokens(body.accessToken, body.refreshToken)
            _isUnlocked.value = true
            return true
        } else if (response.code() == 401 || response.code() == 403) {
            // Sesi token server kedaluwarsa; bersihkan token agar request jaringan berikutnya tidak menggunakan token kedaluwarsa
            encryptedDataStore.clearTokens()
        }
        return false
    }
}
