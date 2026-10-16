package id.a3.core.auth.api

import id.a3.core.auth.model.DeviceMeResponse
import id.a3.core.auth.model.DeviceRegisterRequest
import id.a3.core.auth.model.DeviceRegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface DeviceApiService {

    @POST("/v1/devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegisterRequest,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<DeviceRegisterResponse>

    @GET("/v1/devices/me")
    suspend fun getDeviceMe(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<DeviceMeResponse>
}
