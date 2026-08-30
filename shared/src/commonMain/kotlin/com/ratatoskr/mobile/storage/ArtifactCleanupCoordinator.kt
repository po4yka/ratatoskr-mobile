package com.ratatoskr.mobile.storage

import kotlin.time.Instant

sealed interface ArtifactDeleteResult {
    data object Deleted : ArtifactDeleteResult

    data object Missing : ArtifactDeleteResult

    data object Refused : ArtifactDeleteResult

    data object Failed : ArtifactDeleteResult
}

interface StagedArtifactStore {
    suspend fun inventory(): List<ArtifactInventoryItem>

    suspend fun delete(artifactId: String): ArtifactDeleteResult
}

data class ArtifactCleanupResult(
    val usage: StorageUsage,
    val deletedArtifactIds: Set<String>,
    val failedArtifactIds: Set<String>,
)

class ArtifactCleanupCoordinator(
    private val store: StagedArtifactStore,
    private val policy: ArtifactRetentionPolicy = ArtifactRetentionPolicy(),
) {
    suspend fun cleanup(now: Instant): ArtifactCleanupResult {
        val inventory = store.inventory()
        val decision = policy.cleanup(inventory, now)
        val deleted = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        decision.deleteArtifactIds.sorted().forEach { artifactId ->
            when (store.delete(artifactId)) {
                ArtifactDeleteResult.Deleted,
                ArtifactDeleteResult.Missing,
                -> deleted += artifactId
                ArtifactDeleteResult.Refused,
                ArtifactDeleteResult.Failed,
                -> failed += artifactId
            }
        }
        return ArtifactCleanupResult(
            usage = policy.usage(store.inventory()),
            deletedArtifactIds = deleted,
            failedArtifactIds = failed,
        )
    }
}
