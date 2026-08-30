package com.ratatoskr.mobile.storage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.transfer.FileTransferAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStorageUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun usage_integration_pending_and_clear_confirmation_are_accessible() {
        val usage = StorageUsage(18, 1, 18, 0, 0, 0, 512L * 1024 * 1024, 64)
        var state: LocalStorageState by mutableStateOf(
            LocalStorageState.Ready(usage, FileTransferAvailability.IntegrationPending),
        )
        compose.setContent {
            LocalStorageSurface(
                state = state,
                dispatch = { action ->
                    state =
                        when (action) {
                            LocalStorageAction.RequestClear -> LocalStorageState.ConfirmClear(usage)
                            LocalStorageAction.CancelClear -> LocalStorageState.Ready(usage, FileTransferAvailability.IntegrationPending)
                            LocalStorageAction.ConfirmClear -> LocalStorageState.Empty
                            LocalStorageAction.Cleanup -> state
                        }
                },
                onBack = {},
            )
        }

        compose.onNodeWithText("1 staged items", substring = true).assertIsDisplayed()
        compose.onNodeWithText("integration pending", substring = true, ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear all local Ratatoskr data").performClick()
        compose.onNodeWithText("queued captures, credentials, caches", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("1 staged items", substring = true).assertIsDisplayed()
    }
}
