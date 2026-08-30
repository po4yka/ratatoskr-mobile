package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.diagnostics.MobileDiagnosticEvent
import com.ratatoskr.mobile.diagnostics.MobileDiagnosticOutcome
import com.ratatoskr.mobile.diagnostics.MobileDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibrarySearchState {
    val query: String

    data class Idle(
        override val query: String = "",
    ) : LibrarySearchState

    data class Invalid(
        override val query: String,
    ) : LibrarySearchState

    data class Loading(
        override val query: String,
    ) : LibrarySearchState

    data class Empty(
        override val query: String,
    ) : LibrarySearchState

    data class Content(
        override val query: String,
        val items: List<LibraryItemPresentation>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val nextPageRetryable: Boolean = false,
    ) : LibrarySearchState

    data class CapabilityUnavailable(
        override val query: String,
    ) : LibrarySearchState

    data class Offline(
        override val query: String,
    ) : LibrarySearchState

    data class Unavailable(
        override val query: String,
    ) : LibrarySearchState

    data class RePairingRequired(
        override val query: String,
    ) : LibrarySearchState
}

class LibrarySearchStore(
    private val repository: LibraryRepository,
    private val access: StateFlow<LibraryAccess>,
    private val scope: CoroutineScope,
    private val diagnostics: MobileDiagnostics = MobileDiagnostics(),
) {
    private val mutableState = MutableStateFlow<LibrarySearchState>(LibrarySearchState.Idle())
    val state: StateFlow<LibrarySearchState> = mutableState.asStateFlow()
    private var generation = 0L

    fun updateQuery(value: String) {
        generation += 1
        mutableState.value = LibrarySearchState.Idle(value)
    }

    fun submit() {
        diagnostics.record(MobileDiagnosticEvent.SearchRequested, MobileDiagnosticOutcome.Started)
        val query = mutableState.value.query.trim()
        if (query.isEmpty() || query.scalarCount() > MAX_QUERY_SCALARS) {
            generation += 1
            mutableState.value = LibrarySearchState.Invalid(mutableState.value.query)
            return
        }
        when (access.value) {
            LibraryAccess.PairingRequired -> {
                generation += 1
                mutableState.value = LibrarySearchState.RePairingRequired(query)
            }
            LibraryAccess.CapabilityUnavailable -> {
                generation += 1
                mutableState.value = LibrarySearchState.CapabilityUnavailable(query)
            }
            is LibraryAccess.Available -> requestFirstPage(query)
        }
    }

    fun retry() {
        when (val current = mutableState.value) {
            is LibrarySearchState.Offline,
            is LibrarySearchState.Unavailable,
            -> submit()
            is LibrarySearchState.Content -> if (current.nextPageRetryable) loadMore()
            else -> Unit
        }
    }

    fun loadMore() {
        val current = mutableState.value as? LibrarySearchState.Content ?: return
        if (!current.hasMore || current.loadingMore) return
        val requestGeneration = generation
        val expectedOffset = current.items.size
        mutableState.value = current.copy(loadingMore = true, nextPageRetryable = false)
        scope.launch {
            val result = repository.search(current.query, PAGE_SIZE, expectedOffset)
            if (generation != requestGeneration) return@launch
            val active = mutableState.value as? LibrarySearchState.Content ?: return@launch
            if (active.query != current.query || active.items.size != expectedOffset) return@launch
            mutableState.value =
                when (result) {
                    is LibraryRepositoryResult.Success ->
                        if (result.value.offset == expectedOffset && result.value.limit == PAGE_SIZE) {
                            active.copy(
                                items = active.items + result.value.items.map(::searchPresentation),
                                hasMore = result.value.hasMore,
                                loadingMore = false,
                            )
                        } else {
                            active.copy(loadingMore = false, nextPageRetryable = false)
                        }
                    LibraryRepositoryResult.Unauthorized -> LibrarySearchState.RePairingRequired(current.query)
                    LibraryRepositoryResult.NotFoundOrNotOwned ->
                        active.copy(loadingMore = false, nextPageRetryable = false)
                    is LibraryRepositoryResult.Unavailable ->
                        active.copy(loadingMore = false, nextPageRetryable = result.retryable)
                }
            diagnostics.record(
                MobileDiagnosticEvent.SearchCompleted,
                when (mutableState.value) {
                    is LibrarySearchState.Content,
                    is LibrarySearchState.Empty,
                    -> MobileDiagnosticOutcome.Succeeded
                    else -> MobileDiagnosticOutcome.Unavailable
                },
            )
        }
    }

    private fun requestFirstPage(query: String) {
        generation += 1
        val requestGeneration = generation
        mutableState.value = LibrarySearchState.Loading(query)
        scope.launch {
            val result = repository.search(query, PAGE_SIZE, 0)
            if (generation != requestGeneration) return@launch
            mutableState.value =
                when (result) {
                    is LibraryRepositoryResult.Success -> {
                        val page = result.value
                        if (page.offset != 0 || page.limit != PAGE_SIZE) {
                            LibrarySearchState.Unavailable(query)
                        } else {
                            val items = page.items.map(::searchPresentation)
                            if (items.isEmpty()) {
                                LibrarySearchState.Empty(query)
                            } else {
                                LibrarySearchState.Content(query, items, page.hasMore)
                            }
                        }
                    }
                    LibraryRepositoryResult.Unauthorized -> LibrarySearchState.RePairingRequired(query)
                    LibraryRepositoryResult.NotFoundOrNotOwned -> LibrarySearchState.Unavailable(query)
                    is LibraryRepositoryResult.Unavailable ->
                        if (result.retryable) LibrarySearchState.Offline(query) else LibrarySearchState.Unavailable(query)
                }
        }
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val MAX_QUERY_SCALARS = 512
    }
}

private fun searchPresentation(item: com.ratatoskr.mobile.api.generated.model.LibraryItem) =
    LibraryItemPresentation(
        analysisId = item.analysisId,
        documentId = item.documentId,
        title = item.title,
        readState = item.readState,
        snippet = item.snippet,
        score = item.score,
        authority = ContentAuthority.LivePlatform,
    )
