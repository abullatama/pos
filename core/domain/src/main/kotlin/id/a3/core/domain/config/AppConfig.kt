package id.a3.core.domain.config

enum class AppId(val value: String) {
    CAFE("cafe"),
    LAUNDRY("laundry")
}

interface AppConfig {
    val appId: AppId
    val appVersion: String
    val appName: String
    val apiBaseUrl: String
        get() = DEFAULT_API_BASE_URL

    companion object {
        const val DEFAULT_API_BASE_URL = "https://a3-api.nen.asia/v1/"
    }
}
