package id.athialoundry

import id.a3.core.domain.config.AppConfig
import id.a3.core.domain.config.AppId

object LaundryAppConfig : AppConfig {
    override val appId: AppId = AppId.LAUNDRY
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val appName: String = "athialoundry"
}
