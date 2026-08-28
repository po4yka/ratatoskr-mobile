package com.ratatoskr.mobile.operation

import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.queue.OperationProjector
import com.ratatoskr.mobile.queue.QueueRejection
import com.ratatoskr.mobile.queue.QueueResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OperationProjectionTest {
    private val acceptedAt = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun newer_snapshots_advance_to_terminal() {
        var projection = success(OperationProjector.apply(null, OPERATION_ID, snapshot(OperationStatus.ACCEPTED, 0)))
        projection = success(OperationProjector.apply(projection, OPERATION_ID, snapshot(OperationStatus.QUEUED, 1)))
        projection = success(OperationProjector.apply(projection, OPERATION_ID, snapshot(OperationStatus.RUNNING, 2)))
        projection = success(OperationProjector.apply(projection, OPERATION_ID, snapshot(OperationStatus.SUCCEEDED, 3)))

        assertEquals(OperationStatus.SUCCEEDED, projection.status)
        assertEquals(acceptedAt + 3.seconds, projection.statusChangedAt)
        assertEquals(acceptedAt + 3.seconds, projection.terminatedAt)
    }

    @Test
    fun duplicate_and_older_snapshots_do_not_regress_terminal() {
        val terminal = success(OperationProjector.apply(null, OPERATION_ID, snapshot(OperationStatus.SUCCEEDED, 3)))

        val duplicate = success(OperationProjector.apply(terminal, OPERATION_ID, snapshot(OperationStatus.SUCCEEDED, 3)))
        val older = success(OperationProjector.apply(terminal, OPERATION_ID, snapshot(OperationStatus.RUNNING, 2)))
        val laterNonTerminal = success(OperationProjector.apply(terminal, OPERATION_ID, snapshot(OperationStatus.RUNNING, 4)))

        assertEquals(terminal, duplicate)
        assertEquals(terminal, older)
        assertEquals(terminal, laterNonTerminal)
    }

    @Test
    fun equal_time_conflict_is_refused() {
        val current = success(OperationProjector.apply(null, OPERATION_ID, snapshot(OperationStatus.QUEUED, 1)))

        val result = OperationProjector.apply(current, OPERATION_ID, snapshot(OperationStatus.RUNNING, 1))

        assertEquals(QueueResult.Failure(QueueRejection.ProjectionConflict), result)
    }

    private fun snapshot(
        status: OperationStatus,
        offsetSeconds: Int,
    ) = OperationSnapshot(
        acceptedAt = acceptedAt,
        correlationId = "operation:$OPERATION_ID",
        kind = "capture",
        operationId = OPERATION_ID,
        retryable = false,
        status = status,
        statusChangedAt = acceptedAt + offsetSeconds.seconds,
        progressPercent = if (status == OperationStatus.RUNNING) 50 else null,
        terminatedAt = if (status.isTerminal()) acceptedAt + offsetSeconds.seconds else null,
    )

    private fun OperationStatus.isTerminal() =
        this == OperationStatus.SUCCEEDED ||
            this == OperationStatus.PARTIALLY_SUCCEEDED ||
            this == OperationStatus.FAILED ||
            this == OperationStatus.CANCELLED

    private fun success(result: QueueResult<com.ratatoskr.mobile.queue.OperationProjection>) =
        assertIs<QueueResult.Success<com.ratatoskr.mobile.queue.OperationProjection>>(result).value

    private companion object {
        const val OPERATION_ID = "018f0000-0000-7000-8000-000000000001"
    }
}
