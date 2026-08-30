// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadSessionOpened(
    @SerialName("chunk_size_bytes") @Required val chunkSizeBytes: Int,
    @SerialName("expires_at") @Required val expiresAt: kotlin.time.Instant,
    @SerialName("resumption_token") @Required val resumptionToken: String,
)
