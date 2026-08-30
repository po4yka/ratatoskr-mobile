package com.ratatoskr.mobile.smoke

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.MainActivity
import com.ratatoskr.mobile.RatatoskrApplication
import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.identity.AndroidKeystoreCredentialStorage
import com.ratatoskr.mobile.identity.DeviceCredentials
import com.ratatoskr.mobile.notification.CompletionNotificationEffectiveState
import com.ratatoskr.mobile.operation.OperationRepositoryResult
import com.ratatoskr.mobile.operation.OperationStatusRepository
import com.ratatoskr.mobile.presentation.MobileLocale
import com.ratatoskr.mobile.presentation.MobileStringKey
import com.ratatoskr.mobile.presentation.MobileStrings
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueJitter
import com.ratatoskr.mobile.queue.QueueKeyGenerator
import com.ratatoskr.mobile.queue.QueueResult
import com.ratatoskr.mobile.queue.createAndroidQueuePersistence
import com.ratatoskr.mobile.share.ShareSubmissionAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidShareSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun share_url_confirms_queues_accepts_and_opens_terminal_status() =
        runBlocking {
            val application = compose.activity.application as RatatoskrApplication
            application.container.sessions.signOut()
            AndroidKeystoreCredentialStorage(application).save(credentials())
            application.container.sessions.restore()
            application.container.shareSubmissionAccess =
                MutableStateFlow(ShareSubmissionAccess.Available)
            application.container.operationRepository = FixtureOperationsRepository
            val intent =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Synthetic article\nhttps://example.test/smoke")
            compose.activityRule.scenario.onActivity {
                it.acceptIntent(intent)
            }

            compose.onNodeWithText("Synthetic article\nhttps://example.test/smoke").assertIsDisplayed()
            compose.onNodeWithText("Confirm capture").performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                compose
                    .onAllNodesWithText("Safely queued. Ratatoskr will submit it when online.")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Safely queued. Ratatoskr will submit it when online.").assertIsDisplayed()

            val owner = CaptureOwner(ORIGIN, ACCOUNT_ID)
            val queued =
                application.container.queue
                    .snapshot()
                    .first {
                        it.request.owner == owner &&
                            it.request.payload
                                .toString()
                                .contains("/smoke")
                    }
            val accepted = application.container.queue.recordAccepted(queued.localId, OPERATION_ID)
            assertTrue(accepted is QueueResult.Success)
            val completed = application.container.queue.applySnapshot(queued.localId, terminalSnapshot())
            assertTrue(completed is QueueResult.Success)

            val reopened = reopenedQueue(application)
            assertEquals(OPERATION_ID, reopened.inspect(queued.localId)?.operationId)
            reopened.close()

            val detailIntent =
                Intent(MainActivity.ACTION_VIEW_OPERATION)
                    .putExtra(MainActivity.EXTRA_OPERATION_ID, OPERATION_ID)
            compose.activityRule.scenario.onActivity { it.acceptIntent(detailIntent) }
            compose.onNodeWithText("Completed").assertIsDisplayed()
            val image = compose.onRoot().captureToImage()
            assertTrue(image.width > 0 && image.height > 0)
            application.container.sessions.signOut()
        }

    @Test
    fun hostile_share_displays_safe_actionable_error() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://one.test https://two.test")

        compose.activityRule.scenario.onActivity { it.acceptIntent(intent) }

        compose
            .onNodeWithText("This share contains more than one URL. Share one URL at a time.")
            .assertIsDisplayed()
    }

    @Test
    fun share_file_stages_private_bytes_and_queues_opaque_reference() =
        runBlocking {
            val application = compose.activity.application as RatatoskrApplication
            application.container.sessions.signOut()
            AndroidKeystoreCredentialStorage(application).save(credentials())
            application.container.sessions.restore()
            application.container.shareSubmissionAccess = MutableStateFlow(ShareSubmissionAccess.Available)
            val intent =
                Intent(Intent.ACTION_SEND)
                    .setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://com.ratatoskr.mobile.test.synthetic-share/synthetic.pdf"))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            compose.activityRule.scenario.onActivity { it.acceptIntent(intent) }
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithText("synthetic.pdf").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("integration pending", substring = true, ignoreCase = true).assertIsDisplayed()
            compose.onNodeWithText("Confirm capture").performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                compose
                    .onAllNodesWithText("Safely queued locally. File submission is integration pending.")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            val owner = CaptureOwner(ORIGIN, ACCOUNT_ID)
            val fileRecord =
                application.container.queue.snapshot().first {
                    it.request.owner == owner && it.request.payload is CapturePayload.FileReference
                }
            val payload = fileRecord.request.payload as CapturePayload.FileReference
            assertEquals("synthetic.pdf", payload.displayName)
            assertTrue(
                application.container.artifactStore
                    .publishedFiles()
                    .any { it.name == payload.stagedFileId },
            )
            application.container.sessions.signOut()
        }

    @Test
    fun operation_polling_stops_when_activity_is_not_resumed() {
        val reads = AtomicInteger()
        val application = compose.activity.application as RatatoskrApplication
        application.container.operationRepository =
            object : OperationStatusRepository {
                override suspend fun list(cursor: String?): OperationRepositoryResult<OperationList> =
                    OperationRepositoryResult.Success(OperationList(emptyList()))

                override suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot> {
                    reads.incrementAndGet()
                    return OperationRepositoryResult.Success(runningSnapshot())
                }
            }
        val detailIntent =
            Intent(MainActivity.ACTION_VIEW_OPERATION)
                .putExtra(MainActivity.EXTRA_OPERATION_ID, OPERATION_ID)
        compose.activityRule.scenario.onActivity { it.acceptIntent(detailIntent) }
        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) { reads.get() > 0 }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val readsWhenStopped = reads.get()
        try {
            Thread.sleep(2_500)
            assertEquals(readsWhenStopped, reads.get())
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun shell_wires_search_https_routes_notification_truth_russian_and_accessible_navigation() =
        runBlocking {
            val application = compose.activity.application as RatatoskrApplication
            application.container.sessions.signOut()
            AndroidKeystoreCredentialStorage(application).save(credentials())
            application.container.sessions.restore()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Library").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNode(hasText("Library") and button()).performClick()
            compose.onNodeWithText("Search library").performClick()
            compose.onNode(hasText("Search") and isHeading()).assertIsDisplayed()

            compose.activityRule.scenario.onActivity {
                assertTrue(
                    it.acceptLibraryLink(
                        "https://links.ratatoskr.test/analyses/abcdef01-0000-4000-8000-000000000001",
                    ),
                )
                assertEquals("abcdef01-0000-4000-8000-000000000001", it.pendingContentRouteId)
                assertTrue(it.acceptLibraryLink("https://links.ratatoskr.test/collections/inbox"))
                assertEquals("inbox", it.pendingContentRouteId)
                assertTrue(it.acceptLibraryLink("https://links.ratatoskr.test/repos/ratatoskr/mobile"))
                assertEquals("ratatoskr/mobile", it.pendingContentRouteId)
            }

            assertEquals(
                CompletionNotificationEffectiveState.IntegrationPending,
                application.container.notificationStore.state.value.effective,
            )
            assertEquals("Поиск", MobileStrings.value(MobileStringKey.SearchTitle, MobileLocale.Russian))
            val publicText =
                MobileStrings.value(MobileStringKey.NotificationsIntegrationPending, MobileLocale.English)
            listOf("private-search", "private-note", "private-user@example.test").forEach {
                assertTrue(it !in publicText)
            }
            application.container.sessions.signOut()
        }

    private fun reopenedQueue(application: RatatoskrApplication) =
        CaptureQueue(
            persistence = createAndroidQueuePersistence(application),
            clock = QueueClock { NOW },
            keyGenerator = QueueKeyGenerator { "smoke-reopen-key" },
            jitter = QueueJitter { 0.0 },
        )

    private fun credentials() =
        DeviceCredentials(
            origin = ORIGIN,
            userId = ACCOUNT_ID,
            deviceId = "smoke-device",
            deviceSecret = "synthetic-device-secret",
            accessToken = "synthetic-access-token",
            accessExpiresAt = "2026-08-29T01:00:00Z",
            refreshToken = "synthetic-refresh-token",
            refreshExpiresAt = "2026-08-30T00:00:00Z",
        )

    private fun button() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun terminalSnapshot() =
        OperationSnapshot(
            acceptedAt = NOW,
            correlationId = "operation:$OPERATION_ID",
            kind = "capture",
            operationId = OPERATION_ID,
            retryable = false,
            status = OperationStatus.SUCCEEDED,
            statusChangedAt = NOW,
            progressPercent = 100,
            stage = "done",
            terminatedAt = NOW,
        )

    private fun runningSnapshot() =
        OperationSnapshot(
            acceptedAt = NOW,
            correlationId = "operation:$OPERATION_ID",
            kind = "capture",
            operationId = OPERATION_ID,
            retryable = false,
            status = OperationStatus.RUNNING,
            statusChangedAt = NOW,
            progressPercent = 25,
            stage = "processing",
        )

    private object FixtureOperationsRepository : OperationStatusRepository {
        override suspend fun list(cursor: String?): OperationRepositoryResult<OperationList> =
            OperationRepositoryResult.Success(OperationList(emptyList()))

        override suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot> =
            OperationRepositoryResult.Success(terminalSnapshotStatic())
    }

    companion object {
        private const val ORIGIN = "https://127.0.0.1:1"
        private const val ACCOUNT_ID = "smoke-account"
        private const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000099"
        private val NOW = Instant.parse("2026-08-29T00:00:00Z")

        private fun terminalSnapshotStatic() =
            OperationSnapshot(
                acceptedAt = NOW,
                correlationId = "operation:$OPERATION_ID",
                kind = "capture",
                operationId = OPERATION_ID,
                retryable = false,
                status = OperationStatus.SUCCEEDED,
                statusChangedAt = NOW,
                progressPercent = 100,
                stage = "done",
                terminatedAt = NOW,
            )
    }
}
