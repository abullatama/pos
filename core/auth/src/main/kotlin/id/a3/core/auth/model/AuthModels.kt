package id.a3.core.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PinLoginRequest(
    @SerialName("user_id")
    val userId: JsonElement,
    val pin: String
) {
    constructor(userId: String, pin: String) : this(
        userId = userId.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(userId),
        pin = pin
    )
}

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token")
    val refreshToken: String
)

@Serializable
data class UserDto(
    @SerialName("id")
    private val rawId: JsonElement,
    val name: String,
    val role: String // kasir, staf_laundry, manager, owner
) {
    constructor(id: String, name: String, role: String) : this(
        rawId = id.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(id),
        name = name,
        role = role
    )

    val id: String
        get() = when (rawId) {
            is JsonPrimitive -> rawId.content
            else -> rawId.toString()
        }
}

@Serializable
data class AccountsResponse(
    val accounts: List<UserDto> = emptyList(),
    @SerialName("server_time")
    val serverTime: String? = null
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
    private val rawBusinessUnitId: JsonElement? = null,
    @SerialName("server_time")
    val serverTime: String
) {
    constructor(
        accessToken: String,
        refreshToken: String,
        expiresIn: Int,
        user: UserDto,
        businessUnitId: String? = null,
        serverTime: String
    ) : this(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresIn,
        user = user,
        rawBusinessUnitId = businessUnitId?.let { JsonPrimitive(it) },
        serverTime = serverTime
    )

    val businessUnitId: String?
        get() = when (rawBusinessUnitId) {
            is JsonPrimitive -> rawBusinessUnitId.content
            else -> null
        }
}

data class AuthSession(
    val user: UserDto,
    val businessUnitId: String? = null,
    val isUnlocked: Boolean = false
)
