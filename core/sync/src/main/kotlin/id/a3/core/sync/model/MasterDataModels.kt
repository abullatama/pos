package id.a3.core.sync.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BootstrapResponse(
    val section: String? = null,
    val records: List<JsonObject> = emptyList(),
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    val done: Boolean = true,
    @SerialName("delta_cursor")
    val deltaCursor: String? = null
)

@Serializable
data class ChangesResponse(
    val changes: List<ChangeItemDto> = emptyList(),
    @SerialName("next_cursor")
    val nextCursor: String,
    @SerialName("has_more")
    val hasMore: Boolean = false
)

@Serializable
data class ChangeItemDto(
    val table: String,
    val op: String, // "upsert" or "delete"
    val record: JsonObject
)
