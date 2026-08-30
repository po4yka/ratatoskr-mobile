// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadStatusResponse(
    @SerialName("missing_chunks_count") @Required val missingChunksCount: Int,
    @SerialName("received_chunks") @Required val receivedChunks: List<Int>,
    @SerialName("received_chunks_count") @Required val receivedChunksCount: Int,
    @SerialName("resumption_token") @Required val resumptionToken: String,
    @SerialName("session_state") @Required val sessionState: String,
)
