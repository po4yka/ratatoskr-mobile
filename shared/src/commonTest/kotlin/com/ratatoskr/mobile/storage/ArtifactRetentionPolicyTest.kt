package com.ratatoskr.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ArtifactRetentionPolicyTest {
    @Test
    fun cleanup_removes_only_accepted_cancelled_expired_orphans() {
        val inventory =
            listOf(
                item("accepted", 10, ArtifactRetentionState.Accepted, referenced = false),
                item("cancelled", 20, ArtifactRetentionState.Cancelled, referenced = false),
                item("temp-old", 30, ArtifactRetentionState.Temporary, createdAt = NOW - 25.hours),
                item("failed-old", 40, ArtifactRetentionState.TerminalFailure, terminalAt = NOW - 8.days),
                item("orphan-old", 50, ArtifactRetentionState.Orphan, createdAt = NOW - 25.hours),
                item("queued", 60, ArtifactRetentionState.Queued, referenced = true),
                item("receipt", 70, ArtifactRetentionState.ReceiptPending, referenced = true),
                item("accepted-referenced", 80, ArtifactRetentionState.Accepted, referenced = true),
            )

        val decision = ArtifactRetentionPolicy().cleanup(inventory, NOW)

        assertEquals(setOf("accepted", "cancelled", "temp-old", "failed-old", "orphan-old"), decision.deleteArtifactIds)
        assertEquals(360, decision.usage.totalBytes)
        assertEquals(60, decision.usage.queuedBytes)
        assertEquals(70, decision.usage.receiptPendingBytes)
        assertEquals(150, decision.usage.reclaimableBytes)
    }

    @Test
    fun unfinished_files_are_never_evicted_at_capacity() {
        val full =
            (1..64).map { index ->
                item(
                    id = "queued-$index",
                    size = 8L * 1024L * 1024L,
                    state = ArtifactRetentionState.Queued,
                    referenced = true,
                )
            }

        val admission = ArtifactRetentionPolicy().admit(full, 1, NOW)

        val refused = assertIs<ArtifactAdmission.Refused>(admission)
        assertEquals(512L * 1024L * 1024L, refused.usage.totalBytes)
        assertEquals(64, refused.usage.artifactCount)
        assertEquals(emptySet(), ArtifactRetentionPolicy().cleanup(full, NOW).deleteArtifactIds)
    }

    private fun item(
        id: String,
        size: Long,
        state: ArtifactRetentionState,
        createdAt: Instant = NOW - 1.hours,
        terminalAt: Instant? = null,
        referenced: Boolean = false,
    ) = ArtifactInventoryItem(id, size, state, createdAt, terminalAt, referenced)

    private companion object {
        val NOW = Instant.parse("2026-08-30T00:00:00Z")
    }
}
