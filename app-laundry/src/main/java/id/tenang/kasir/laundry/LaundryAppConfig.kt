package id.tenang.kasir.laundry

import id.a3.core.domain.config.AppConfig
import id.a3.core.domain.config.AppId

object LaundryAppConfig : AppConfig {
    override val appId: AppId = AppId.LAUNDRY
    override val appVersion: String = "1.0.0"
    override val appName: String = "Athia Laundry House"
}
