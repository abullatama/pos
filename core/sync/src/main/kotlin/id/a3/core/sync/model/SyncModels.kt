package id.a3.core.sync.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncRequest(
    val bundles: List<SyncBundleDto>
)

@Serializable
data class SyncBundleDto(
    @SerialName("bundle_id")
    val bundleId: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("shift_id")
    val shiftId: String? = null,
    val events: List<SyncEventDto>
)

@Serializable
data class SyncEventDto(
    @SerialName("event_id")
    val eventId: String,
    val type: String,
    @SerialName("occurred_at")
    val occurredAt: String,
    val payload: JsonElement
)

@Serializable
data class SyncResponse(
    val results: List<BundleResultDto>,
    @SerialName("server_time")
    val serverTime: String
)

@Serializable
data class BundleResultDto(
    @SerialName("bundle_id")
    val bundleId: String,
    val status: String,
    val flags: List<String>? = null,
    @SerialName("reason_code")
    val reasonCode: String? = null,
    val retryable: Boolean? = null,
    val message: String? = null,
    @SerialName("server_received_at")
    val serverReceivedAt: String? = null
)
