package com.ratatoskr.mobile.github

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GithubCatalogStoreTest {
    @Test
    fun empty_query_browses_stable_fixture_order_without_network() =
        runTest {
            val repository = RecordingGithubRepository()
            val graph = GithubApplicationGraph(repository, githubAccess, backgroundScope, identityFactory)
            runCurrent()

            graph.catalogStore.search("")

            val state = assertIs<GithubCatalogState.Content>(graph.catalogStore.state.value)
            assertEquals(listOf("ratatoskr/ratatoskr", "ktorio/ktor", "JetBrains/compose-multiplatform"), state.rows.map { it.fullName })
            assertTrue(state.fixtureAuthority)
            assertTrue(repository.previewUrls.isEmpty())
            assertTrue(repository.actions.isEmpty())
        }

    @Test
    fun bounded_case_insensitive_query_filters_name_and_description() =
        runTest {
            val store = GithubCatalogStore(githubAccess, backgroundScope)
            runCurrent()

            store.search("HTTP CLIENT")

            val state = assertIs<GithubCatalogState.Content>(store.state.value)
            assertEquals(listOf("ktorio/ktor"), state.rows.map { it.fullName })
            assertFalse(state.queryRejected)
        }

    @Test
    fun over_limit_query_preserves_last_results() =
        runTest {
            val store = GithubCatalogStore(githubAccess, backgroundScope)
            runCurrent()
            store.search("ratatoskr")
            val previous = assertIs<GithubCatalogState.Content>(store.state.value)

            store.search("x".repeat(129))

            val rejected = assertIs<GithubCatalogState.Content>(store.state.value)
            assertEquals(previous.acceptedQuery, rejected.acceptedQuery)
            assertEquals(previous.rows, rejected.rows)
            assertTrue(rejected.queryRejected)
        }

    @Test
    fun unpaired_missing_stale_or_malformed_capability_exposes_no_actions() =
        runTest {
            val access = MutableStateFlow<GithubAccess>(GithubAccess.PairingRequired)
            val store = GithubCatalogStore(access, backgroundScope)
            runCurrent()
            assertEquals(GithubCatalogState.PairingRequired, store.state.value)

            access.value = GithubAccess.CapabilityUnavailable
            runCurrent()
            assertEquals(GithubCatalogState.CapabilityUnavailable, store.state.value)
        }
}
