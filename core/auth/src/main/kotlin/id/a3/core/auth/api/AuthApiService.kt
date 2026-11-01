package id.a3.core.auth.api

import id.a3.core.auth.model.AccountsResponse
import id.a3.core.auth.model.PinLoginRequest
import id.a3.core.auth.model.RefreshTokenRequest
import id.a3.core.auth.model.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @GET("/v1/accounts")
    suspend fun getAccounts(
        @Header("X-Device-Credential") deviceCredential: String? = null,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<AccountsResponse>

    @POST("/v1/auth/pin-login")
    suspend fun pinLogin(
        @Body request: PinLoginRequest,
        @Header("X-Device-Credential") deviceCredential: String? = null,
        @Header("X-AppId") appId: String? = null,
        @Header("X-AppVersion") appVersion: String? = null
    ): Response<TokenResponse>

    @POST("/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest,
        @Header("X-Device-Credential") deviceCredential: String? = null
    ): Response<TokenResponse>
}
