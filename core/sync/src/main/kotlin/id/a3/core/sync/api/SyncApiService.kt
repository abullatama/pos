package id.a3.core.sync.api

import id.a3.core.sync.model.OrderNotificationStatusResponse
import id.a3.core.sync.model.SyncRequest
import id.a3.core.sync.model.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface SyncApiService {

    @POST("/v1/sync/bundles")
    suspend fun sendBundles(
        @Body request: SyncRequest,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<SyncResponse>

    @GET("/v1/orders/{id}/notification-status")
    suspend fun getOrderNotificationStatus(
        @Path("id") orderId: String,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<OrderNotificationStatusResponse>
}
