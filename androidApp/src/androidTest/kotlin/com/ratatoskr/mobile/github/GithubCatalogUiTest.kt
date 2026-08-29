package com.ratatoskr.mobile.github

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GithubCatalogUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fixture_browse_search_and_detail_are_visibly_unsynchronized() {
        var opened: GithubCatalogRow? = null
        var showDetail by mutableStateOf(false)
        compose.setContent {
            if (showDetail) {
                GithubDetailSurface(content(), {}, {}, {}, {})
            } else {
                GithubCatalogSurface(
                    GithubCatalogState.Content("", listOf(ROW)),
                    onSearch = {},
                    onOpen = { opened = it },
                )
            }
        }

        compose.onNodeWithText("Contract fixture browse — not synchronized; resets when Ratatoskr restarts.").assertIsDisplayed()
        compose.onNodeWithText(ROW.fullName).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(ROW, opened)
            showDetail = true
        }
        compose.onNodeWithText("Live Platform preview").assertIsDisplayed()
        compose.onNodeWithText(PREVIEW.target.fullName).assertIsDisplayed()
    }

    @Test
    fun track_and_star_show_distinct_confirmation_effects_before_dispatch() {
        var state: GithubDetailState by mutableStateOf(content(pending(GithubActionMode.Track)))
        var confirmations = 0
        compose.setContent {
            GithubDetailSurface(
                state,
                onSelect = {},
                onConfirm = { confirmations += 1 },
                onCancel = {},
                onRetryUncertain = {},
            )
        }

        compose.onNodeWithText("Ratatoskr desired backup tracking; no completed backup or GitHub write.").assertIsDisplayed()
        compose.onNodeWithText("Confirm tracking").performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }

        compose.runOnIdle { state = content(pending(GithubActionMode.Star)) }
        compose.onNodeWithText("External GitHub star plus metadata and desired backup request.").assertIsDisplayed()
    }

    @Test
    fun partial_fixture_renders_all_component_facts() {
        compose.setContent {
            GithubDetailSurface(
                content(
                    result =
                        GithubActionPresentation(
                            "Partial",
                            "Metadata: Succeeded",
                            "GitHub star: Succeeded",
                            "Desired backup: Failed (DependencyUnavailable)",
                        ),
                ),
                {},
                {},
                {},
                {},
            )
        }

        compose.onNodeWithText("Partial").assertIsDisplayed()
        compose.onNodeWithText("Metadata: Succeeded").assertIsDisplayed()
        compose.onNodeWithText("GitHub star: Succeeded").assertIsDisplayed()
        compose.onNodeWithText("Desired backup: Failed (DependencyUnavailable)").assertIsDisplayed()
    }

    @Test
    fun loading_empty_unavailable_reauth_invalid_and_outcome_unknown_states_are_visible() {
        var catalog: GithubCatalogState by mutableStateOf(GithubCatalogState.Content("none", emptyList()))
        var detail: GithubDetailState? by mutableStateOf(null)
        compose.setContent {
            val current = detail
            if (current == null) {
                GithubCatalogSurface(catalog, {}, {})
            } else {
                GithubDetailSurface(current, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("No fixture repositories match").assertIsDisplayed()
        compose.runOnIdle { catalog = GithubCatalogState.CapabilityUnavailable }
        compose.onNodeWithText("GitHub is unavailable on this Ratatoskr instance").assertIsDisplayed()
        compose.runOnIdle { catalog = GithubCatalogState.PairingRequired }
        compose.onNodeWithText("Pair this device again").assertIsDisplayed()
        compose.runOnIdle { detail = GithubDetailState.Loading }
        compose.onNodeWithText("Loading live repository preview…").assertIsDisplayed()
        compose.runOnIdle { detail = GithubDetailState.Failed(GithubDetailFailure.InvalidResponse, false) }
        compose.onNodeWithText("Platform returned an invalid GitHub response").assertIsDisplayed()
        compose.runOnIdle { detail = content(outcomeUnknown = true) }
        compose.onNodeWithText("Action outcome is unknown; retry uses the same idempotency key.").assertIsDisplayed()
    }

    private fun pending(mode: GithubActionMode) =
        GithubPendingConfirmation(
            mode,
            if (mode == GithubActionMode.Track) "Confirm tracking" else "Confirm GitHub star",
            "fixture disclosure",
            GithubPreviewFingerprint(
                PREVIEW.target,
                PREVIEW.accountRef,
                PREVIEW.availableActions,
                PREVIEW.availableActions,
            ),
        )

    private fun content(
        pending: GithubPendingConfirmation? = null,
        result: GithubActionPresentation? = null,
        outcomeUnknown: Boolean = false,
    ) = GithubDetailState.Content(
        PREVIEW,
        PREVIEW.availableActions,
        pending = pending,
        result = result,
        outcomeUnknown = outcomeUnknown,
        uncertainRetryAvailable = outcomeUnknown,
    )

    private companion object {
        val PREVIEW =
            GithubRepositoryPreview(
                GithubRepositoryTarget(42, "owner/repository", "https://github.com/owner/repository"),
                "Synthetic live preview",
                123,
                "Kotlin",
                "github-account:018f0000-0000-7000-8000-000000000604",
                setOf(GithubActionMode.Metadata, GithubActionMode.Track, GithubActionMode.Star),
            )
        val ROW = GithubCatalogRow("owner/repository", "Synthetic catalog fixture", PREVIEW.target.canonicalUrl)
    }
}
