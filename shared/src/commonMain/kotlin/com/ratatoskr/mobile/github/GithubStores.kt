package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.GithubServiceCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface GithubAccess {
    data object PairingRequired : GithubAccess

    data object CapabilityUnavailable : GithubAccess

    data class Available(
        val capability: GithubServiceCapability,
    ) : GithubAccess
}

data class GithubCatalogRow(
    val fullName: String,
    val description: String,
    val canonicalUrl: String,
)

sealed interface GithubCatalogState {
    data object PairingRequired : GithubCatalogState

    data object CapabilityUnavailable : GithubCatalogState

    data class Content(
        val acceptedQuery: String,
        val rows: List<GithubCatalogRow>,
        val queryRejected: Boolean = false,
        val fixtureAuthority: Boolean = true,
    ) : GithubCatalogState
}

class GithubCatalogStore(
    private val access: StateFlow<GithubAccess>,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(access.value.catalogState())
    val state: StateFlow<GithubCatalogState> = mutableState.asStateFlow()

    init {
        scope.launch {
            access.collectLatest { current ->
                mutableState.value = current.catalogState()
            }
        }
    }

    fun search(query: String) {
        val current = mutableState.value as? GithubCatalogState.Content ?: return
        if (query.length > MAX_QUERY_CHARS) {
            mutableState.value = current.copy(queryRejected = true)
            return
        }
        val needle = query.trim().lowercase()
        mutableState.value =
            GithubCatalogState.Content(
                acceptedQuery = query,
                rows =
                    if (needle.isEmpty()) {
                        FIXTURE_ROWS
                    } else {
                        FIXTURE_ROWS.filter { row ->
                            needle in row.fullName.lowercase() || needle in row.description.lowercase()
                        }
                    },
            )
    }

    private fun GithubAccess.catalogState(): GithubCatalogState =
        when (this) {
            GithubAccess.PairingRequired -> GithubCatalogState.PairingRequired
            GithubAccess.CapabilityUnavailable -> GithubCatalogState.CapabilityUnavailable
            is GithubAccess.Available -> GithubCatalogState.Content("", FIXTURE_ROWS)
        }

    private companion object {
        const val MAX_QUERY_CHARS = 128
        val FIXTURE_ROWS =
            listOf(
                GithubCatalogRow(
                    "ratatoskr/ratatoskr",
                    "Ratatoskr contract fixture for knowledge capture and catalog workflows.",
                    "https://github.com/ratatoskr/ratatoskr",
                ),
                GithubCatalogRow(
                    "ktorio/ktor",
                    "Asynchronous Kotlin framework and HTTP client contract fixture.",
                    "https://github.com/ktorio/ktor",
                ),
                GithubCatalogRow(
                    "JetBrains/compose-multiplatform",
                    "Shared Compose UI contract fixture for Android and iOS.",
                    "https://github.com/JetBrains/compose-multiplatform",
                ),
            )
    }
}

data class GithubActionIdentity(
    val confirmationEvidenceRef: String,
    val idempotencyKey: String,
)

fun interface GithubActionIdentityFactory {
    fun create(): GithubActionIdentity
}

data class GithubPreviewFingerprint(
    val target: GithubRepositoryTarget,
    val accountRef: String?,
    val availableActions: Set<GithubActionMode>,
    val capabilityActions: Set<GithubActionMode>,
)

data class GithubPendingConfirmation(
    val mode: GithubActionMode,
    val title: String,
    val disclosure: String,
    val fingerprint: GithubPreviewFingerprint,
)

enum class GithubDetailFailure {
    Unavailable,
    InvalidResponse,
}

sealed interface GithubDetailState {
    data object Idle : GithubDetailState

    data object Loading : GithubDetailState

    data object RePairingRequired : GithubDetailState

    data class Failed(
        val failure: GithubDetailFailure,
        val canRetry: Boolean,
    ) : GithubDetailState

    data class Content(
        val preview: GithubRepositoryPreview,
        val actions: Set<GithubActionMode>,
        val pending: GithubPendingConfirmation? = null,
        val result: GithubActionPresentation? = null,
        val submitting: Boolean = false,
        val outcomeUnknown: Boolean = false,
        val uncertainRetryAvailable: Boolean = false,
    ) : GithubDetailState
}

class GithubDetailStore(
    private val repository: GithubRepository,
    private val access: StateFlow<GithubAccess>,
    private val identityFactory: GithubActionIdentityFactory,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<GithubDetailState>(GithubDetailState.Idle)
    val state: StateFlow<GithubDetailState> = mutableState.asStateFlow()
    private var uncertainAction: UncertainAction? = null
    private var loadGeneration = 0L

    init {
        scope.launch {
            access.collectLatest(::applyAccess)
        }
    }

    fun load(canonicalUrl: String) {
        val capability = (access.value as? GithubAccess.Available)?.capability
        if (capability == null) {
            applyAccess(access.value)
            return
        }
        uncertainAction = null
        val generation = ++loadGeneration
        mutableState.value = GithubDetailState.Loading
        scope.launch {
            val result = repository.preview(canonicalUrl)
            if (generation != loadGeneration) return@launch
            val currentCapability = (access.value as? GithubAccess.Available)?.capability
            if (currentCapability == null) {
                applyAccess(access.value)
                return@launch
            }
            mutableState.value =
                when (result) {
                    is GithubRepositoryResult.Success -> result.value.toContent(canonicalUrl, currentCapability)
                    GithubRepositoryResult.Unauthorized -> GithubDetailState.RePairingRequired
                    GithubRepositoryResult.InvalidResponse ->
                        GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false)
                    is GithubRepositoryResult.Unavailable ->
                        GithubDetailState.Failed(GithubDetailFailure.Unavailable, result.retryable)
                }
        }
    }

    fun select(mode: GithubActionMode) {
        val current = mutableState.value as? GithubDetailState.Content ?: return
        if (current.submitting || mode !in current.actions) return
        val fingerprint = current.fingerprint() ?: return
        if (mode == GithubActionMode.Metadata) {
            submit(current.request(mode, identityFactory.create()), fingerprint)
            return
        }
        if (mode == GithubActionMode.Star && current.preview.accountRef == null) return
        val disclosure =
            when (mode) {
                GithubActionMode.Track ->
                    "Ratatoskr will request desired backup tracking for ${current.preview.target.fullName}. " +
                        "This is not a completed backup and does not perform a GitHub write."
                GithubActionMode.Star ->
                    "For ${current.preview.target.fullName}, Ratatoskr will use ${current.preview.accountRef} for an " +
                        "external GitHub star, update metadata, and request desired backup tracking."
                GithubActionMode.Metadata -> return
            }
        mutableState.value =
            current.copy(
                pending =
                    GithubPendingConfirmation(
                        mode = mode,
                        title = if (mode == GithubActionMode.Star) "Confirm GitHub star" else "Confirm tracking",
                        disclosure = disclosure,
                        fingerprint = fingerprint,
                    ),
                result = null,
                outcomeUnknown = false,
                uncertainRetryAvailable = false,
            )
    }

    fun confirm(pending: GithubPendingConfirmation) {
        val current = mutableState.value as? GithubDetailState.Content ?: return
        if (current.pending != pending || current.fingerprint() != pending.fingerprint) return
        mutableState.value = current.copy(pending = null)
        submit(current.request(pending.mode, identityFactory.create()), pending.fingerprint)
    }

    fun cancel(pending: GithubPendingConfirmation) {
        val current = mutableState.value as? GithubDetailState.Content ?: return
        if (current.pending == pending) mutableState.value = current.copy(pending = null)
    }

    fun retryUncertain() {
        val current = mutableState.value as? GithubDetailState.Content ?: return
        val uncertain = uncertainAction ?: return
        if (!current.outcomeUnknown || !current.uncertainRetryAvailable) return
        if (current.fingerprint() != uncertain.fingerprint) {
            uncertainAction = null
            mutableState.value = current.copy(uncertainRetryAvailable = false)
            return
        }
        submit(uncertain.request, uncertain.fingerprint)
    }

    private fun submit(
        request: GithubActionRequest,
        fingerprint: GithubPreviewFingerprint,
    ) {
        val current = mutableState.value as? GithubDetailState.Content ?: return
        if (current.submitting || current.fingerprint() != fingerprint) return
        uncertainAction = null
        mutableState.value =
            current.copy(
                pending = null,
                result = null,
                submitting = true,
                outcomeUnknown = false,
                uncertainRetryAvailable = false,
            )
        scope.launch {
            val response = repository.action(request)
            val latest = mutableState.value as? GithubDetailState.Content ?: return@launch
            if (!latest.matchesSubmittedContext(fingerprint)) return@launch
            when (response) {
                is GithubRepositoryResult.Success -> {
                    uncertainAction = null
                    mutableState.value = latest.copy(result = response.value.present(), submitting = false)
                }
                GithubRepositoryResult.Unauthorized -> {
                    uncertainAction = null
                    mutableState.value = GithubDetailState.RePairingRequired
                }
                GithubRepositoryResult.InvalidResponse -> {
                    uncertainAction = null
                    mutableState.value = GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false)
                }
                is GithubRepositoryResult.Unavailable -> {
                    if (response.outcomeUnknown) {
                        uncertainAction = UncertainAction(request, fingerprint)
                        mutableState.value =
                            latest.copy(
                                submitting = false,
                                outcomeUnknown = true,
                                uncertainRetryAvailable = true,
                            )
                    } else {
                        uncertainAction = null
                        mutableState.value =
                            GithubDetailState.Failed(GithubDetailFailure.Unavailable, response.retryable)
                    }
                }
            }
        }
    }

    private fun GithubRepositoryPreview.toContent(
        requestedUrl: String,
        capability: GithubServiceCapability,
    ): GithubDetailState {
        if (
            target.canonicalUrl != requestedUrl ||
            !capability.repositoryPreview ||
            !capability.actions.containsAll(availableActions) ||
            (GithubActionMode.Star in availableActions && accountRef == null)
        ) {
            return GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false)
        }
        return GithubDetailState.Content(this, availableActions)
    }

    private fun applyAccess(currentAccess: GithubAccess) {
        when (currentAccess) {
            GithubAccess.PairingRequired -> {
                loadGeneration += 1
                uncertainAction = null
                mutableState.value = GithubDetailState.RePairingRequired
            }
            GithubAccess.CapabilityUnavailable -> {
                loadGeneration += 1
                uncertainAction = null
                mutableState.value = GithubDetailState.Failed(GithubDetailFailure.Unavailable, canRetry = false)
            }
            is GithubAccess.Available -> {
                val current = mutableState.value as? GithubDetailState.Content ?: return
                if (
                    !currentAccess.capability.repositoryPreview ||
                    !currentAccess.capability.actions.containsAll(current.preview.availableActions)
                ) {
                    uncertainAction = null
                    mutableState.value = GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, canRetry = false)
                    return
                }
                val fingerprint =
                    GithubPreviewFingerprint(
                        current.preview.target,
                        current.preview.accountRef,
                        current.preview.availableActions,
                        currentAccess.capability.actions,
                    )
                val retryStillValid = uncertainAction?.fingerprint == fingerprint
                if (!retryStillValid) uncertainAction = null
                mutableState.value =
                    current.copy(
                        actions = current.preview.availableActions,
                        pending = current.pending?.takeIf { it.fingerprint == fingerprint },
                        uncertainRetryAvailable = current.uncertainRetryAvailable && retryStillValid,
                    )
            }
        }
    }

    private fun GithubDetailState.Content.fingerprint(): GithubPreviewFingerprint? {
        val capability = (access.value as? GithubAccess.Available)?.capability ?: return null
        return GithubPreviewFingerprint(preview.target, preview.accountRef, preview.availableActions, capability.actions)
    }

    private fun GithubDetailState.Content.matchesSubmittedContext(fingerprint: GithubPreviewFingerprint): Boolean {
        val capability = (access.value as? GithubAccess.Available)?.capability ?: return false
        return preview.target == fingerprint.target &&
            preview.accountRef == fingerprint.accountRef &&
            preview.availableActions == fingerprint.availableActions &&
            capability.repositoryPreview &&
            capability.actions.containsAll(preview.availableActions)
    }

    private fun GithubDetailState.Content.request(
        mode: GithubActionMode,
        identity: GithubActionIdentity,
    ): GithubActionRequest =
        GithubActionRequest(
            mode = mode,
            target = preview.target,
            accountRef = preview.accountRef.takeIf { mode == GithubActionMode.Star },
            confirmationEvidenceRef = identity.confirmationEvidenceRef,
            idempotencyKey = identity.idempotencyKey,
        )

    private data class UncertainAction(
        val request: GithubActionRequest,
        val fingerprint: GithubPreviewFingerprint,
    )
}

class GithubApplicationGraph(
    repository: GithubRepository,
    access: StateFlow<GithubAccess>,
    scope: CoroutineScope,
    identityFactory: GithubActionIdentityFactory,
) {
    val catalogStore = GithubCatalogStore(access, scope)
    val detailStore = GithubDetailStore(repository, access, identityFactory, scope)

    fun select(row: GithubCatalogRow) = detailStore.load(row.canonicalUrl)
}
