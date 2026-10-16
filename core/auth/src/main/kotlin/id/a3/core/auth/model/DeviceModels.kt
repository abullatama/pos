package id.a3.core.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequest(
    @SerialName("registration_code")
    val registrationCode: String,
    @SerialName("device_id")
    val deviceId: String,
    val name: String
)

@Serializable
data class DeviceRegisterResponse(
    val status: String // "PENDING"
)

@Serializable
data class DeviceMeResponse(
    val status: String, // "PENDING", "ACTIVE", "REVOKED"
    @SerialName("business_unit_id")
    val businessUnitId: String? = null,
    @SerialName("device_credential")
    val deviceCredential: String? = null
)
