package id.a3.core.sync.api

import id.a3.core.domain.config.AppConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Interceptor OkHttp yang secara otomatis menambahkan header wajib A3 ke setiap permintaan HTTP:
 * - Accept: application/json
 * - X-AppId: "cafe" atau "laundry"
 * - X-AppVersion: Versi aplikasi klien
 * - X-Request-Id: UUID unik per permintaan untuk pelacakan log & idempotency tracing
 * - X-Device-Credential: Kredensial rahasia perangkat jika tersedia
 * - Authorization: Bearer token jika tersedia
 */
class AppHeaderInterceptor(
    private val appConfig: AppConfig,
    private val deviceCredentialProvider: (() -> String?)? = null,
    private val authTokenProvider: (() -> String?)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("X-AppId", appConfig.appId.value)
            .header("X-AppVersion", appConfig.appVersion)
            .header("X-Request-Id", UUID.randomUUID().toString())

        val cred = deviceCredentialProvider?.invoke()
        if (!cred.isNullOrBlank() && originalRequest.header("X-Device-Credential") == null) {
            builder.header("X-Device-Credential", cred)
        }

        val token = authTokenProvider?.invoke()
        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
