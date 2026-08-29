package com.ratatoskr.mobile.operation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class OperationStatusUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fixture_list_renders_running_partial_failed_and_completed() {
        compose.setContent {
            OperationListSurface(
                state =
                    OperationListState.Content(
                        listOf(
                            operation("running", OperationStatus.RUNNING).copy(
                                progressPercent = 25,
                                stage = "extracting",
                            ),
                            operation("partial", OperationStatus.PARTIALLY_SUCCEEDED),
                            operation("failed", OperationStatus.FAILED),
                            operation("completed", OperationStatus.SUCCEEDED),
                        ),
                    ),
                onRefresh = {},
                onOpen = {},
            )
        }

        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.onNodeWithText("extracting").assertIsDisplayed()
        compose.onNodeWithText("25%").assertIsDisplayed()
        compose.onAllNodesWithText("2026-08-29T00:00:00Z").assertCountEquals(4)
        compose.onNodeWithText("Partially completed").assertIsDisplayed()
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun offline_list_retains_stale_context() {
        var state: OperationListState by
            mutableStateOf(OperationListState.Content(listOf(operation("stale", OperationStatus.RUNNING))))
        compose.setContent {
            OperationListSurface(state = state, onRefresh = {}, onOpen = {})
        }
        compose.onNodeWithText("capture").assertIsDisplayed()

        compose.runOnIdle { state = OperationListState.Offline }

        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("capture").assertIsDisplayed()
    }

    @Test
    fun detail_renders_progress_counts_and_safe_errors() {
        compose.setContent {
            OperationDetailSurface(
                state =
                    OperationDetailState.Content(
                        operation("detail", OperationStatus.RUNNING).copy(
                            progressPercent = 40,
                            stage = "extracting",
                            warningCount = 1,
                            errorCount = 2,
                            resultCount = 3,
                        ),
                    ),
                onRetry = {},
                onPair = {},
            )
        }

        compose.onNodeWithText("40%").assertIsDisplayed()
        compose.onNodeWithText("extracting").assertIsDisplayed()
        compose.onNodeWithText("1 warning").assertIsDisplayed()
        compose.onNodeWithText("2 errors").assertIsDisplayed()
        compose.onNodeWithText("3 results").assertIsDisplayed()
    }

    @Test
    fun offline_and_reauth_states_are_actionable() {
        var showDetail by mutableStateOf(false)
        compose.setContent {
            if (showDetail) {
                OperationDetailSurface(OperationDetailState.RePairingRequired, onRetry = {}, onPair = {})
            } else {
                OperationListSurface(OperationListState.Offline, onRefresh = {}, onOpen = {})
            }
        }
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        compose.runOnIdle { showDetail = true }
        compose.onNodeWithText("Pair device").assertIsDisplayed()
    }

    private fun operation(
        id: String,
        status: OperationStatus,
    ) = OperationPresentation(
        operationId = id,
        kind = "capture",
        status = status,
        statusChangedAt = Instant.parse("2026-08-29T00:00:00Z"),
        progressPercent = null,
        stage = null,
        warningCount = 0,
        errorCount = 0,
        resultCount = 0,
    )
}
