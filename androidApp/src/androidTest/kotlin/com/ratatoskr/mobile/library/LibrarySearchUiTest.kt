package com.ratatoskr.mobile.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.api.generated.model.ReadState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySearchUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun search_flow_renders_ranked_live_results_and_loads_next_page_once() {
        var state: LibrarySearchState by mutableStateOf(content(hasMore = true))
        var loadMore = 0
        var opened: String? = null
        compose.setContent {
            LibrarySearchSurface(
                state = state,
                onQueryChanged = {},
                onSubmit = {},
                onRetry = {},
                onLoadMore = {
                    loadMore += 1
                    state = (state as LibrarySearchState.Content).copy(loadingMore = true)
                },
                onOpen = { opened = it.analysisId },
            )
        }

        compose.onNodeWithText("Live Platform search results").assertIsDisplayed()
        compose.onNode(hasContentDescription("Highest ranked, Unread")).assertExists()
        compose.onNodeWithText("Highest ranked").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ID_1, opened) }
        compose.onNodeWithText("Load more").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, loadMore) }
        compose.onNodeWithText("Loading more…").assertIsDisplayed()
    }

    @Test
    fun validation_capability_empty_offline_and_repairing_states_are_truthful() {
        var state: LibrarySearchState by mutableStateOf(LibrarySearchState.Invalid(" "))
        compose.setContent {
            LibrarySearchSurface(state, {}, {}, {}, {}, {})
        }

        compose.onNodeWithText("Enter 1–512 characters").assertIsDisplayed()
        compose.runOnIdle { state = LibrarySearchState.CapabilityUnavailable("query") }
        compose.onNodeWithText("Search is unavailable on this Ratatoskr instance").assertIsDisplayed()
        compose.runOnIdle { state = LibrarySearchState.Empty("query") }
        compose.onNodeWithText("No matches for this search").assertIsDisplayed()
        compose.runOnIdle { state = LibrarySearchState.Offline("query") }
        compose.onNodeWithText("Search is offline").assertIsDisplayed()
        compose.runOnIdle { state = LibrarySearchState.RePairingRequired("query") }
        compose.onNodeWithText("Pair this device again").assertIsDisplayed()
    }

    @Test
    fun stale_response_never_replaces_current_query() {
        var state: LibrarySearchState by mutableStateOf(content(query = "beta", hasMore = false))
        compose.setContent { LibrarySearchSurface(state, {}, {}, {}, {}, {}) }

        compose.onNodeWithText("Search: beta").assertIsDisplayed()
        compose.runOnIdle {
            state = content(query = "beta", hasMore = false).copy(items = listOf(item(ID_2, "Current beta")))
        }
        compose.onNodeWithText("Current beta").assertIsDisplayed()
        compose.onNodeWithText("Highest ranked").assertDoesNotExist()
    }

    private fun content(
        query: String = "durable",
        hasMore: Boolean,
    ) = LibrarySearchState.Content(
        query = query,
        items = listOf(item(ID_1, "Highest ranked"), item(ID_2, "Second ranked")),
        hasMore = hasMore,
    )

    private fun item(
        id: String,
        title: String,
    ) = LibraryItemPresentation(
        analysisId = id,
        documentId = DOCUMENT_ID,
        title = title,
        readState = ReadState.UNREAD,
        snippet = "Matched evidence",
        score = 0.9f,
    )

    private companion object {
        const val ID_1 = "abcdef01-0000-4000-8000-000000000001"
        const val ID_2 = "abcdef01-0000-4000-8000-000000000002"
        const val DOCUMENT_ID = "abcdef01-0000-4000-8000-000000000011"
    }
}
