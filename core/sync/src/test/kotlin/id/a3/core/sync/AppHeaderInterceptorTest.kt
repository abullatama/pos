package id.a3.core.sync

import id.a3.core.domain.config.AppConfig
import id.a3.core.domain.config.AppId
import id.a3.core.sync.api.AppHeaderInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AppHeaderInterceptorTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `interceptor attaches X-AppId cafe, X-AppVersion, and X-Request-Id to all requests`() {
        val cafeConfig = object : AppConfig {
            override val appId: AppId = AppId.CAFE
            override val appVersion: String = "1.2.0"
            override val appName: String = "a3coffeeshop"
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AppHeaderInterceptor(cafeConfig))
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/v1/time"))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("application/json", recordedRequest.getHeader("Accept"))
        assertEquals("cafe", recordedRequest.getHeader("X-AppId"))
        assertEquals("1.2.0", recordedRequest.getHeader("X-AppVersion"))

        val requestId = recordedRequest.getHeader("X-Request-Id")
        assertNotNull(requestId)
        // Verify valid UUID
        val parsedUuid = UUID.fromString(requestId)
        assertNotNull(parsedUuid)
    }

    @Test
    fun `interceptor attaches X-AppId laundry and distinct X-Request-Id per request`() {
        val laundryConfig = object : AppConfig {
            override val appId: AppId = AppId.LAUNDRY
            override val appVersion: String = "1.0.5"
            override val appName: String = "athialoundry"
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AppHeaderInterceptor(laundryConfig))
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val req1 = Request.Builder().url(mockWebServer.url("/v1/config")).build()
        val req2 = Request.Builder().url(mockWebServer.url("/v1/config")).build()

        client.newCall(req1).execute()
        client.newCall(req2).execute()

        val recorded1 = mockWebServer.takeRequest()
        val recorded2 = mockWebServer.takeRequest()

        assertEquals("laundry", recorded1.getHeader("X-AppId"))
        assertEquals("laundry", recorded2.getHeader("X-AppId"))
        assertEquals("1.0.5", recorded1.getHeader("X-AppVersion"))

        val reqId1 = recorded1.getHeader("X-Request-Id")
        val reqId2 = recorded2.getHeader("X-Request-Id")
        assertNotNull(reqId1)
        assertNotNull(reqId2)
        assertNotEquals("Each request must have unique X-Request-Id", reqId1, reqId2)
    }

    @Test
    fun `interceptor attaches credential and bearer token when providers are supplied`() {
        val cafeConfig = object : AppConfig {
            override val appId: AppId = AppId.CAFE
            override val appVersion: String = "1.2.0"
            override val appName: String = "a3coffeeshop"
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(
                AppHeaderInterceptor(
                    appConfig = cafeConfig,
                    deviceCredentialProvider = { "secret_device_cred_123" },
                    authTokenProvider = { "jwt_access_token_456" }
                )
            )
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/v1/bootstrap"))
            .build()

        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("secret_device_cred_123", recorded.getHeader("X-Device-Credential"))
        assertEquals("Bearer jwt_access_token_456", recorded.getHeader("Authorization"))
    }
}
