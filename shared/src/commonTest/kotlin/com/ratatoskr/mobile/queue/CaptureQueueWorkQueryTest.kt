package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.capture.CaptureSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class CaptureQueueWorkQueryTest {
    @Test
    fun next_wake_time_is_owner_scoped_and_durable() =
        runTest {
            val persistence = MemoryQueuePersistence()
            val clock = MutableQueueClock()
            val first = testQueue(persistence = persistence, clock = clock)
            val ownerA = first.enqueue(captureRequest(owner = OWNER_A)).success()
            first.enqueue(captureRequest(owner = OWNER_B)).success()
            val claim = assertNotNull(first.claimReady(OWNER_A, 1.minutes))
            val waiting = first.recordFailure(ownerA.localId, claim.token, QueueFailure.Connectivity).success()

            val reopened = testQueue(persistence = persistence, clock = clock)
            assertEquals(waiting.nextEligibleAt, reopened.nextWakeAt(OWNER_A))
            assertEquals(NOW, reopened.nextWakeAt(OWNER_B))
        }

    @Test
    fun accepted_operations_are_refreshable_not_submittable() =
        runTest {
            val queue = testQueue()
            val stored = queue.enqueue(captureRequest(owner = OWNER_A)).success()
            queue.recordAccepted(stored.localId, OPERATION_ID).success()

            assertEquals(listOf(stored.localId), queue.pendingOperationRefreshes(OWNER_A).map { it.localId })
            assertEquals(emptyList(), queue.pendingSubmissions(OWNER_A))
            assertNull(queue.nextWakeAt(OWNER_A))
        }

    @Test
    fun terminal_refresh_unblocks_source_lane() =
        runTest {
            val queue = testQueue()
            val first =
                queue
                    .enqueue(
                        captureRequest(owner = OWNER_A, source = CaptureSource.AndroidShareTarget),
                    ).success()
            val second =
                queue
                    .enqueue(
                        captureRequest(owner = OWNER_A, source = CaptureSource.AndroidShareTarget),
                    ).success()
            queue.recordAccepted(first.localId, OPERATION_ID).success()
            assertNull(queue.claimReady(OWNER_A, 1.minutes))

            queue.applySnapshot(first.localId, terminalSnapshot()).success()

            assertEquals(second.localId, assertNotNull(queue.claimReady(OWNER_A, 1.minutes)).record.localId)
        }

    private fun terminalSnapshot() =
        OperationSnapshot(
            acceptedAt = NOW,
            correlationId = "operation:$OPERATION_ID",
            kind = "capture",
            operationId = OPERATION_ID,
            retryable = false,
            status = OperationStatus.SUCCEEDED,
            statusChangedAt = NOW + 1.minutes,
            terminatedAt = NOW + 1.minutes,
        )

    private companion object {
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
    }
}
