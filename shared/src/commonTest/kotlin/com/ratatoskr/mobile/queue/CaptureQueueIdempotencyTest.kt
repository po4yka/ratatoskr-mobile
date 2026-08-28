package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.capture.CapturePayload
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CaptureQueueIdempotencyTest {
    @Test
    fun matching_idempotency_key_returns_existing_record() =
        runTest {
            val queue = testQueue()
            val first = queue.enqueue(captureRequest(), "idem-1").success()

            val repeated = queue.enqueue(captureRequest(), "idem-1").success()

            assertEquals(first, repeated)
        }

    @Test
    fun different_payload_for_existing_key_is_refused() =
        runTest {
            val queue = testQueue()
            val first = queue.enqueue(captureRequest(), "idem-1").success()

            val rejection =
                queue
                    .enqueue(
                        captureRequest(payload = CapturePayload.TextNote("different")),
                        "idem-1",
                    ).failure()

            assertEquals(QueueRejection.IdempotencyConflict, rejection)
            assertEquals(first, queue.inspect(first.localId))
        }

    @Test
    fun full_queue_keeps_existing_records() =
        runTest {
            val queue = testQueue(limits = QueueLimits(maxUnfinishedRecords = 1))
            val first = queue.enqueue(captureRequest(), "idem-1").success()

            val rejection =
                queue
                    .enqueue(
                        captureRequest(payload = CapturePayload.TextNote("second")),
                        "idem-2",
                    ).failure()

            assertEquals(QueueRejection.CapacityExceeded, rejection)
            assertEquals(first, queue.inspect(first.localId))
        }

    @Test
    fun ready_work_is_owner_scoped() =
        runTest {
            val queue = testQueue()
            queue.enqueue(captureRequest(owner = OWNER_A), "idem-a").success()
            val ownerB = queue.enqueue(captureRequest(owner = OWNER_B), "idem-b").success()

            val claim = assertNotNull(queue.claimReady(OWNER_B, kotlin.time.Duration.parse("1m")))

            assertEquals(ownerB.localId, claim.record.localId)
            assertEquals(OWNER_B, claim.record.request.owner)
        }
}
