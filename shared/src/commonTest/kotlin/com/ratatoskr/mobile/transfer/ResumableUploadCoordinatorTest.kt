package com.ratatoskr.mobile.transfer

import com.ratatoskr.mobile.transfer.generated.TransferBlobRef
import com.ratatoskr.mobile.transfer.generated.TransferContentDigest
import com.ratatoskr.mobile.transfer.generated.UploadChunkReceipt
import com.ratatoskr.mobile.transfer.generated.UploadCompletionOutcome
import com.ratatoskr.mobile.transfer.generated.UploadSessionOpened
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import com.ratatoskr.mobile.transfer.generated.UploadStatusResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

class ResumableUploadCoordinatorTest {
    @Test
    fun resume_after_interruption_sends_only_receiver_missing_chunks() =
        runTest {
            val transport = RecordingTransport(received = setOf(0))
            val coordinator = ResumableUploadCoordinator(transport)

            coordinator.resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint())

            assertEquals(listOf(1), transport.sentIndices)
        }

    @Test
    fun receiver_status_recovers_uncheckpointed_ack() =
        runTest {
            val transport = RecordingTransport(received = setOf(0, 1))
            val stale = checkpoint().copy(receivedChunks = setOf(0))

            val result =
                ResumableUploadCoordinator(transport)
                    .resume("capture-1", "idem-1", declaration(), BytesSource(), stale)

            assertEquals(emptyList(), transport.sentIndices)
            assertEquals(setOf(0, 1), assertIs<UploadAttemptResult.Uploaded>(result).checkpoint.receivedChunks)
        }

    @Test
    fun receiver_status_and_each_ack_are_persisted_before_progress_continues() =
        runTest {
            val persisted = mutableListOf<UploadCheckpoint>()
            val coordinator =
                ResumableUploadCoordinator(
                    transport = RecordingTransport(received = setOf(0)),
                    checkpointSink = {
                        persisted += it
                        TransferResult.Success(Unit)
                    },
                )

            coordinator.resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint())

            assertEquals(listOf(setOf(0), setOf(0, 1)), persisted.map(UploadCheckpoint::receivedChunks))
        }

    @Test
    fun uncertain_finalize_reconciles_without_new_session() =
        runTest {
            val transport = RecordingTransport(received = setOf(0, 1), finalizeFailsOnce = true)
            val coordinator = ResumableUploadCoordinator(transport, now = { Instant.parse("2026-08-30T00:00:00Z") })

            assertIs<UploadAttemptResult.Failed>(
                coordinator.resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint()),
            )
            val result = coordinator.resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint())

            assertEquals(0, transport.openCount)
            assertIs<UploadAttemptResult.Uploaded>(result)
        }

    @Test
    fun expired_session_reopens_same_declaration() =
        runTest {
            val transport = RecordingTransport(received = emptySet())
            val expired = checkpoint().copy(expiresAt = Instant.parse("2026-08-29T00:00:00Z"))

            val result =
                ResumableUploadCoordinator(transport, now = { Instant.parse("2026-08-30T00:00:00Z") })
                    .resume("capture-1", "idem-1", declaration(), BytesSource(), expired)

            assertEquals(1, transport.openCount)
            assertEquals("idem-1", assertIs<UploadAttemptResult.Uploaded>(result).checkpoint.captureIdempotencyKey)
        }

    @Test
    fun receiver_expired_session_reopens_same_declaration() =
        runTest {
            val transport = RecordingTransport(received = setOf(0), statusFailsOnce = TransferFailure.SessionExpired)

            val result =
                ResumableUploadCoordinator(transport, now = { Instant.parse("2026-08-30T00:00:00Z") })
                    .resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint())

            assertEquals(1, transport.openCount)
            assertIs<UploadAttemptResult.Uploaded>(result)
        }

    @Test
    fun changed_staged_bytes_fail_integrity() =
        runTest {
            val changed = BytesSource(metadataDigest = "2".repeat(64))

            val result =
                ResumableUploadCoordinator(RecordingTransport(received = emptySet()))
                    .resume("capture-1", "idem-1", declaration(), changed, checkpoint())

            assertEquals(TransferFailure.Integrity, assertIs<UploadAttemptResult.Failed>(result).failure)
        }

    @Test
    fun receipt_does_not_complete_platform_operation() =
        runTest {
            val result =
                ResumableUploadCoordinator(RecordingTransport(received = setOf(0, 1)))
                    .resume("capture-1", "idem-1", declaration(), BytesSource(), checkpoint())

            assertFalse(assertIs<UploadAttemptResult.Uploaded>(result).platformAccepted)
        }

    private fun declaration() =
        UploadSessionRequest(
            declaredSizeBytes = 100_000,
            mediaType = "application/pdf",
            digest = TransferContentDigest("sha256", "1".repeat(64)),
            chunkSizeBytes = 65_536,
        )

    private fun checkpoint() =
        UploadCheckpoint(
            captureLocalId = "capture-1",
            captureIdempotencyKey = "idem-1",
            declaration = declaration(),
            resumptionToken = TOKEN,
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            receivedChunks = setOf(0),
        )

    private class BytesSource(
        private val metadataDigest: String = "1".repeat(64),
    ) : StagedArtifactSource {
        override suspend fun metadata() =
            TransferResult.Success(StagedArtifactMetadata("artifact-1", 100_000, "application/pdf", metadataDigest))

        override suspend fun read(
            offset: Long,
            length: Int,
        ) = TransferResult.Success(ByteArray(length) { 1 })
    }

    private class RecordingTransport(
        received: Set<Int>,
        private var finalizeFailsOnce: Boolean = false,
        private var statusFailsOnce: TransferFailure? = null,
    ) : BlobReceiptTransport {
        private val recorded = received.toMutableSet()
        val sentIndices = mutableListOf<Int>()
        var openCount = 0

        override suspend fun open(request: UploadSessionRequest): TransferResult<UploadSessionOpened> {
            openCount += 1
            recorded.clear()
            return TransferResult.Success(
                UploadSessionOpened(
                    chunkSizeBytes = request.chunkSizeBytes,
                    expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
                    resumptionToken = TOKEN,
                ),
            )
        }

        override suspend fun status(resumptionToken: String): TransferResult<UploadStatusResponse> {
            statusFailsOnce?.let { failure ->
                statusFailsOnce = null
                return TransferResult.Failure(failure)
            }
            return TransferResult.Success(
                UploadStatusResponse(
                    resumptionToken = resumptionToken,
                    sessionState = "open",
                    receivedChunks = recorded.sorted(),
                    receivedChunksCount = recorded.size,
                    missingChunksCount = 2 - recorded.size,
                ),
            )
        }

        override suspend fun putChunk(
            resumptionToken: String,
            chunkIndex: Int,
            bytes: ByteArray,
        ): TransferResult<UploadChunkReceipt> {
            sentIndices += chunkIndex
            recorded += chunkIndex
            return TransferResult.Success(
                UploadChunkReceipt(
                    chunkIndex = chunkIndex,
                    idempotentReplay = false,
                    receivedChunksCount = recorded.size,
                    resumptionToken = resumptionToken,
                ),
            )
        }

        override suspend fun finalize(resumptionToken: String): TransferResult<UploadCompletionOutcome> {
            if (finalizeFailsOnce) {
                finalizeFailsOnce = false
                return TransferResult.Failure(TransferFailure.Connectivity)
            }
            return TransferResult.Success(
                UploadCompletionOutcome(
                    outcome = "stored",
                    blobRef =
                        TransferBlobRef(
                            ownerService = "ratatoskr-extractor",
                            digest = TransferContentDigest("sha256", "1".repeat(64)),
                            mediaType = "application/pdf",
                            lengthBytes = 100_000,
                        ),
                ),
            )
        }
    }

    private companion object {
        const val TOKEN = "rst_0v8k4a2j9pm1d7n5tp3es6uabfhij1cm4nop5qrs"
    }
}
