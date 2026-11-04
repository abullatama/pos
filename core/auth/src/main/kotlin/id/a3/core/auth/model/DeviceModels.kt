package id.a3.core.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
    val status: String, // "PENDING"
    @SerialName("device_id")
    val deviceId: String? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("server_time")
    val serverTime: String? = null
)

@Serializable
data class DeviceMeResponse(
    val status: String, // "PENDING", "ACTIVE", "REVOKED"
    @SerialName("device_id")
    val deviceId: String? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("business_unit_id")
    private val rawBusinessUnitId: JsonElement? = null,
    @SerialName("device_credential")
    val deviceCredential: String? = null,
    @SerialName("server_time")
    val serverTime: String? = null
) {
    val businessUnitId: String?
        get() = when (rawBusinessUnitId) {
            is JsonPrimitive -> rawBusinessUnitId.content
            else -> null
        }
}

@Serializable
data class DeviceHeartbeatRequest(
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("app_id")
    val appId: String? = null,
    @SerialName("outbox_pending")
    val outboxPending: Int = 0,
    @SerialName("outbox_problem")
    val outboxProblem: Int = 0,
    @SerialName("battery_percent")
    val batteryPercent: Int? = null,
    @SerialName("last_sync_at")
    val lastSyncAt: String? = null,
    @SerialName("printer_status")
    val printerStatus: String = "unknown"
)
