// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadSessionRequest(
    @SerialName("chunk_size_bytes") @Required val chunkSizeBytes: Int,
    @SerialName("declared_size_bytes") @Required val declaredSizeBytes: Long,
    @SerialName("digest") @Required val digest: TransferContentDigest,
    @SerialName("media_type") @Required val mediaType: String,
)
