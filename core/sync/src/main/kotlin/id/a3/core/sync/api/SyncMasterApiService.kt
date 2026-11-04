package id.a3.core.sync.api

import id.a3.core.sync.model.BootstrapResponse
import id.a3.core.sync.model.ChangesResponse
import id.a3.core.sync.model.ConfigResponse
import id.a3.core.sync.model.ServerTimeResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SyncMasterApiService {

    @GET("/v1/bootstrap")
    suspend fun getBootstrap(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<BootstrapResponse>

    @GET("/v1/changes")
    suspend fun getChanges(
        @Query("cursor") cursor: String,
        @Query("limit") limit: Int? = null,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<ChangesResponse>

    @GET("/v1/config")
    suspend fun getConfig(
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<ConfigResponse>

    @GET("/v1/time")
    suspend fun getTime(
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<ServerTimeResponse>
}
