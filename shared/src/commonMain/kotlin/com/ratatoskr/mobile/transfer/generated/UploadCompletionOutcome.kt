// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadCompletionOutcome(
    @SerialName("blob_ref") val blobRef: TransferBlobRef? = null,
    @SerialName("outcome") @Required val outcome: String,
    @SerialName("computed_sha256_hex") val computedSha256Hex: String? = null,
    @SerialName("declared_sha256_hex") val declaredSha256Hex: String? = null,
)
