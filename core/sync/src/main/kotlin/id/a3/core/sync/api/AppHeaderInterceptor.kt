package id.a3.core.sync.api

import id.a3.core.domain.config.AppConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Interceptor OkHttp yang secara otomatis menambahkan header wajib A3 ke setiap permintaan HTTP:
 * - X-AppId: "cafe" atau "laundry"
 * - X-AppVersion: Versi aplikasi klien
 * - X-Request-Id: UUID unik per permintaan untuk pelacakan log & idempotency tracing
 */
class AppHeaderInterceptor(
    private val appConfig: AppConfig,
    private val deviceCredentialProvider: (() -> String?)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .header("X-AppId", appConfig.appId.value)
            .header("X-AppVersion", appConfig.appVersion)
            .header("X-Request-Id", UUID.randomUUID().toString())

        val cred = deviceCredentialProvider?.invoke()
        if (!cred.isNullOrBlank() && originalRequest.header("X-Device-Credential") == null) {
            builder.header("X-Device-Credential", cred)
        }

        return chain.proceed(builder.build())
    }
}
