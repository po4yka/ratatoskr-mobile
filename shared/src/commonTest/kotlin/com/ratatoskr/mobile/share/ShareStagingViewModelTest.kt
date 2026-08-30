package com.ratatoskr.mobile.share

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueJitter
import com.ratatoskr.mobile.queue.QueueKeyGenerator
import com.ratatoskr.mobile.queue.QueuePersistence
import com.ratatoskr.mobile.queue.QueueRecord
import com.ratatoskr.mobile.queue.QueueTransaction
import com.ratatoskr.mobile.submission.SubmissionScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShareStagingViewModelTest {
    @Test
    fun confirm_commits_before_scheduler() =
        runTest {
            val events = mutableListOf<String>()
            val fixture = fixture(events)

            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            val queued = assertIs<ShareStagingState.Queued>(fixture.store.state.value)
            assertEquals(listOf("persist", "schedule"), events)
            assertNotNull(fixture.queue.inspect(queued.localId))
            fixture.store.close()
        }

    @Test
    fun cancel_persists_nothing() =
        runTest {
            val events = mutableListOf<String>()
            val fixture = fixture(events)

            fixture.store.dispatch(ShareStagingAction.Cancel)
            advanceUntilIdle()

            assertIs<ShareStagingState.Cancelled>(fixture.store.state.value)
            assertTrue(events.isEmpty())
            assertTrue(fixture.persistence.snapshot().isEmpty())
            fixture.store.close()
        }

    @Test
    fun offline_confirmation_reports_durable_queue_state() =
        runTest {
            val fixture = fixture(mutableListOf())

            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()
            fixture.store.close()

            val queued = assertIs<ShareStagingState.Queued>(fixture.store.state.value)
            assertTrue(queued.message.contains("queued", ignoreCase = true))
            assertNotNull(fixture.queue.inspect(queued.localId))
            fixture.store.close()
        }

    @Test
    fun unsupported_plain_text_cannot_submit() =
        runTest {
            val events = mutableListOf<String>()
            val fixture =
                fixture(
                    events = events,
                    intake = ShareIntake.UnsupportedText("selected words"),
                )

            val ready = assertIs<ShareStagingState.Ready>(fixture.store.state.value)
            assertFalse(ready.canSubmit)
            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            assertEquals(ready, fixture.store.state.value)
            assertTrue(events.isEmpty())
            fixture.store.close()
        }

    @Test
    fun repeat_confirm_does_not_duplicate() =
        runTest {
            val fixture = fixture(mutableListOf())

            fixture.store.dispatch(ShareStagingAction.Confirm)
            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            assertIs<ShareStagingState.Queued>(fixture.store.state.value)
            assertEquals(1, fixture.persistence.snapshot().size)
            fixture.store.close()
        }

    @Test
    fun submit_capability_controls_confirmation_and_recovers() =
        runTest {
            val access = MutableStateFlow(ShareSubmissionAccess.CapabilityUnavailable)
            val fixture = fixture(mutableListOf(), submissionAccess = access)

            val unavailable = assertIs<ShareStagingState.Ready>(fixture.store.state.value)
            assertFalse(unavailable.canSubmit)
            assertTrue(unavailable.message.orEmpty().contains("unavailable", ignoreCase = true))

            access.value = ShareSubmissionAccess.Available
            runCurrent()

            assertTrue(assertIs<ShareStagingState.Ready>(fixture.store.state.value).canSubmit)
            fixture.store.close()
        }

    @Test
    fun ios_confirmation_uses_handoff_identity_and_source() =
        runTest {
            var committed: QueueRecord? = null
            val fixture =
                fixture(
                    events = mutableListOf(),
                    captureSource = CaptureSource.IosShareExtension,
                    captureCreatedAt = IOS_CAPTURED_AT,
                    idempotencyKey = "ios-share-handoff-1",
                    onCommitted = { committed = it },
                )

            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            val record = assertNotNull(committed)
            assertEquals(CaptureSource.IosShareExtension, record.request.source)
            assertEquals(IOS_CAPTURED_AT, record.request.createdAt)
            assertEquals("ios-share-handoff-1", record.idempotencyKey)
            fixture.store.close()
        }

    @Test
    fun ios_restart_converges_on_existing_queue_record() =
        runTest {
            val events = mutableListOf<String>()
            val fixture =
                fixture(
                    events = events,
                    captureSource = CaptureSource.IosShareExtension,
                    captureCreatedAt = IOS_CAPTURED_AT,
                    idempotencyKey = "ios-share-handoff-2",
                )
            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()
            fixture.store.close()

            val restarted =
                ShareStagingStore(
                    initialIntake = ShareIntake.Url("https://example.test/a", "https://example.test/a"),
                    owner = CurrentCaptureOwner { OWNER },
                    queue = fixture.queue,
                    scheduler = SubmissionScheduler { events += "schedule" },
                    clock = QueueClock { NOW },
                    scope = this,
                    captureSource = CaptureSource.IosShareExtension,
                    captureCreatedAt = IOS_CAPTURED_AT,
                    idempotencyKey = "ios-share-handoff-2",
                )
            restarted.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            assertIs<ShareStagingState.Queued>(restarted.state.value)
            assertEquals(1, fixture.persistence.snapshot().size)
            restarted.close()
        }

    @Test
    fun ios_id_reuse_with_changed_payload_fails_closed() =
        runTest {
            val fixture =
                fixture(
                    events = mutableListOf(),
                    captureSource = CaptureSource.IosShareExtension,
                    captureCreatedAt = IOS_CAPTURED_AT,
                    idempotencyKey = "ios-share-handoff-3",
                )
            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()
            fixture.store.close()

            var failures = 0
            val conflicting =
                ShareStagingStore(
                    initialIntake = ShareIntake.Url("https://other.test/b", "https://other.test/b"),
                    owner = CurrentCaptureOwner { OWNER },
                    queue = fixture.queue,
                    scheduler = SubmissionScheduler {},
                    clock = QueueClock { NOW },
                    scope = this,
                    captureSource = CaptureSource.IosShareExtension,
                    captureCreatedAt = IOS_CAPTURED_AT,
                    idempotencyKey = "ios-share-handoff-3",
                    onFailure = { failures += 1 },
                )
            conflicting.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            assertIs<ShareStagingState.Failed>(conflicting.state.value)
            assertEquals(1, failures)
            assertEquals(1, fixture.persistence.snapshot().size)
            conflicting.close()
        }

    @Test
    fun cancel_invokes_cleanup_without_queueing() =
        runTest {
            var cancellations = 0
            val fixture = fixture(mutableListOf(), onCancelled = { cancellations += 1 })

            fixture.store.dispatch(ShareStagingAction.Cancel)
            fixture.store.dispatch(ShareStagingAction.Cancel)
            advanceUntilIdle()
            fixture.store.close()

            assertEquals(1, cancellations)
            assertTrue(fixture.persistence.snapshot().isEmpty())
            fixture.store.close()
        }

    @Test
    fun queue_failure_retains_handoff() =
        runTest {
            var failures = 0
            val persistence = RecordingPersistence(mutableListOf())
            val queue =
                CaptureQueue(
                    persistence = persistence,
                    clock = QueueClock { NOW },
                    keyGenerator = QueueKeyGenerator { "key" },
                    jitter = QueueJitter { 0.0 },
                )
            val store =
                ShareStagingStore(
                    initialIntake = ShareIntake.Url("https://example.test/a", "https://example.test/a"),
                    owner = CurrentCaptureOwner { null },
                    queue = queue,
                    scheduler = SubmissionScheduler {},
                    clock = QueueClock { NOW },
                    scope = this,
                    captureSource = CaptureSource.IosShareExtension,
                    idempotencyKey = "ios-share-handoff-4",
                    onFailure = { failures += 1 },
                )

            store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()

            assertIs<ShareStagingState.Failed>(store.state.value)
            assertEquals(1, failures)
            assertTrue(persistence.snapshot().isEmpty())
            store.close()
        }

    @Test
    fun confirmed_file_enqueues_before_schedule() =
        runTest {
            val events = mutableListOf<String>()
            val fixture = fixture(events, intake = fileIntake())

            fixture.store.dispatch(ShareStagingAction.Confirm)
            advanceUntilIdle()
            fixture.store.close()

            val queued = assertIs<ShareStagingState.Queued>(fixture.store.state.value)
            val record = assertNotNull(fixture.queue.inspect(queued.localId))
            assertEquals(
                CapturePayload.FileReference("artifact-1", "synthetic.pdf", "application/pdf", 18),
                record.request.payload,
            )
            assertEquals(listOf("persist"), events)
        }

    @Test
    fun cancelled_file_removes_only_unreferenced_draft() =
        runTest {
            var cleanupCalls = 0
            val fixture = fixture(mutableListOf(), intake = fileIntake(), onCancelled = { cleanupCalls += 1 })

            fixture.store.dispatch(ShareStagingAction.Cancel)
            advanceUntilIdle()
            fixture.store.close()

            assertEquals(1, cleanupCalls)
            assertTrue(fixture.persistence.snapshot().isEmpty())
        }

    private fun fileIntake() =
        ShareIntake.File(
            stagedFileId = "artifact-1",
            displayName = "synthetic.pdf",
            mediaType = "application/pdf",
            byteSize = 18,
            sha256Hex = "a".repeat(64),
        )

    private fun kotlinx.coroutines.test.TestScope.fixture(
        events: MutableList<String>,
        intake: ShareIntake = ShareIntake.Url("Shared title\nhttps://example.test/a", "https://example.test/a"),
        submissionAccess: MutableStateFlow<ShareSubmissionAccess> =
            MutableStateFlow(ShareSubmissionAccess.Available),
        captureSource: CaptureSource = CaptureSource.AndroidShareTarget,
        captureCreatedAt: Instant? = null,
        idempotencyKey: String? = null,
        onCommitted: (QueueRecord) -> Unit = {},
        onCancelled: () -> Unit = {},
    ): Fixture {
        val persistence = RecordingPersistence(events)
        var key = 0
        val queue =
            CaptureQueue(
                persistence = persistence,
                clock = QueueClock { NOW },
                keyGenerator = QueueKeyGenerator { "key-${++key}" },
                jitter = QueueJitter { 0.0 },
            )
        val store =
            ShareStagingStore(
                initialIntake = intake,
                owner = CurrentCaptureOwner { OWNER },
                queue = queue,
                scheduler = SubmissionScheduler { events += "schedule" },
                clock = QueueClock { NOW },
                scope = this,
                submissionAccess = submissionAccess,
                captureSource = captureSource,
                captureCreatedAt = captureCreatedAt,
                idempotencyKey = idempotencyKey,
                onCommitted = onCommitted,
                onCancelled = onCancelled,
            )
        return Fixture(store, queue, persistence)
    }

    private data class Fixture(
        val store: ShareStagingStore,
        val queue: CaptureQueue,
        val persistence: RecordingPersistence,
    )

    private class RecordingPersistence(
        private val events: MutableList<String>,
    ) : QueuePersistence {
        private val records = mutableListOf<QueueRecord>()

        override suspend fun <T> transaction(block: suspend QueueTransaction.() -> T): T =
            block(
                object : QueueTransaction {
                    override suspend fun records(): List<QueueRecord> = records.toList()

                    override suspend fun insert(record: QueueRecord) {
                        records += record
                        events += "persist"
                    }

                    override suspend fun update(record: QueueRecord) {
                        val index = records.indexOfFirst { it.localId == record.localId }
                        records[index] = record
                    }
                },
            )

        fun snapshot(): List<QueueRecord> = records.toList()

        override fun close() = Unit
    }

    private companion object {
        val NOW = Instant.parse("2026-08-29T00:00:00Z")
        val IOS_CAPTURED_AT = Instant.parse("2026-08-29T00:05:00Z")
        val OWNER = CaptureOwner("https://platform.example.test", "account-1")
    }
}
