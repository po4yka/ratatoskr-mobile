// Generated from pinned ratatoskr-contracts JSON Schemas. DO NOT EDIT.
package com.ratatoskr.mobile.transfer.generated

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadChunkReceipt(
    @SerialName("chunk_index") @Required val chunkIndex: Int,
    @SerialName("idempotent_replay") @Required val idempotentReplay: Boolean,
    @SerialName("received_chunks_count") @Required val receivedChunksCount: Int,
    @SerialName("resumption_token") @Required val resumptionToken: String,
)
