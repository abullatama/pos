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
        get() = "https://a3-api.sidik.cloud/v1/"
}
