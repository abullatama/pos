package id.a3.core.sync.api

import id.a3.core.domain.config.AppConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object SyncApiServiceFactory {

    fun create(
        appConfig: AppConfig,
        baseUrl: String = appConfig.apiBaseUrl,
        deviceCredentialProvider: (() -> String?)? = null,
        authTokenProvider: (() -> String?)? = null
    ): SyncApiService {
        val json = Json { ignoreUnknownKeys = true }
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                AppHeaderInterceptor(
                    appConfig = appConfig,
                    deviceCredentialProvider = deviceCredentialProvider,
                    authTokenProvider = authTokenProvider
                )
            )
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(SyncApiService::class.java)
    }
}
