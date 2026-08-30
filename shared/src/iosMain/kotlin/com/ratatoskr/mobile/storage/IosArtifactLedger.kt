package com.ratatoskr.mobile.storage

import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueRecord
import com.ratatoskr.mobile.queue.QueueState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
class IosArtifactLedger(
    private val queue: CaptureQueue,
    private val roots: List<String>,
) : StagedArtifactStore {
    private val manager = NSFileManager.defaultManager

    override suspend fun inventory(): List<ArtifactInventoryItem> {
        val records =
            queue
                .snapshot()
                .mapNotNull { record ->
                    (record.request.payload as? CapturePayload.FileReference)?.let { it.stagedFileId to record }
                }.toMap()
        return roots.flatMap { root ->
            val names = manager.contentsOfDirectoryAtPath(root, null)?.filterIsInstance<String>().orEmpty()
            names.mapNotNull { name ->
                if (!name.matches(OPAQUE_ID)) return@mapNotNull null
                val path = "$root/$name"
                val attributes = manager.attributesOfItemAtPath(path, null) ?: return@mapNotNull null
                val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: return@mapNotNull null
                val record = records[name]
                ArtifactInventoryItem(
                    artifactId = name,
                    sizeBytes = size.coerceAtLeast(0L),
                    state = record?.retentionState() ?: ArtifactRetentionState.Orphan,
                    createdAt = record?.request?.createdAt ?: kotlin.time.Instant.DISTANT_PAST,
                    terminalAt = record?.projection?.terminatedAt,
                    referenced = record != null && !record.stagedArtifactReclaimable,
                    withinOwnedRoot = true,
                    symbolicLink = attributes[NSFileType] == NSFileTypeSymbolicLink,
                )
            }
        }
    }

    override suspend fun delete(artifactId: String): ArtifactDeleteResult {
        if (!artifactId.matches(OPAQUE_ID)) return ArtifactDeleteResult.Refused
        val existing = roots.map { "$it/$artifactId" }.filter(manager::fileExistsAtPath)
        if (existing.isEmpty()) return ArtifactDeleteResult.Missing
        if (existing.any { manager.attributesOfItemAtPath(it, null)?.get(NSFileType) == NSFileTypeSymbolicLink }) {
            return ArtifactDeleteResult.Refused
        }
        val deleted = existing.all { manager.removeItemAtPath(it, null) }
        return if (deleted) ArtifactDeleteResult.Deleted else ArtifactDeleteResult.Failed
    }

    private fun QueueRecord.retentionState(): ArtifactRetentionState =
        when {
            uploadReceipt != null && operationId == null -> ArtifactRetentionState.ReceiptPending
            uploadCheckpoint != null -> ArtifactRetentionState.Uploading
            state == QueueState.Cancelled -> ArtifactRetentionState.Cancelled
            state == QueueState.PermanentFailure || state == QueueState.ResolutionConflict -> ArtifactRetentionState.TerminalFailure
            state == QueueState.Accepted || state == QueueState.Tracking || state == QueueState.Completed -> ArtifactRetentionState.Accepted
            else -> ArtifactRetentionState.Queued
        }

    private companion object {
        val OPAQUE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}
