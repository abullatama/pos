package id.a3.core.sync.api

import id.a3.core.sync.model.CustomerSearchResponse
import id.a3.core.sync.model.FileUploadResponse
import id.a3.core.sync.model.OrderNotificationStatusResponse
import id.a3.core.sync.model.SyncRequest
import id.a3.core.sync.model.SyncResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SyncApiService {

    @POST("/v1/sync/bundles")
    suspend fun sendBundles(
        @Body request: SyncRequest,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<SyncResponse>

    @Multipart
    @POST("/v1/files")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("file_id") fileId: RequestBody,
        @Part("kind") kind: RequestBody,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<FileUploadResponse>

    @GET("/v1/orders/{id}/notification-status")
    suspend fun getOrderNotificationStatus(
        @Path("id") orderId: String,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<OrderNotificationStatusResponse>

    @GET("/v1/customers/search")
    suspend fun searchCustomers(
        @Query("q") query: String,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<CustomerSearchResponse>
}
