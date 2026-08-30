// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransferContentDigest(
    @SerialName("algorithm") @Required val algorithm: String,
    @SerialName("hex") @Required val hex: String,
)

@Serializable
data class TransferBlobRef(
    @SerialName("digest") @Required val digest: TransferContentDigest,
    @SerialName("length_bytes") @Required val lengthBytes: Long,
    @SerialName("media_type") @Required val mediaType: String,
    @SerialName("owner_service") @Required val ownerService: String,
)
