package id.a3.core.auth

import android.content.Context
import id.a3.core.auth.api.AuthApiService
import id.a3.core.auth.crypto.TinkCryptoManager
import id.a3.core.auth.datastore.EncryptedAuthDataStore
import id.a3.core.domain.config.AppConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object AuthManagerFactory {

    private var instance: AuthManager? = null

    private fun getDefaultBaseUrl(appConfig: AppConfig?): String {
        return appConfig?.apiBaseUrl ?: AppConfig.DEFAULT_API_BASE_URL
    }

    fun getOrCreate(
        context: Context,
        appConfig: AppConfig,
        baseUrl: String = getDefaultBaseUrl(appConfig)
    ): AuthManager {
        return instance ?: synchronized(this) {
            instance ?: create(context, appConfig, baseUrl).also { instance = it }
        }
    }

    fun create(
        context: Context,
        appConfig: AppConfig,
        baseUrl: String = getDefaultBaseUrl(appConfig)
    ): AuthManager {
        val appContext = context.applicationContext
        val cryptoManager = TinkCryptoManager.create(appContext)
        val encryptedDataStore = EncryptedAuthDataStore(
            dataStore = appContext.authDataStore,
            cryptoManager = cryptoManager,
            hardwareIdProvider = { DeviceManagerFactory.getHardwareDeviceId(appContext) }
        )

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
        val okHttpClient = OkHttpClient.Builder()
            .dns(id.a3.core.auth.api.RobustDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("X-AppId", appConfig.appId.value)
                    .header("X-AppVersion", appConfig.appVersion)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(AuthApiService::class.java)

        return AuthManager(
            encryptedDataStore = encryptedDataStore,
            apiService = apiService,
            appId = appConfig.appId.value,
            appVersion = appConfig.appVersion
        )
    }
}
