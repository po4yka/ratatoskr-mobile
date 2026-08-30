package com.ratatoskr.mobile.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ArtifactCleanupCoordinatorTest {
    @Test
    fun interrupted_cleanup_converges_without_touching_referenced_or_outside_paths() =
        runTest {
            val store =
                FakeStore(
                    mutableListOf(
                        item("accepted", 10, ArtifactRetentionState.Accepted),
                        item("already-missing", 20, ArtifactRetentionState.Cancelled),
                        item("queued", 30, ArtifactRetentionState.Queued, referenced = true),
                        item("outside", 40, ArtifactRetentionState.Orphan, withinOwnedRoot = false),
                        item("symlink", 50, ArtifactRetentionState.Orphan, symbolicLink = true),
                    ),
                    missing = setOf("already-missing"),
                )

            val first = ArtifactCleanupCoordinator(store).cleanup(NOW)
            val second = ArtifactCleanupCoordinator(store).cleanup(NOW)

            assertEquals(setOf("accepted", "already-missing"), first.deletedArtifactIds)
            assertEquals(emptySet(), second.deletedArtifactIds)
            assertEquals(setOf("queued", "outside", "symlink"), store.items.map { it.artifactId }.toSet())
            assertEquals(120, second.usage.totalBytes)
            assertTrue(store.deleteCalls.none { it == "outside" || it == "symlink" || it == "queued" })
        }

    @Test
    fun delete_failure_remains_counted_and_visible() =
        runTest {
            val store = FakeStore(mutableListOf(item("blocked", 77, ArtifactRetentionState.Accepted)), failing = setOf("blocked"))

            val result = ArtifactCleanupCoordinator(store).cleanup(NOW)

            assertEquals(setOf("blocked"), result.failedArtifactIds)
            assertEquals(77, result.usage.totalBytes)
            assertEquals(77, result.usage.reclaimableBytes)
            assertEquals(listOf("blocked"), store.items.map { it.artifactId })
        }

    private fun item(
        id: String,
        bytes: Long,
        state: ArtifactRetentionState,
        referenced: Boolean = false,
        withinOwnedRoot: Boolean = true,
        symbolicLink: Boolean = false,
    ) = ArtifactInventoryItem(
        artifactId = id,
        sizeBytes = bytes,
        state = state,
        createdAt = NOW - 25.hours,
        referenced = referenced,
        withinOwnedRoot = withinOwnedRoot,
        symbolicLink = symbolicLink,
    )

    private class FakeStore(
        val items: MutableList<ArtifactInventoryItem>,
        private val missing: Set<String> = emptySet(),
        private val failing: Set<String> = emptySet(),
    ) : StagedArtifactStore {
        val deleteCalls = mutableListOf<String>()

        override suspend fun inventory(): List<ArtifactInventoryItem> = items.toList()

        override suspend fun delete(artifactId: String): ArtifactDeleteResult {
            deleteCalls += artifactId
            if (artifactId in failing) return ArtifactDeleteResult.Failed
            val removed = items.removeAll { it.artifactId == artifactId }
            return if (removed) ArtifactDeleteResult.Deleted else ArtifactDeleteResult.Missing
        }
    }

    private companion object {
        val NOW = Instant.parse("2026-08-30T00:00:00Z")
    }
}
