package com.ratatoskr.mobile.storage

import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueRecord
import com.ratatoskr.mobile.queue.QueueState
import com.ratatoskr.mobile.share.AndroidStagedArtifactStore
import java.nio.file.Files

class AndroidArtifactLedger(
    private val queue: CaptureQueue,
    private val artifacts: AndroidStagedArtifactStore,
) : StagedArtifactStore {
    override suspend fun inventory(): List<ArtifactInventoryItem> {
        val records =
            queue
                .snapshot()
                .mapNotNull { record ->
                    (record.request.payload as? CapturePayload.FileReference)?.let { it.stagedFileId to record }
                }.toMap()
        return artifacts.publishedFiles().map { file ->
            val record = records[file.name]
            ArtifactInventoryItem(
                artifactId = file.name,
                sizeBytes = file.length().coerceAtLeast(0L),
                state = record?.retentionState() ?: ArtifactRetentionState.Orphan,
                createdAt = record?.request?.createdAt ?: kotlin.time.Instant.DISTANT_PAST,
                terminalAt = record?.projection?.terminatedAt,
                referenced = record != null && !record.stagedArtifactReclaimable,
                withinOwnedRoot = true,
                symbolicLink = Files.isSymbolicLink(file.toPath()),
            )
        }
    }

    override suspend fun delete(artifactId: String): ArtifactDeleteResult =
        if (artifacts.deleteUnreferenced(artifactId)) ArtifactDeleteResult.Deleted else ArtifactDeleteResult.Refused

    private fun QueueRecord.retentionState(): ArtifactRetentionState =
        when {
            uploadReceipt != null && operationId == null -> ArtifactRetentionState.ReceiptPending
            uploadCheckpoint != null -> ArtifactRetentionState.Uploading
            state == QueueState.Cancelled -> ArtifactRetentionState.Cancelled
            state == QueueState.PermanentFailure || state == QueueState.ResolutionConflict -> ArtifactRetentionState.TerminalFailure
            state == QueueState.Accepted || state == QueueState.Tracking || state == QueueState.Completed -> ArtifactRetentionState.Accepted
            else -> ArtifactRetentionState.Queued
        }
}
