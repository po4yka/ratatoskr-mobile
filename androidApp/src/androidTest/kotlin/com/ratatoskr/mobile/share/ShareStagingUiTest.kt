package com.ratatoskr.mobile.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareStagingUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun url_preview_requires_confirm() {
        val actions = mutableListOf<ShareStagingAction>()
        compose.setContent {
            ShareStagingSurface(
                state =
                    ShareStagingState.Ready(
                        originalText = "Article title\nhttps://example.test/article",
                        url = "https://example.test/article",
                        canSubmit = true,
                        message = null,
                    ),
                dispatch = actions::add,
            )
        }

        compose.onNodeWithText("Article title\nhttps://example.test/article").assertIsDisplayed()
        compose.onNodeWithText("Confirm capture").assertIsEnabled().performClick()
        assertEquals(listOf(ShareStagingAction.Confirm), actions)
    }

    @Test
    fun plain_text_explains_unavailable_contract() {
        compose.setContent {
            ShareStagingSurface(
                state =
                    ShareStagingState.Ready(
                        originalText = "Selected words",
                        url = null,
                        canSubmit = false,
                        message = "Platform does not currently accept plain text captures.",
                    ),
                dispatch = {},
            )
        }

        compose.onNodeWithText("Selected words").assertIsDisplayed()
        compose.onNodeWithText("Platform does not currently accept plain text captures.").assertIsDisplayed()
        compose.onNodeWithText("Confirm capture").assertIsNotEnabled()
    }

    @Test
    fun queued_state_is_visible_offline() {
        compose.setContent {
            ShareStagingSurface(
                state = ShareStagingState.Queued("local-1", "Safely queued. Ratatoskr will submit it when online."),
                dispatch = {},
            )
        }

        compose.onNodeWithText("Safely queued. Ratatoskr will submit it when online.").assertIsDisplayed()
        compose.onNodeWithText("local-1", substring = true).assertDoesNotExist()
    }

    @Test
    fun file_preview_shows_upload_impact_and_integration_pending() {
        val file = ShareIntake.File("artifact-1", "synthetic.pdf", "application/pdf", 18, "a".repeat(64))
        compose.setContent {
            ShareStagingSurface(
                ShareStagingState.Ready(
                    originalText = file.displayName,
                    url = null,
                    canSubmit = true,
                    message = "File submission is integration pending; the queued file stays local.",
                    file = file,
                ),
                dispatch = {},
            )
        }

        compose.onNodeWithText("synthetic.pdf").assertIsDisplayed()
        compose.onNodeWithText("18 bytes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("integration pending", substring = true, ignoreCase = true).assertIsDisplayed()
    }
}
