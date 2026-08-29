package com.ratatoskr.mobile.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LibraryReaderRequest {
    data class LiveSummary(
        val item: LibraryItemPresentation,
    ) : LibraryReaderRequest

    data class Fixture(
        val itemId: String,
    ) : LibraryReaderRequest
}

data class ReaderContentPresentation(
    val item: FixtureLibraryItem,
    val authority: ContentAuthority = ContentAuthority.ContractFixture,
    val integrationPending: Boolean = true,
)

sealed interface LibraryReaderState {
    data object Idle : LibraryReaderState

    data object Loading : LibraryReaderState

    data class Content(
        val reader: ReaderContentPresentation,
    ) : LibraryReaderState

    data class IntegrationPending(
        val item: LibraryItemPresentation,
    ) : LibraryReaderState

    data object Unavailable : LibraryReaderState
}

@Suppress("UNUSED_PARAMETER")
class LibraryReaderStore(
    private val fixtures: FixtureUserContentRepository,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<LibraryReaderState>(LibraryReaderState.Idle)
    val state: StateFlow<LibraryReaderState> = mutableState.asStateFlow()

    fun load(request: LibraryReaderRequest) {
        mutableState.value = LibraryReaderState.Loading
        mutableState.value =
            when (request) {
                is LibraryReaderRequest.LiveSummary -> LibraryReaderState.IntegrationPending(request.item)
                is LibraryReaderRequest.Fixture -> {
                    val item = fixtures.state.value.item(request.itemId)
                    if (item == null) {
                        LibraryReaderState.Unavailable
                    } else {
                        LibraryReaderState.Content(ReaderContentPresentation(item))
                    }
                }
            }
    }
}
