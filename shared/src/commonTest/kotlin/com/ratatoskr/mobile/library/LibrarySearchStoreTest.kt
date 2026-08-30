package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryItem
import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySearchStoreTest {
    @Test
    fun valid_query_renders_live_results_in_platform_order() =
        runTest {
            val repository = FakeRepository(mutableListOf(successPage(0, hasMore = true, ANALYSIS_2, ANALYSIS_1)))
            val store = LibrarySearchStore(repository, available(), this)

            store.updateQuery("  durable queue  ")
            store.submit()
            advanceUntilIdle()

            val content = assertIs<LibrarySearchState.Content>(store.state.value)
            assertEquals("durable queue", content.query)
            assertEquals(listOf(ANALYSIS_2, ANALYSIS_1), content.items.map { it.analysisId })
            assertTrue(content.items.all { it.authority == ContentAuthority.LivePlatform })
            assertEquals(listOf(SearchCall("durable queue", 25, 0)), repository.calls)
        }

    @Test
    fun blank_oversized_and_missing_capability_send_nothing() =
        runTest {
            val repository = FakeRepository()
            val availableStore = LibrarySearchStore(repository, available(), this)

            availableStore.updateQuery(" \n ")
            availableStore.submit()
            assertIs<LibrarySearchState.Invalid>(availableStore.state.value)
            availableStore.updateQuery("x".repeat(513))
            availableStore.submit()
            assertIs<LibrarySearchState.Invalid>(availableStore.state.value)

            val unavailableStore =
                LibrarySearchStore(repository, MutableStateFlow(LibraryAccess.CapabilityUnavailable), this)
            unavailableStore.updateQuery("valid")
            unavailableStore.submit()
            assertIs<LibrarySearchState.CapabilityUnavailable>(unavailableStore.state.value)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun duplicate_load_more_appends_one_page_once() =
        runTest {
            val nextPage = CompletableDeferred<LibraryRepositoryResult<LibraryPage>>()
            val repository =
                DeferredRepository(
                    first = successPage(0, hasMore = true, ANALYSIS_1),
                    byQuery = mutableMapOf("query" to nextPage),
                )
            val store = LibrarySearchStore(repository, available(), this)
            store.updateQuery("query")
            store.submit()
            advanceUntilIdle()

            store.loadMore()
            store.loadMore()
            runCurrent()
            assertEquals(1, repository.nextPageCalls)

            nextPage.complete(successPage(1, hasMore = false, ANALYSIS_2))
            advanceUntilIdle()
            val content = assertIs<LibrarySearchState.Content>(store.state.value)
            assertEquals(listOf(ANALYSIS_1, ANALYSIS_2), content.items.map { it.analysisId })
            assertFalse(content.hasMore)
        }

    @Test
    fun stale_older_query_cannot_replace_newer_results() =
        runTest {
            val alpha = CompletableDeferred<LibraryRepositoryResult<LibraryPage>>()
            val beta = CompletableDeferred<LibraryRepositoryResult<LibraryPage>>()
            val repository = RacingRepository(mapOf("alpha" to alpha, "beta" to beta))
            val store = LibrarySearchStore(repository, available(), this)

            store.updateQuery("alpha")
            store.submit()
            runCurrent()
            store.updateQuery("beta")
            store.submit()
            runCurrent()
            beta.complete(successPage(0, hasMore = false, ANALYSIS_2))
            runCurrent()
            alpha.complete(successPage(0, hasMore = false, ANALYSIS_1))
            advanceUntilIdle()

            val content = assertIs<LibrarySearchState.Content>(store.state.value)
            assertEquals("beta", content.query)
            assertEquals(listOf(ANALYSIS_2), content.items.map { it.analysisId })
        }

    @Test
    fun offline_retry_keeps_query_without_results() =
        runTest {
            val repository =
                FakeRepository(
                    mutableListOf(
                        LibraryRepositoryResult.Unavailable(retryable = true),
                        successPage(0, hasMore = false, ANALYSIS_1),
                    ),
                )
            val store = LibrarySearchStore(repository, available(), this)
            store.updateQuery("recovery")
            store.submit()
            advanceUntilIdle()
            assertEquals(LibrarySearchState.Offline("recovery"), store.state.value)

            store.retry()
            advanceUntilIdle()
            assertIs<LibrarySearchState.Content>(store.state.value)
            assertEquals(2, repository.calls.size)
        }

    @Test
    fun unauthorized_requires_repairing() =
        runTest {
            val repository = FakeRepository(mutableListOf(LibraryRepositoryResult.Unauthorized))
            val store = LibrarySearchStore(repository, available(), this)
            store.updateQuery("private")
            store.submit()
            advanceUntilIdle()

            assertEquals(LibrarySearchState.RePairingRequired("private"), store.state.value)
        }

    private fun available() = MutableStateFlow<LibraryAccess>(LibraryAccess.Available(canReplaceReadState = true))

    private fun successPage(
        offset: Int,
        hasMore: Boolean,
        vararg ids: String,
    ): LibraryRepositoryResult<LibraryPage> =
        LibraryRepositoryResult.Success(
            LibraryPage(
                hasMore = hasMore,
                items =
                    ids.mapIndexed { index, id ->
                        LibraryItem(
                            analysisId = id,
                            documentId = if (id == ANALYSIS_1) DOCUMENT_1 else DOCUMENT_2,
                            readState = if (index % 2 == 0) ReadState.UNREAD else ReadState.READ,
                            title = "Result ${offset + index + 1}",
                            snippet = "Matched snippet ${offset + index + 1}",
                            score = 0.9f - index * 0.1f,
                        )
                    },
                limit = 25,
                offset = offset,
            ),
        )

    private data class SearchCall(
        val query: String,
        val limit: Int,
        val offset: Int,
    )

    private class FakeRepository(
        private val results: MutableList<LibraryRepositoryResult<LibraryPage>> = mutableListOf(),
    ) : LibraryRepository {
        val calls = mutableListOf<SearchCall>()

        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> = error("recent is not search")

        override suspend fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): LibraryRepositoryResult<LibraryPage> {
            calls += SearchCall(query, limit, offset)
            return results.removeAt(0)
        }

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> = error("read state is not search")
    }

    private class DeferredRepository(
        private val first: LibraryRepositoryResult<LibraryPage>,
        private val byQuery: MutableMap<String, CompletableDeferred<LibraryRepositoryResult<LibraryPage>>>,
    ) : LibraryRepository {
        var nextPageCalls = 0

        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> = error("recent is not search")

        override suspend fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): LibraryRepositoryResult<LibraryPage> =
            if (offset == 0) {
                first
            } else {
                nextPageCalls += 1
                byQuery.getValue(query).await()
            }

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> = error("read state is not search")
    }

    private class RacingRepository(
        private val responses: Map<String, CompletableDeferred<LibraryRepositoryResult<LibraryPage>>>,
    ) : LibraryRepository {
        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> = error("recent is not search")

        override suspend fun search(
            query: String,
            limit: Int,
            offset: Int,
        ): LibraryRepositoryResult<LibraryPage> = responses.getValue(query).await()

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> = error("read state is not search")
    }

    private companion object {
        const val ANALYSIS_1 = "00000000-0000-4000-8000-000000000001"
        const val ANALYSIS_2 = "00000000-0000-4000-8000-000000000002"
        const val DOCUMENT_1 = "00000000-0000-4000-8000-000000000011"
        const val DOCUMENT_2 = "00000000-0000-4000-8000-000000000012"
    }
}
