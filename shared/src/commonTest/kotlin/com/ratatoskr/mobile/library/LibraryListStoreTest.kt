package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryItem
import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryListStoreTest {
    @Test
    fun recent_items_keep_platform_order() =
        runTest {
            val repository = FakeRepository(recent = mutableListOf(successPage()))
            val store = LibraryListStore(repository, available(), this)

            store.refresh()
            advanceUntilIdle()

            val content = assertIs<LibraryListState.Content>(store.state.value)
            assertEquals(listOf(ANALYSIS_2, ANALYSIS_1), content.items.map { it.analysisId })
            assertEquals(listOf(ReadState.UNREAD, ReadState.READ), content.items.map { it.readState })
        }

    @Test
    fun missing_capability_sends_no_request() =
        runTest {
            val repository = FakeRepository()
            val store = LibraryListStore(repository, MutableStateFlow(LibraryAccess.CapabilityUnavailable), this)

            store.refresh()
            advanceUntilIdle()

            assertEquals(LibraryListState.CapabilityUnavailable, store.state.value)
            assertEquals(0, repository.recentCalls)
        }

    @Test
    fun authoritative_read_response_updates_only_read_state() =
        runTest {
            val repository =
                FakeRepository(
                    recent = mutableListOf(successPage()),
                    replacements = mutableListOf(LibraryRepositoryResult.Success(ReadStateResource(ReadState.READ))),
                )
            val store = LibraryListStore(repository, available(), this)
            store.refresh()
            advanceUntilIdle()
            val before = content(store)

            store.replaceReadState(ANALYSIS_2, ReadState.READ)
            advanceUntilIdle()

            val after = content(store)
            assertEquals(ReadState.READ, after.items.first().readState)
            assertEquals(before.items[1], after.items[1])
            assertNull(after.mutationError)
        }

    @Test
    fun uncertain_read_response_keeps_last_confirmed_state() =
        runTest {
            val repository =
                FakeRepository(
                    recent = mutableListOf(successPage()),
                    replacements =
                        mutableListOf(
                            LibraryRepositoryResult.Unavailable(retryable = true, outcomeUnknown = true),
                        ),
                )
            val store = LibraryListStore(repository, available(), this)
            store.refresh()
            advanceUntilIdle()

            store.replaceReadState(ANALYSIS_2, ReadState.READ)
            advanceUntilIdle()

            val content = content(store)
            assertEquals(ReadState.UNREAD, content.items.first().readState)
            assertEquals(LibraryMutationError.OutcomeUnknown, content.mutationError)
        }

    @Test
    fun revocation_requests_repairing() =
        runTest {
            val repository = FakeRepository(recent = mutableListOf(LibraryRepositoryResult.Unauthorized))
            val store = LibraryListStore(repository, available(), this)

            store.refresh()
            advanceUntilIdle()

            assertEquals(LibraryListState.RePairingRequired, store.state.value)
        }

    private fun available() = MutableStateFlow<LibraryAccess>(LibraryAccess.Available(canReplaceReadState = true))

    private fun content(store: LibraryListStore) = assertIs<LibraryListState.Content>(store.state.value)

    private fun successPage() =
        LibraryRepositoryResult.Success(
            LibraryPage(
                hasMore = false,
                items =
                    listOf(
                        LibraryItem(ANALYSIS_2, DOCUMENT_2, ReadState.UNREAD, "Second"),
                        LibraryItem(ANALYSIS_1, DOCUMENT_1, ReadState.READ, "First"),
                    ),
                limit = 25,
                offset = 0,
            ),
        )

    private class FakeRepository(
        private val recent: MutableList<LibraryRepositoryResult<LibraryPage>> = mutableListOf(),
        private val replacements: MutableList<LibraryRepositoryResult<ReadStateResource>> = mutableListOf(),
    ) : LibraryRepository {
        var recentCalls = 0

        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> {
            recentCalls += 1
            return recent.removeAt(0)
        }

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> = replacements.removeAt(0)
    }

    private companion object {
        const val ANALYSIS_1 = "00000000-0000-4000-8000-000000000001"
        const val ANALYSIS_2 = "00000000-0000-4000-8000-000000000002"
        const val DOCUMENT_1 = "00000000-0000-4000-8000-000000000011"
        const val DOCUMENT_2 = "00000000-0000-4000-8000-000000000012"
    }
}
