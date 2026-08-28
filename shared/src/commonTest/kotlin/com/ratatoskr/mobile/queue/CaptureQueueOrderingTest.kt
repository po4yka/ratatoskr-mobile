package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class CaptureQueueOrderingTest {
    @Test
    fun dequeue_is_fifo_within_source() =
        runTest {
            val queue = testQueue()
            val first = queue.enqueue(captureRequest(payload = CapturePayload.TextNote("first"))).success()
            val second = queue.enqueue(captureRequest(payload = CapturePayload.TextNote("second"))).success()
            val third = queue.enqueue(captureRequest(payload = CapturePayload.TextNote("third"))).success()

            val firstClaim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            assertEquals(first.localId, firstClaim.record.localId)
            assertNull(queue.claimReady(OWNER_A, 1.minutes))
            queue.recordFailure(first.localId, firstClaim.token, QueueFailure.Validation).success()

            val secondClaim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            assertEquals(second.localId, secondClaim.record.localId)
            queue.recordFailure(second.localId, secondClaim.token, QueueFailure.Validation).success()

            assertEquals(third.localId, assertNotNull(queue.claimReady(OWNER_A, 1.minutes)).record.localId)
        }

    @Test
    fun waiting_source_does_not_block_other_source() =
        runTest {
            val clock = MutableQueueClock()
            val queue = testQueue(clock = clock)
            val first = queue.enqueue(captureRequest(source = CaptureSource.MainApp)).success()
            val other =
                queue
                    .enqueue(
                        captureRequest(
                            source = CaptureSource.AndroidShareTarget,
                            payload = CapturePayload.TextNote("other lane"),
                        ),
                    ).success()
            val firstClaim = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            assertEquals(first.localId, firstClaim.record.localId)
            queue.recordFailure(first.localId, firstClaim.token, QueueFailure.Connectivity).success()

            assertEquals(other.localId, assertNotNull(queue.claimReady(OWNER_A, 1.minutes)).record.localId)
        }

    @Test
    fun expired_claim_reopens_after_restart() =
        runTest {
            val persistence = MemoryQueuePersistence()
            val clock = MutableQueueClock()
            val firstQueue = testQueue(persistence = persistence, clock = clock)
            val stored = firstQueue.enqueue(captureRequest()).success()
            val abandoned = assertNotNull(firstQueue.claimReady(OWNER_A, 1.minutes))
            firstQueue.close()

            clock.current += 2.minutes
            val reopened =
                testQueue(
                    persistence = persistence,
                    clock = clock,
                    keyGenerator = SequenceQueueKeyGenerator(100),
                )
            val reclaimed = assertNotNull(reopened.claimReady(OWNER_A, 1.minutes))

            assertEquals(stored.localId, reclaimed.record.localId)
            assertEquals(stored.idempotencyKey, reclaimed.record.idempotencyKey)
            assertNotEquals(abandoned.token, reclaimed.token)
        }

    @Test
    fun stale_claim_token_cannot_complete_new_attempt() =
        runTest {
            val clock = MutableQueueClock()
            val queue = testQueue(clock = clock)
            val stored = queue.enqueue(captureRequest()).success()
            val abandoned = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))
            clock.current += 2.minutes
            val reclaimed = assertNotNull(queue.claimReady(OWNER_A, 1.minutes))

            val rejection =
                queue
                    .recordFailure(
                        stored.localId,
                        abandoned.token,
                        QueueFailure.Connectivity,
                    ).failure()

            assertEquals(QueueRejection.StaleClaim, rejection)
            assertEquals(reclaimed.token, queue.inspect(stored.localId)?.claimToken)
        }
}
