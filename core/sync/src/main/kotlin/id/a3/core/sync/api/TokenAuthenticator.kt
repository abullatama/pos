package id.a3.core.sync.api

import id.a3.core.auth.AuthManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp Authenticator yang menangani respon HTTP 401 Unauthorized secara otomatis & idempoten:
 * 1. Mencegah perulangan tak berujung (infinite loop) dengan pembatasan responseCount >= 3.
 * 2. Menghindari pemanggilan refresh ganda jika thread/coroutine lain telah berhasil memperbarui token.
 * 3. Sinkronisasi thread-safe melalui Mutex saat memanggil AuthManager.refreshToken().
 * 4. Menyusun kembali request dengan header Authorization: Bearer <newToken>.
 */
class TokenAuthenticator(
    private val authManager: AuthManager,
    private val deviceCredentialProvider: (() -> String?)? = null
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // 1. Cegah infinite loop jika server terus membalas 401
        if (responseCount(response) >= 3) {
            return null
        }

        // Ambil token dari request yang ditolak 401
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        return runBlocking {
            refreshMutex.withLock {
                // 2. Periksa apakah coroutine lain telah memperbarui token di DataStore
                val currentToken = authManager.getAccessToken()?.trim()
                if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                // 3. Panggil API refresh token
                val credential = deviceCredentialProvider?.invoke()
                val refreshed = authManager.refreshToken(credential)
                if (refreshed) {
                    val newToken = authManager.getAccessToken()?.trim()
                    if (!newToken.isNullOrBlank()) {
                        return@withLock response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                }

                // Refresh gagal, hentikan retry (biarkan 401 ditangani oleh pemanggil)
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
