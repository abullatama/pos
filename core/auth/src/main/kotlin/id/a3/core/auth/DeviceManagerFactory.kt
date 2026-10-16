package id.a3.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import id.a3.core.auth.api.DeviceApiService
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

val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "a3_auth_prefs")

object DeviceManagerFactory {

    @Volatile
    private var instance: DeviceManager? = null

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.MODEL.contains("Android SDK built for x86") ||
            android.os.Build.HARDWARE.contains("goldfish") ||
            android.os.Build.HARDWARE.contains("ranchu")
    }

    fun getDefaultBaseUrl(): String {
        return if (isEmulator()) "http://10.0.2.2:8080" else "http://127.0.0.1:8080"
    }

    fun getOrCreate(
        context: Context,
        appConfig: AppConfig,
        baseUrl: String = getDefaultBaseUrl()
    ): DeviceManager {
        return instance ?: synchronized(this) {
            instance ?: create(context, appConfig, baseUrl).also { instance = it }
        }
    }

    fun create(
        context: Context,
        appConfig: AppConfig,
        baseUrl: String = getDefaultBaseUrl()
    ): DeviceManager {
        val appContext = context.applicationContext
        val cryptoManager = TinkCryptoManager.create(appContext)
        val encryptedDataStore = EncryptedAuthDataStore(appContext.authDataStore, cryptoManager)

        val json = Json { ignoreUnknownKeys = true }
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val apiService = retrofit.create(DeviceApiService::class.java)

        return DeviceManager(
            encryptedDataStore = encryptedDataStore,
            apiService = apiService,
            appId = appConfig.appId.value,
            appVersion = appConfig.appVersion
        )
    }
}
