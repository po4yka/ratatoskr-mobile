package com.ratatoskr.mobile.accessibility

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.github.GithubActionMode
import com.ratatoskr.mobile.github.GithubDetailState
import com.ratatoskr.mobile.github.GithubDetailSurface
import com.ratatoskr.mobile.github.GithubPendingConfirmation
import com.ratatoskr.mobile.github.GithubPreviewFingerprint
import com.ratatoskr.mobile.github.GithubRepositoryPreview
import com.ratatoskr.mobile.github.GithubRepositoryTarget
import com.ratatoskr.mobile.library.LibraryItemPresentation
import com.ratatoskr.mobile.library.LibraryListState
import com.ratatoskr.mobile.library.LibraryListSurface
import com.ratatoskr.mobile.library.LibrarySearchState
import com.ratatoskr.mobile.library.LibrarySearchSurface
import com.ratatoskr.mobile.notification.CompletionNotificationEffectiveState
import com.ratatoskr.mobile.notification.CompletionNotificationState
import com.ratatoskr.mobile.notification.CompletionSubscriptionAvailability
import com.ratatoskr.mobile.notification.NativeNotificationPermissionState
import com.ratatoskr.mobile.notification.NotificationSettingsSurface
import com.ratatoskr.mobile.operation.OperationDetailState
import com.ratatoskr.mobile.operation.OperationDetailSurface
import com.ratatoskr.mobile.operation.OperationPresentation
import com.ratatoskr.mobile.presentation.LocalMobileLocale
import com.ratatoskr.mobile.presentation.MobileLocale
import com.ratatoskr.mobile.storage.LocalStorageState
import com.ratatoskr.mobile.storage.LocalStorageSurface
import com.ratatoskr.mobile.storage.StorageUsage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class AccessibilityUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun search_and_notification_have_headings_named_input_live_status_and_48dp_actions() {
        var showNotifications by mutableStateOf(false)
        compose.setContent {
            if (showNotifications) {
                NotificationSettingsSurface(
                    state =
                        CompletionNotificationState(
                            paired = true,
                            availability = CompletionSubscriptionAvailability.IntegrationPending,
                            permission = NativeNotificationPermissionState.NotDetermined,
                            enabledByUser = false,
                            subscriptionHandlePresent = false,
                            effective = CompletionNotificationEffectiveState.IntegrationPending,
                        ),
                    onEnable = {},
                    onDisable = {},
                    onBack = {},
                )
            } else {
                LibrarySearchSurface(
                    state = LibrarySearchState.Offline("evidence"),
                    onQueryChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onLoadMore = {},
                    onOpen = {},
                )
            }
        }

        compose.onNode(hasText("Search") and isHeading()).assertExists()
        compose.onNode(hasContentDescription("Search the Ratatoskr library")).assertExists()
        compose.onNode(hasStateDescription("Search is offline")).assert(liveRegion())
        compose.onNode(hasText("Retry search") and button()).assertHeightIsAtLeast(48.dp)
        val inputTop =
            compose
                .onNode(hasContentDescription("Search the Ratatoskr library"))
                .fetchSemanticsNode()
                .boundsInRoot.top
        val retryTop =
            compose
                .onNode(hasText("Retry search") and button())
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue(inputTop < retryTop)

        compose.runOnIdle { showNotifications = true }
        compose.onNode(hasText("Operation notifications") and isHeading()).assertExists()
        compose
            .onNode(
                hasStateDescription(
                    "Server completion notifications are integration pending. No permission or push token is requested.",
                ),
            ).assert(liveRegion())
        compose.onNode(hasText("Back") and button()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun russian_labels_remain_visible_at_double_font_scale() {
        var state: LibrarySearchState by mutableStateOf(LibrarySearchState.Offline("доказательства"))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalMobileLocale provides MobileLocale.Russian,
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                LibrarySearchSurface(
                    state = state,
                    onQueryChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onLoadMore = {},
                    onOpen = {},
                )
            }
        }

        compose.onNode(hasText("Поиск") and isHeading()).assertExists()
        compose.onNode(hasStateDescription("Поиск недоступен без сети")).assertExists()
        compose.onNode(hasText("Повторить поиск") and button()).assertHeightIsAtLeast(48.dp)
        compose.runOnIdle {
            state =
                LibrarySearchState.Content(
                    query = "доказательства",
                    items =
                        listOf(
                            LibraryItemPresentation(
                                analysisId = "abcdef01-0000-4000-8000-000000000001",
                                documentId = "abcdef01-0000-4000-8000-000000000002",
                                title = "Результат",
                                readState = ReadState.UNREAD,
                                snippet = null,
                                score = 0.9f,
                            ),
                        ),
                    hasMore = false,
                )
        }
        compose.onNode(hasText("Непрочитано")).assertExists()
        compose.onNode(hasText("Релевантность: 0.9")).assertExists()
        compose.onNode(hasContentDescription("Результат, Непрочитано")).assertExists()
    }

    @Test
    fun library_read_state_operation_partial_and_error_states_are_exposed() {
        var showOperation by mutableStateOf(false)
        compose.setContent {
            if (showOperation) {
                OperationDetailSurface(
                    state =
                        OperationDetailState.Content(
                            OperationPresentation(
                                operationId = "abcdef01-0000-4000-8000-000000000003",
                                kind = "capture",
                                status = OperationStatus.PARTIALLY_SUCCEEDED,
                                statusChangedAt = Instant.parse("2026-08-30T00:00:00Z"),
                                progressPercent = 100,
                                stage = "complete",
                                warningCount = 1,
                                errorCount = 1,
                                resultCount = 2,
                            ),
                        ),
                    onRetry = {},
                    onPair = {},
                )
            } else {
                LibraryListSurface(
                    state =
                        LibraryListState.Content(
                            items =
                                listOf(
                                    LibraryItemPresentation(
                                        analysisId = "abcdef01-0000-4000-8000-000000000001",
                                        documentId = "abcdef01-0000-4000-8000-000000000002",
                                        title = "Accessible analysis",
                                        readState = ReadState.UNREAD,
                                        snippet = null,
                                    ),
                                ),
                            canReplaceReadState = true,
                        ),
                    onRefresh = {},
                    onReplaceReadState = { _, _ -> },
                    onOpen = {},
                )
            }
        }
        compose.onNode(hasText("Library") and isHeading()).assertExists()
        compose.onNode(hasText("Mark read") and button()).assertHeightIsAtLeast(48.dp)

        compose.runOnIdle { showOperation = true }
        compose.onNode(hasText("Operation status") and isHeading()).assertExists()
        compose.onNode(hasStateDescription("Partially completed")).assert(liveRegion())
        compose.onNode(hasStateDescription("1 error")).assertExists()
    }

    @Test
    fun github_confirmation_and_local_clear_have_named_roles_and_safe_order() {
        var showStorage by mutableStateOf(false)
        val preview =
            GithubRepositoryPreview(
                target = GithubRepositoryTarget(42, "owner/repository", "https://github.com/owner/repository"),
                description = "Synthetic",
                stargazerCount = 1,
                primaryLanguage = "Kotlin",
                accountRef = null,
                availableActions = setOf(GithubActionMode.Track),
            )
        val pending =
            GithubPendingConfirmation(
                mode = GithubActionMode.Track,
                title = "Confirm tracking",
                disclosure = "Only Ratatoskr desired backup state will change.",
                fingerprint = GithubPreviewFingerprint(preview.target, null, preview.availableActions, preview.availableActions),
            )
        compose.setContent {
            if (showStorage) {
                LocalStorageSurface(
                    state = LocalStorageState.ConfirmClear(StorageUsage(16, 1, 16, 0, 0, 0, 1024, 8)),
                    dispatch = {},
                    onBack = {},
                )
            } else {
                GithubDetailSurface(
                    state = GithubDetailState.Content(preview, preview.availableActions, pending = pending),
                    onSelect = {},
                    onConfirm = {},
                    onCancel = {},
                    onRetryUncertain = {},
                )
            }
        }
        compose.onNode(hasText("Live Platform preview") and isHeading()).assertExists()
        compose.onNode(hasText("Confirm tracking") and button()).assertHeightIsAtLeast(48.dp)

        compose.runOnIdle { showStorage = true }
        compose.onNode(hasText("Local storage") and isHeading()).assertExists()
        compose.onNode(hasText("Erase all local data") and button()).assertHeightIsAtLeast(48.dp)
        compose.onNode(hasText("Cancel") and button()).assertHeightIsAtLeast(48.dp)
    }

    private fun liveRegion() = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun button() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
}
