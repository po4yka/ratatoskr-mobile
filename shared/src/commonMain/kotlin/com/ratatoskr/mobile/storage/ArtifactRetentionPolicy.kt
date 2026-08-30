package com.ratatoskr.mobile.storage

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

enum class ArtifactRetentionState {
    Temporary,
    Queued,
    Uploading,
    ReceiptPending,
    Accepted,
    Cancelled,
    TerminalFailure,
    Orphan,
}

data class ArtifactInventoryItem(
    val artifactId: String,
    val sizeBytes: Long,
    val state: ArtifactRetentionState,
    val createdAt: Instant,
    val terminalAt: Instant? = null,
    val referenced: Boolean = false,
    val withinOwnedRoot: Boolean = true,
    val symbolicLink: Boolean = false,
)

data class StorageUsage(
    val totalBytes: Long,
    val artifactCount: Int,
    val queuedBytes: Long,
    val uploadingBytes: Long,
    val receiptPendingBytes: Long,
    val reclaimableBytes: Long,
    val capacityBytes: Long,
    val capacityCount: Int,
)

data class RetentionDecision(
    val deleteArtifactIds: Set<String>,
    val usage: StorageUsage,
)

sealed interface ArtifactAdmission {
    data object Allowed : ArtifactAdmission

    data class Refused(
        val usage: StorageUsage,
    ) : ArtifactAdmission
}

class ArtifactRetentionPolicy {
    fun cleanup(
        inventory: List<ArtifactInventoryItem>,
        now: Instant,
    ): RetentionDecision {
        val deletable = inventory.filterTo(mutableSetOf()) { it.isDeletable(now) }.mapTo(mutableSetOf()) { it.artifactId }
        return RetentionDecision(deletable, usage(inventory))
    }

    fun admit(
        inventory: List<ArtifactInventoryItem>,
        incomingBytes: Long,
        now: Instant,
    ): ArtifactAdmission {
        val current = usage(inventory)
        if (incomingBytes !in 1..MAX_FILE_BYTES) return ArtifactAdmission.Refused(current)
        val deletable = cleanup(inventory, now).deleteArtifactIds
        val retained = inventory.filterNot { it.artifactId in deletable }
        val afterCleanup = usage(retained)
        return if (
            afterCleanup.totalBytes + incomingBytes > DEFAULT_CAPACITY_BYTES ||
            afterCleanup.artifactCount + 1 > DEFAULT_CAPACITY_COUNT
        ) {
            ArtifactAdmission.Refused(current)
        } else {
            ArtifactAdmission.Allowed
        }
    }

    fun usage(inventory: List<ArtifactInventoryItem>): StorageUsage =
        StorageUsage(
            totalBytes = inventory.sumOf { it.sizeBytes },
            artifactCount = inventory.count { it.state != ArtifactRetentionState.Temporary },
            queuedBytes = inventory.filter { it.state == ArtifactRetentionState.Queued }.sumOf { it.sizeBytes },
            uploadingBytes = inventory.filter { it.state == ArtifactRetentionState.Uploading }.sumOf { it.sizeBytes },
            receiptPendingBytes = inventory.filter { it.state == ArtifactRetentionState.ReceiptPending }.sumOf { it.sizeBytes },
            reclaimableBytes =
                inventory
                    .filter { item ->
                        !item.referenced &&
                            item.withinOwnedRoot &&
                            !item.symbolicLink &&
                            item.state in RECLAIMABLE_STATES
                    }.sumOf { it.sizeBytes },
            capacityBytes = DEFAULT_CAPACITY_BYTES,
            capacityCount = DEFAULT_CAPACITY_COUNT,
        )

    companion object {
        const val MAX_FILE_BYTES = 100L * 1024L * 1024L
        const val DEFAULT_CAPACITY_BYTES = 512L * 1024L * 1024L
        const val DEFAULT_CAPACITY_COUNT = 64
        private val RECLAIMABLE_STATES =
            setOf(
                ArtifactRetentionState.Temporary,
                ArtifactRetentionState.Accepted,
                ArtifactRetentionState.Cancelled,
                ArtifactRetentionState.TerminalFailure,
                ArtifactRetentionState.Orphan,
            )
    }
}

private fun ArtifactInventoryItem.isDeletable(now: Instant): Boolean {
    if (referenced || !withinOwnedRoot || symbolicLink) return false
    return when (state) {
        ArtifactRetentionState.Accepted,
        ArtifactRetentionState.Cancelled,
        -> true
        ArtifactRetentionState.Temporary,
        ArtifactRetentionState.Orphan,
        -> createdAt <= now - 24.hours
        ArtifactRetentionState.TerminalFailure -> terminalAt?.let { it <= now - 7.days } == true
        ArtifactRetentionState.Queued,
        ArtifactRetentionState.Uploading,
        ArtifactRetentionState.ReceiptPending,
        -> false
    }
}
