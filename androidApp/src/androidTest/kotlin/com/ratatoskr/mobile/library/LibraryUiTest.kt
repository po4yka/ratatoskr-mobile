package com.ratatoskr.mobile.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.api.generated.model.ReadState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renders_recent_read_state_and_dispatches_one_replacement() {
        var replacement: Pair<String, ReadState>? = null
        compose.setContent {
            LibraryListSurface(
                state =
                    LibraryListState.Content(
                        items =
                            listOf(
                                LibraryItemPresentation(ID, "document", "Recent analysis", ReadState.UNREAD, "Evidence"),
                            ),
                        canReplaceReadState = true,
                    ),
                onRefresh = {},
                onReplaceReadState = { id, value -> replacement = id to value },
                onOpen = {},
            )
        }

        compose.onNodeWithText("Recent analysis").assertIsDisplayed()
        compose.onNodeWithText("Unread").assertIsDisplayed()
        compose.onNodeWithText("Mark read").performClick()
        compose.runOnIdle { assertEquals(ID to ReadState.READ, replacement) }
    }

    @Test
    fun fixture_preview_labels_unsynchronized_favorite_note_and_memberships() {
        val repository = ContractFixtureUserContentRepository()
        var favorites = 0
        var notes = 0
        var collections = 0
        var tags = 0
        compose.setContent {
            FixtureLibrarySurface(
                catalog = repository.state.value,
                onToggleFavorite = { favorites += 1 },
                onSaveNote = { _, _ -> notes += 1 },
                onCollectionMembership = { _, _, _ -> collections += 1 },
                onTagMembership = { _, _, _ -> tags += 1 },
                onOpen = {},
            )
        }

        compose
            .onNodeWithText("Contract fixture preview — not synchronized; resets when Ratatoskr restarts.")
            .assertIsDisplayed()
        compose
            .onAllNodesWithText("Favorite", useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
        compose
            .onAllNodesWithText("Save note", useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
        compose
            .onAllNodesWithText("Add to Research", useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
        compose
            .onAllNodesWithText("Add tag: Provenance", useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            assertEquals(1, favorites)
            assertEquals(1, notes)
            assertEquals(1, collections)
            assertEquals(1, tags)
        }
    }

    @Test
    fun reader_renders_provenance_warnings_and_hostile_text_inertly() {
        val item = ContractFixtureUserContentRepository().state.value.item(FixtureIds.ARTICLE)!!
        compose.setContent {
            LibraryReaderSurface(LibraryReaderState.Content(ReaderContentPresentation(item)))
        }

        compose.onNodeWithText("Source: https://example.test/articles/evidence").assertIsDisplayed()
        compose.onNodeWithText("Read state: Unread").assertIsDisplayed()
        compose.onNodeWithText("Favorite: No").assertIsDisplayed()
        compose.onNodeWithText("Tags: contracts, provenance").assertIsDisplayed()
        compose.onNodeWithText("Synthetic extraction omitted one decorative block.").assertIsDisplayed()
        compose.onNodeWithText("<script>alert('inert')</script> remains plain text in Ratatoskr.").assertIsDisplayed()
    }

    @Test
    fun loading_empty_offline_reauth_and_integration_pending_states_are_visible() {
        var state: LibraryListState by mutableStateOf(LibraryListState.Loading)
        var reader: LibraryReaderState? by mutableStateOf(null)
        compose.setContent {
            val readerState = reader
            if (readerState == null) {
                LibraryListSurface(state, {}, { _, _ -> }, {})
            } else {
                LibraryReaderSurface(readerState)
            }
        }
        compose.onNodeWithText("Loading library…").assertIsDisplayed()
        compose.runOnIdle { state = LibraryListState.Empty }
        compose.onNodeWithText("No analyses yet").assertIsDisplayed()
        compose.runOnIdle { state = LibraryListState.Offline }
        compose.onNodeWithText("Library is offline").assertIsDisplayed()
        compose.runOnIdle { state = LibraryListState.RePairingRequired }
        compose.onNodeWithText("Pair this device again").assertIsDisplayed()

        val live = LibraryItemPresentation(ID, "document", "Summary only", ReadState.UNREAD, null)
        compose.runOnIdle { reader = LibraryReaderState.IntegrationPending(live) }
        compose.onNodeWithText("Full reader contract is integration pending").assertIsDisplayed()
    }

    private companion object {
        const val ID = "abcdef01-0000-4000-8000-000000000001"
    }
}
