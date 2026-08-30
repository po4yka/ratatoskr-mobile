package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.TransferBlobRef
import com.ratatoskr.mobile.transfer.generated.UploadChunkReceipt
import com.ratatoskr.mobile.transfer.generated.UploadCompletionOutcome
import com.ratatoskr.mobile.transfer.generated.UploadSessionOpened
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import com.ratatoskr.mobile.transfer.generated.UploadStatusResponse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class StagedArtifactMetadata(
    val artifactId: String,
    val sizeBytes: Long,
    val mediaType: String,
    val sha256Hex: String,
)

interface StagedArtifactSource {
    suspend fun metadata(): TransferResult<StagedArtifactMetadata>

    suspend fun read(
        offset: Long,
        length: Int,
    ): TransferResult<ByteArray>
}

interface BlobReceiptTransport {
    suspend fun open(request: UploadSessionRequest): TransferResult<UploadSessionOpened>

    suspend fun status(resumptionToken: String): TransferResult<UploadStatusResponse>

    suspend fun putChunk(
        resumptionToken: String,
        chunkIndex: Int,
        bytes: ByteArray,
    ): TransferResult<UploadChunkReceipt>

    suspend fun finalize(resumptionToken: String): TransferResult<UploadCompletionOutcome>
}

@Serializable
data class UploadCheckpoint(
    val captureLocalId: String,
    val captureIdempotencyKey: String,
    val declaration: UploadSessionRequest,
    val resumptionToken: String,
    val expiresAt: Instant,
    val receivedChunks: Set<Int> = emptySet(),
)

sealed interface UploadAttemptResult {
    data class InProgress(
        val checkpoint: UploadCheckpoint,
    ) : UploadAttemptResult

    data class Uploaded(
        val checkpoint: UploadCheckpoint,
        val blobRef: TransferBlobRef,
        val platformAccepted: Boolean = false,
    ) : UploadAttemptResult

    data class Failed(
        val failure: TransferFailure,
    ) : UploadAttemptResult
}

class ResumableUploadCoordinator(
    private val transport: BlobReceiptTransport,
    private val checkpointSink: suspend (UploadCheckpoint) -> TransferResult<Unit> = {
        TransferResult.Success(Unit)
    },
    private val now: () -> Instant = {
        kotlin.time.Clock.System
            .now()
    },
) {
    suspend fun resume(
        captureLocalId: String,
        captureIdempotencyKey: String,
        declaration: UploadSessionRequest,
        source: StagedArtifactSource,
        checkpoint: UploadCheckpoint? = null,
    ): UploadAttemptResult {
        if (
            checkpoint != null &&
            (
                checkpoint.captureLocalId != captureLocalId ||
                    checkpoint.captureIdempotencyKey != captureIdempotencyKey ||
                    checkpoint.declaration != declaration
            )
        ) {
            return UploadAttemptResult.Failed(TransferFailure.InvalidDeclaration)
        }
        val plan =
            (UploadPlan.create(declaration.declaredSizeBytes, declaration.chunkSizeBytes) as? TransferResult.Success)
                ?.value ?: return UploadAttemptResult.Failed(TransferFailure.InvalidDeclaration)
        val metadata =
            when (val result = source.metadata()) {
                is TransferResult.Success -> result.value
                is TransferResult.Failure -> return UploadAttemptResult.Failed(result.reason)
            }
        if (
            metadata.sizeBytes != declaration.declaredSizeBytes ||
            metadata.mediaType != declaration.mediaType ||
            metadata.sha256Hex != declaration.digest.hex ||
            declaration.digest.algorithm != "sha256"
        ) {
            return UploadAttemptResult.Failed(TransferFailure.Integrity)
        }

        suspend fun openCheckpoint(): TransferResult<UploadCheckpoint> =
            when (val result = transport.open(declaration)) {
                is TransferResult.Success -> {
                    if (result.value.chunkSizeBytes != declaration.chunkSizeBytes || result.value.expiresAt <= now()) {
                        TransferResult.Failure(TransferFailure.InvalidResponse)
                    } else {
                        TransferResult.Success(
                            UploadCheckpoint(
                                captureLocalId = captureLocalId,
                                captureIdempotencyKey = captureIdempotencyKey,
                                declaration = declaration,
                                resumptionToken = result.value.resumptionToken,
                                expiresAt = result.value.expiresAt,
                            ),
                        )
                    }
                }
                is TransferResult.Failure -> result
            }

        var opened =
            checkpoint?.takeIf { it.expiresAt > now() } ?: when (val result = openCheckpoint()) {
                is TransferResult.Success -> result.value
                is TransferResult.Failure -> return UploadAttemptResult.Failed(result.reason)
            }
        var statusResult = transport.status(opened.resumptionToken)
        if (
            statusResult is TransferResult.Failure &&
            statusResult.reason in setOf(TransferFailure.SessionExpired, TransferFailure.SessionUnknown)
        ) {
            opened =
                when (val result = openCheckpoint()) {
                    is TransferResult.Success -> result.value
                    is TransferResult.Failure -> return UploadAttemptResult.Failed(result.reason)
                }
            statusResult = transport.status(opened.resumptionToken)
        }
        val status =
            when (statusResult) {
                is TransferResult.Success -> statusResult.value
                is TransferResult.Failure -> return UploadAttemptResult.Failed(statusResult.reason)
            }
        val received = status.receivedChunks
        if (
            status.resumptionToken != opened.resumptionToken ||
            status.sessionState !in setOf("open", "finalized") ||
            received != received.distinct().sorted() ||
            received.any { it !in 0 until plan.chunkCount } ||
            status.receivedChunksCount != received.size ||
            status.missingChunksCount != plan.chunkCount - received.size
        ) {
            return UploadAttemptResult.Failed(TransferFailure.InvalidResponse)
        }
        var current = opened.copy(receivedChunks = received.toSet())
        persist(current)?.let { return UploadAttemptResult.Failed(it) }
        for (index in 0 until plan.chunkCount) {
            if (index in current.receivedChunks) continue
            val bytes =
                when (val read = source.read(index.toLong() * plan.chunkSizeBytes, plan.chunkLength(index)!!)) {
                    is TransferResult.Success -> read.value
                    is TransferResult.Failure -> return UploadAttemptResult.Failed(read.reason)
                }
            if (bytes.size != plan.chunkLength(index)) {
                return UploadAttemptResult.Failed(TransferFailure.Integrity)
            }
            when (val receipt = transport.putChunk(opened.resumptionToken, index, bytes)) {
                is TransferResult.Success -> {
                    if (
                        receipt.value.resumptionToken != opened.resumptionToken ||
                        receipt.value.chunkIndex != index
                    ) {
                        return UploadAttemptResult.Failed(TransferFailure.InvalidResponse)
                    }
                    current = current.copy(receivedChunks = current.receivedChunks + index)
                    persist(current)?.let { return UploadAttemptResult.Failed(it) }
                }
                is TransferResult.Failure -> return UploadAttemptResult.Failed(receipt.reason)
            }
        }
        val completion =
            when (val result = transport.finalize(current.resumptionToken)) {
                is TransferResult.Success -> result.value
                is TransferResult.Failure -> return UploadAttemptResult.Failed(result.reason)
            }
        if (completion.outcome == "digest_mismatch") {
            return UploadAttemptResult.Failed(TransferFailure.Integrity)
        }
        val blobRef = completion.blobRef
        if (
            completion.outcome != "stored" ||
            blobRef == null ||
            blobRef.digest.algorithm != declaration.digest.algorithm ||
            blobRef.digest.hex != declaration.digest.hex ||
            blobRef.mediaType != declaration.mediaType ||
            blobRef.lengthBytes != declaration.declaredSizeBytes
        ) {
            return UploadAttemptResult.Failed(TransferFailure.InvalidResponse)
        }
        return UploadAttemptResult.Uploaded(
            checkpoint = current,
            blobRef = blobRef,
            platformAccepted = false,
        )
    }

    private suspend fun persist(checkpoint: UploadCheckpoint): TransferFailure? =
        try {
            when (val result = checkpointSink(checkpoint)) {
                is TransferResult.Success -> null
                is TransferResult.Failure -> result.reason
            }
        } catch (_: Throwable) {
            TransferFailure.LocalPersistence
        }
}
