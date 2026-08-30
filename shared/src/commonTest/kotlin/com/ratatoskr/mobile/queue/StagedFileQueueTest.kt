package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.transfer.UploadCheckpoint
import com.ratatoskr.mobile.transfer.generated.TransferBlobRef
import com.ratatoskr.mobile.transfer.generated.TransferContentDigest
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class StagedFileQueueTest {
    @Test
    fun transfer_checkpoint_preserves_capture_identity() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(fileRequest(), "capture-idempotency").success()
            val checkpoint = checkpoint(stored)

            val updated = queue.recordUploadCheckpoint(stored.localId, checkpoint).success()

            assertEquals(stored.localId, updated.uploadCheckpoint?.captureLocalId)
            assertEquals(stored.idempotencyKey, updated.uploadCheckpoint?.captureIdempotencyKey)
            assertEquals("artifact-1", (updated.request.payload as CapturePayload.FileReference).stagedFileId)
            assertEquals(stored.sourceSequence, updated.sourceSequence)
        }

    @Test
    fun platform_receipt_acceptance_releases_bytes_only_after_binding() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(fileRequest(), "capture-idempotency").success()
            queue.recordUploadCheckpoint(stored.localId, checkpoint(stored)).success()

            val uploaded = queue.recordUploadReceipt(stored.localId, receipt()).success()

            assertEquals(receipt(), uploaded.uploadReceipt)
            assertFalse(uploaded.stagedArtifactReclaimable)

            val accepted = queue.recordAccepted(stored.localId, OPERATION_ID).success()

            assertTrue(accepted.stagedArtifactReclaimable)
            assertEquals(receipt(), accepted.uploadReceipt)
        }

    private fun fileRequest() =
        captureRequest(
            payload =
                CapturePayload.FileReference(
                    stagedFileId = "artifact-1",
                    displayName = "synthetic.pdf",
                    mediaType = "application/pdf",
                    byteSize = 3,
                ),
        )

    private fun checkpoint(record: QueueRecord) =
        UploadCheckpoint(
            captureLocalId = record.localId,
            captureIdempotencyKey = record.idempotencyKey,
            declaration =
                UploadSessionRequest(
                    declaredSizeBytes = 3,
                    chunkSizeBytes = 65_536,
                    digest = DIGEST,
                    mediaType = "application/pdf",
                ),
            resumptionToken = "resume-token",
            expiresAt = NOW + 1.hours,
            receivedChunks = setOf(0),
        )

    private fun receipt() =
        TransferBlobRef(
            ownerService = "blob-store",
            digest = DIGEST,
            mediaType = "application/pdf",
            lengthBytes = 3,
        )

    private companion object {
        val DIGEST = TransferContentDigest("sha256", "a".repeat(64))
        const val OPERATION_ID = "018f0000-0000-7000-8000-000000000001"
    }
}
