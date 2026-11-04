package id.a3coffeeshop

import id.a3.core.domain.config.AppConfig
import id.a3.core.domain.config.AppId

object CafeAppConfig : AppConfig {
    override val appId: AppId = AppId.CAFE
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val appName: String = "a3coffeeshop"
}
