package id.a3.core.sync.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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

@Serializable
data class OrderNotificationStatusResponse(
    val status: String? = null, // "none", "queued", "sent", "failed"
    @SerialName("last_error")
    val lastError: String? = null,
    @SerialName("sent_at")
    val sentAt: String? = null,
    val notifications: List<OrderNotificationItemDto> = emptyList(),
    @SerialName("server_time")
    val serverTime: String? = null
) {
    val effectiveStatus: String
        get() = status ?: notifications.firstOrNull()?.status ?: "none"

    val effectiveError: String?
        get() = lastError
}

@Serializable
data class OrderNotificationItemDto(
    val kind: String,
    val status: String, // "none", "queued", "sent", "failed"
    val attempts: Int = 0,
    @SerialName("last_attempt_at")
    val lastAttemptAt: String? = null
)

@Serializable
data class ConfigResponse(
    val settings: Map<String, JsonElement> = emptyMap(),
    @SerialName("feature_flags")
    val featureFlags: Map<String, Boolean> = emptyMap(),
    @SerialName("min_app_version")
    val minAppVersion: String? = null,
    val labels: Map<String, String> = emptyMap()
)

@Serializable
data class FileUploadResponse(
    @SerialName("file_id")
    val fileId: String? = null,
    val id: String? = null,
    val filename: String? = null,
    val size: Long? = null,
    @SerialName("mime_type")
    val mimeType: String? = null,
    @SerialName("server_time")
    val serverTime: String? = null
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientIdStringSerializer : kotlinx.serialization.KSerializer<String?> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("LenientIdString", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String? {
        return if (decoder is kotlinx.serialization.json.JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is kotlinx.serialization.json.JsonNull) null
            else runCatching { (element as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull() ?: element.toString()
        } else {
            decoder.decodeString()
        }
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }
}

@Serializable
data class RemoteCustomerDto(
    @Serializable(with = LenientIdStringSerializer::class)
    val id: String? = null,
    val name: String,
    val phone: String,
    @SerialName("wa_opt_out")
    val waOptOut: Boolean = false
) {
    val idString: String
        get() = id ?: ""
}

@Serializable
data class CustomerSearchResponse(
    val customers: List<RemoteCustomerDto> = emptyList(),
    @SerialName("server_time")
    val serverTime: String? = null
)

@Serializable
data class ServerTimeResponse(
    @SerialName("server_time")
    val serverTime: String,
    val timezone: String? = null
)
