package id.a3.core.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PinLoginRequest(
    @SerialName("user_id")
    val userId: String,
    val pin: String
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val role: String // kasir, staf_laundry, manager, owner
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    val user: UserDto,
    @SerialName("business_unit_id")
    val businessUnitId: String,
    @SerialName("server_time")
    val serverTime: String
)

data class AuthSession(
    val user: UserDto,
    val businessUnitId: String,
    val isUnlocked: Boolean = false
)
