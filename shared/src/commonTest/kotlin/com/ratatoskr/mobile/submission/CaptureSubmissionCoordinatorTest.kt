package com.ratatoskr.mobile.submission

import com.ratatoskr.mobile.identity.Authorization
import com.ratatoskr.mobile.queue.MutableQueueClock
import com.ratatoskr.mobile.queue.OWNER_A
import com.ratatoskr.mobile.queue.QueueState
import com.ratatoskr.mobile.queue.captureRequest
import com.ratatoskr.mobile.queue.success
import com.ratatoskr.mobile.queue.testQueue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class CaptureSubmissionCoordinatorTest {
    @Test
    fun accepted_claim_binds_original_operation() =
        runTest {
            val fixture = fixture(listOf(PlatformCaptureResult.Accepted(OPERATION_ID)))
            val stored = fixture.queue.enqueue(captureRequest(owner = OWNER_A), IDEMPOTENCY_KEY).success()

            assertEquals(SubmissionDrainResult.Accepted, fixture.coordinator.drainOne(OWNER_A))
            val accepted = assertNotNull(fixture.queue.inspect(stored.localId))
            assertEquals(QueueState.Accepted, accepted.state)
            assertEquals(OPERATION_ID, accepted.operationId)
        }

    @Test
    fun uncertain_restart_reuses_body_and_key() =
        runTest {
            val clock = MutableQueueClock()
            val fixture =
                fixture(
                    results =
                        listOf(
                            PlatformCaptureResult.Retryable(CaptureTransportFailure.Connectivity),
                            PlatformCaptureResult.Accepted(OPERATION_ID),
                        ),
                    clock = clock,
                )
            fixture.queue.enqueue(captureRequest(owner = OWNER_A), IDEMPOTENCY_KEY).success()

            assertEquals(SubmissionDrainResult.RetryScheduled, fixture.coordinator.drainOne(OWNER_A))
            clock.current += 1.minutes
            assertEquals(SubmissionDrainResult.Accepted, fixture.coordinator.drainOne(OWNER_A))

            assertEquals(listOf(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY), fixture.api.keys)
            assertEquals(listOf(URL, URL), fixture.api.urls)
        }

    @Test
    fun connectivity_persists_retry_time() =
        runTest {
            val fixture = fixture(listOf(PlatformCaptureResult.Retryable(CaptureTransportFailure.Connectivity)))
            val stored = fixture.queue.enqueue(captureRequest(owner = OWNER_A), IDEMPOTENCY_KEY).success()

            assertEquals(SubmissionDrainResult.RetryScheduled, fixture.coordinator.drainOne(OWNER_A))
            val waiting = assertNotNull(fixture.queue.inspect(stored.localId))
            assertEquals(QueueState.RetryWait, waiting.state)
            assertEquals(1, waiting.attemptCount)
        }

    @Test
    fun permanent_failure_stops() =
        runTest {
            val fixture = fixture(listOf(PlatformCaptureResult.Permanent(CaptureTransportFailure.Validation)))
            val stored = fixture.queue.enqueue(captureRequest(owner = OWNER_A), IDEMPOTENCY_KEY).success()

            assertEquals(SubmissionDrainResult.PermanentFailure, fixture.coordinator.drainOne(OWNER_A))
            assertEquals(QueueState.PermanentFailure, fixture.queue.inspect(stored.localId)?.state)
        }

    @Test
    fun revocation_preserves_auth_required_capture() =
        runTest {
            val fixture = fixture(emptyList(), authorized = UnauthorizedExecutor)
            val stored = fixture.queue.enqueue(captureRequest(owner = OWNER_A), IDEMPOTENCY_KEY).success()

            assertEquals(SubmissionDrainResult.AuthRequired, fixture.coordinator.drainOne(OWNER_A))
            val paused = assertNotNull(fixture.queue.inspect(stored.localId))
            assertEquals(QueueState.AuthRequired, paused.state)
            assertEquals(0, paused.attemptCount)
        }

    private fun fixture(
        results: List<PlatformCaptureResult>,
        clock: MutableQueueClock = MutableQueueClock(),
        authorized: AuthorizedRequestExecutor = PassingExecutor,
    ): Fixture {
        val queue = testQueue(clock = clock)
        val api = RecordingCaptureApi(results.toMutableList())
        return Fixture(queue, api, CaptureSubmissionCoordinator(queue, api, authorized))
    }

    private data class Fixture(
        val queue: com.ratatoskr.mobile.queue.CaptureQueue,
        val api: RecordingCaptureApi,
        val coordinator: CaptureSubmissionCoordinator,
    )

    private class RecordingCaptureApi(
        private val results: MutableList<PlatformCaptureResult>,
    ) : PlatformCaptureApi {
        val keys = mutableListOf<String>()
        val urls = mutableListOf<String>()

        override suspend fun submit(
            authorization: Authorization,
            url: String,
            idempotencyKey: String,
        ): PlatformCaptureResult {
            keys += idempotencyKey
            urls += url
            return results.removeAt(0)
        }
    }

    private object PassingExecutor : AuthorizedRequestExecutor {
        override suspend fun <T> execute(request: suspend (Authorization) -> AuthorizedResult<T>): AuthorizedResult<T> =
            request(Authorization(OWNER_A.origin, "access-token"))
    }

    private object UnauthorizedExecutor : AuthorizedRequestExecutor {
        override suspend fun <T> execute(request: suspend (Authorization) -> AuthorizedResult<T>): AuthorizedResult<T> =
            AuthorizedResult.Unauthorized
    }

    private companion object {
        const val URL = "https://example.test/article"
        const val IDEMPOTENCY_KEY = "persisted-key"
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
    }
}
