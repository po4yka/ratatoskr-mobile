package com.ratatoskr.mobile.queue

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CaptureQueueRetryTest {
    @Test
    fun retry_outcome_persists_attempt_and_next_eligible_time() =
        runTest {
            val clock = MutableQueueClock()
            val queue = testQueue(clock = clock)
            val stored = queue.enqueue(captureRequest()).success()
            val claim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))

            val retry = queue.recordFailure(stored.localId, claim.token, QueueFailure.Connectivity).success()

            assertEquals(1, retry.attemptCount)
            assertEquals(QueueState.RetryWait, retry.state)
            assertEquals(NOW + 15.seconds, retry.nextEligibleAt)
            assertEquals(retry, queue.inspect(stored.localId))
        }

    @Test
    fun server_hint_controls_dequeue_eligibility() =
        runTest {
            val clock = MutableQueueClock()
            val queue = testQueue(clock = clock)
            val stored = queue.enqueue(captureRequest()).success()
            val claim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            queue
                .recordFailure(
                    stored.localId,
                    claim.token,
                    QueueFailure.RateLimited,
                    serverRetryAt = NOW + 5.minutes,
                ).success()

            clock.current = NOW + 4.minutes
            assertNull(queue.claimReady(OWNER_A, 1.minutes))
            clock.current = NOW + 5.minutes
            assertEquals(stored.localId, assertNotNull(queue.claimReady(OWNER_A, 1.minutes)).record.localId)
        }

    @Test
    fun retry_exhaustion_stops_automatic_work() =
        runTest {
            val clock = MutableQueueClock()
            val queue = testQueue(clock = clock, limits = QueueLimits(maxAttempts = 2))
            val stored = queue.enqueue(captureRequest()).success()
            val first = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            val waiting = queue.recordFailure(stored.localId, first.token, QueueFailure.ServerUnavailable).success()
            clock.current = waiting.nextEligibleAt
            val second = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))

            val exhausted = queue.recordFailure(stored.localId, second.token, QueueFailure.ServerUnavailable).success()

            assertEquals(2, exhausted.attemptCount)
            assertEquals(QueueState.PermanentFailure, exhausted.state)
            assertNull(queue.claimReady(OWNER_A, 1.minutes))
        }

    @Test
    fun permanent_failure_is_never_dequeued() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(captureRequest()).success()
            val claim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))

            val failed = queue.recordFailure(stored.localId, claim.token, QueueFailure.Policy).success()

            assertEquals(QueueState.PermanentFailure, failed.state)
            assertNull(queue.claimReady(OWNER_A, 1.minutes))
        }

    @Test
    fun authentication_failure_does_not_consume_retry_budget() =
        runTest {
            val queue = testQueue(limits = QueueLimits(maxAttempts = 1))
            val stored = queue.enqueue(captureRequest()).success()
            val claim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))

            val paused = queue.recordFailure(stored.localId, claim.token, QueueFailure.Authentication).success()

            assertEquals(QueueState.AuthRequired, paused.state)
            assertEquals(0, paused.attemptCount)
        }
}
