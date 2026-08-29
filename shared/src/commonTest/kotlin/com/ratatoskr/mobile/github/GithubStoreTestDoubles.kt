package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.GithubServiceCapability
import kotlinx.coroutines.flow.MutableStateFlow

internal val githubCapability =
    GithubServiceCapability(
        repositoryPreview = true,
        actions = setOf(GithubActionMode.Metadata, GithubActionMode.Track, GithubActionMode.Star),
    )

internal val githubAccess = MutableStateFlow<GithubAccess>(GithubAccess.Available(githubCapability))

internal val githubPreview =
    GithubRepositoryPreview(
        target = GithubRepositoryTarget(42, "owner/repository", "https://github.com/owner/repository"),
        description = "A small repository description.",
        stargazerCount = 123,
        primaryLanguage = "Rust",
        accountRef = "github-account:018f0000-0000-7000-8000-000000000604",
        availableActions = githubCapability.actions,
    )

internal class RecordingGithubRepository(
    var previewResult: GithubRepositoryResult<GithubRepositoryPreview> = GithubRepositoryResult.Success(githubPreview),
    var actionResults: MutableList<GithubRepositoryResult<GithubActionResult>> = mutableListOf(),
) : GithubRepository {
    val previewUrls = mutableListOf<String>()
    val actions = mutableListOf<GithubActionRequest>()

    override suspend fun preview(canonicalUrl: String): GithubRepositoryResult<GithubRepositoryPreview> {
        previewUrls += canonicalUrl
        return previewResult
    }

    override suspend fun action(request: GithubActionRequest): GithubRepositoryResult<GithubActionResult> {
        actions += request
        return actionResults.removeFirstOrNull() ?: GithubRepositoryResult.Unavailable(retryable = false)
    }
}

internal val identityFactory =
    GithubActionIdentityFactory {
        GithubActionIdentity(
            confirmationEvidenceRef = "mobile-confirmation:018f0000-0000-7000-8000-000000000605",
            idempotencyKey = "mobile-github-action.018f0000-0000-7000-8000-000000000606",
        )
    }
