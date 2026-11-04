package id.a3.core.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun `default api base url is set to a3-api nen asia v1`() {
        assertEquals("https://a3-api.nen.asia/v1/", AppConfig.DEFAULT_API_BASE_URL)
        assertTrue(AppConfig.DEFAULT_API_BASE_URL.endsWith("/"))
    }

    @Test
    fun `app config inherits default api base url`() {
        val config = object : AppConfig {
            override val appId: AppId = AppId.CAFE
            override val appVersion: String = "1.0.0"
            override val appName: String = "test"
        }

        assertEquals(AppConfig.DEFAULT_API_BASE_URL, config.apiBaseUrl)
    }

    @Test
    fun `app config allows custom override of api base url`() {
        val customUrl = "https://staging.a3-api.nen.asia/v1/"
        val config = object : AppConfig {
            override val appId: AppId = AppId.LAUNDRY
            override val appVersion: String = "1.0.0"
            override val appName: String = "test"
            override val apiBaseUrl: String = customUrl
        }

        assertEquals(customUrl, config.apiBaseUrl)
    }
}
