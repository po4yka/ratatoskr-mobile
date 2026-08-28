package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CaptureQueueResolutionTest {
    @Test
    fun existing_server_operation_converges_after_uncertain_response() =
        runTest {
            val persistence = MemoryQueuePersistence()
            val first = testQueue(persistence = persistence)
            val stored = first.enqueue(captureRequest(), "idem-1").success()
            first.close()
            val reopened = testQueue(persistence = persistence, keyGenerator = SequenceQueueKeyGenerator(100))

            val accepted = reopened.recordAccepted(stored.localId, OPERATION_A).success()

            assertEquals(QueueState.Accepted, accepted.state)
            assertEquals(OPERATION_A, accepted.operationId)
            assertEquals("idem-1", accepted.idempotencyKey)
        }

    @Test
    fun repeated_identical_acceptance_is_idempotent() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(captureRequest()).success()
            val accepted = queue.recordAccepted(stored.localId, OPERATION_A).success()

            val repeated = queue.recordAccepted(stored.localId, OPERATION_A).success()

            assertEquals(accepted, repeated)
        }

    @Test
    fun different_operation_for_same_key_fails_closed() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(captureRequest()).success()
            queue.recordAccepted(stored.localId, OPERATION_A).success()

            val conflict = queue.recordAccepted(stored.localId, OPERATION_B).success()

            assertEquals(QueueState.ResolutionConflict, conflict.state)
            assertEquals(OPERATION_A, conflict.operationId)
            assertEquals(OPERATION_B, conflict.conflictingOperationId)
            assertEquals(null, queue.claimReady(OWNER_A, kotlin.time.Duration.parse("1m")))
        }

    @Test
    fun bound_operation_snapshot_updates_persisted_projection() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(captureRequest()).success()
            queue.recordAccepted(stored.localId, OPERATION_A).success()
            val snapshot =
                OperationSnapshot(
                    acceptedAt = NOW,
                    correlationId = "operation:$OPERATION_A",
                    kind = "capture",
                    operationId = OPERATION_A,
                    retryable = false,
                    status = OperationStatus.SUCCEEDED,
                    statusChangedAt = NOW + 1.seconds,
                    terminatedAt = NOW + 1.seconds,
                )

            val completed = queue.applySnapshot(stored.localId, snapshot).success()

            assertEquals(QueueState.Completed, completed.state)
            assertEquals(OperationStatus.SUCCEEDED, completed.projection?.status)
            assertEquals(completed, queue.inspect(stored.localId))
        }

    private companion object {
        const val OPERATION_A = "018f0000-0000-7000-8000-000000000001"
        const val OPERATION_B = "018f0000-0000-7000-8000-000000000002"
    }
}
