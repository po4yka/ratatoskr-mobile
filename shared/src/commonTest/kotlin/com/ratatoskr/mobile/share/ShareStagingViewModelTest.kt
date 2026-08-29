package com.ratatoskr.mobile.share

import com.ratatoskr.mobile.capture.CaptureOwner
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

    private fun kotlinx.coroutines.test.TestScope.fixture(
        events: MutableList<String>,
        intake: ShareIntake = ShareIntake.Url("Shared title\nhttps://example.test/a", "https://example.test/a"),
        submissionAccess: MutableStateFlow<ShareSubmissionAccess> =
            MutableStateFlow(ShareSubmissionAccess.Available),
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
        val OWNER = CaptureOwner("https://platform.example.test", "account-1")
    }
}
