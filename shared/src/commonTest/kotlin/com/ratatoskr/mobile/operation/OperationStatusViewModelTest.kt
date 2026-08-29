package com.ratatoskr.mobile.operation

import app.cash.turbine.test
import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.api.generated.model.OperationSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OperationStatusViewModelTest {
    @Test
    fun list_exposes_loading_empty_fixture_and_offline_states() =
        runTest {
            val repository =
                FakeRepository(
                    listResults =
                        mutableListOf(
                            OperationRepositoryResult.Success(OperationList(emptyList())),
                            OperationRepositoryResult.Success(OperationList(listOf(summary(OperationStatus.RUNNING)))),
                            OperationRepositoryResult.Unavailable(retryable = true),
                        ),
                )
            val store = OperationListStore(repository, this)

            store.state.test {
                assertEquals(OperationListState.Idle, awaitItem())
                store.refresh()
                assertEquals(OperationListState.Loading, awaitItem())
                assertEquals(OperationListState.Empty, awaitItem())
                store.refresh()
                assertEquals(OperationListState.Loading, awaitItem())
                assertIs<OperationListState.Content>(awaitItem())
                store.refresh()
                assertEquals(OperationListState.Loading, awaitItem())
                assertEquals(OperationListState.Offline, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun running_detail_advances_monotonically_to_terminal() =
        runTest {
            val repository =
                FakeRepository(
                    readResults =
                        mutableListOf(
                            OperationRepositoryResult.Success(snapshot(OperationStatus.RUNNING, 1)),
                            OperationRepositoryResult.Success(snapshot(OperationStatus.SUCCEEDED, 2)),
                        ),
                )
            val store = detailStore(repository)

            store.setVisible(true)
            runCurrent()
            assertEquals(OperationStatus.RUNNING, content(store).status)
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(OperationStatus.SUCCEEDED, content(store).status)
            assertEquals(2, repository.readCount)
        }

    @Test
    fun older_snapshot_does_not_regress() =
        runTest {
            val repository =
                FakeRepository(
                    readResults =
                        mutableListOf(
                            OperationRepositoryResult.Success(snapshot(OperationStatus.RUNNING, 2)),
                            OperationRepositoryResult.Success(snapshot(OperationStatus.QUEUED, 1)),
                        ),
                )
            val store = detailStore(repository)

            store.setVisible(true)
            runCurrent()
            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(OperationStatus.RUNNING, content(store).status)
            store.setVisible(false)
        }

    @Test
    fun polling_stops_when_hidden_or_terminal() =
        runTest {
            val hiddenRepository =
                FakeRepository(
                    readResults = mutableListOf(OperationRepositoryResult.Success(snapshot(OperationStatus.RUNNING, 1))),
                )
            val hidden = detailStore(hiddenRepository)
            hidden.setVisible(true)
            runCurrent()
            hidden.setVisible(false)
            advanceTimeBy(10.seconds)
            assertEquals(1, hiddenRepository.readCount)

            val terminalRepository =
                FakeRepository(
                    readResults = mutableListOf(OperationRepositoryResult.Success(snapshot(OperationStatus.SUCCEEDED, 1))),
                )
            val terminal = detailStore(terminalRepository)
            terminal.setVisible(true)
            advanceUntilIdle()
            assertEquals(1, terminalRepository.readCount)
        }

    @Test
    fun failure_cap_requires_manual_retry() =
        runTest {
            val repository =
                FakeRepository(
                    readResults =
                        mutableListOf(
                            OperationRepositoryResult.Unavailable(true),
                            OperationRepositoryResult.Unavailable(true),
                            OperationRepositoryResult.Unavailable(true),
                            OperationRepositoryResult.Success(snapshot(OperationStatus.RUNNING, 1)),
                        ),
                )
            val store = detailStore(repository)

            store.setVisible(true)
            advanceTimeBy(10.seconds)
            runCurrent()
            val failed = assertIs<OperationDetailState.Failed>(store.state.value)
            assertTrue(failed.requiresManualRetry)
            assertEquals(3, repository.readCount)
            store.retry()
            runCurrent()
            assertEquals(OperationStatus.RUNNING, content(store).status)
        }

    private fun kotlinx.coroutines.test.TestScope.detailStore(repository: FakeRepository) =
        OperationDetailStore(
            operationId = OPERATION_ID,
            repository = repository,
            scope = this,
            pollingDelay = OperationPollingDelay { delay(it) },
        )

    private fun content(store: OperationDetailStore) = assertIs<OperationDetailState.Content>(store.state.value).operation

    private class FakeRepository(
        private val listResults: MutableList<OperationRepositoryResult<OperationList>> = mutableListOf(),
        private val readResults: MutableList<OperationRepositoryResult<OperationSnapshot>> = mutableListOf(),
    ) : OperationStatusRepository {
        var readCount = 0

        override suspend fun list(cursor: String?): OperationRepositoryResult<OperationList> = listResults.removeAt(0)

        override suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot> {
            readCount += 1
            return readResults.removeAt(0)
        }
    }

    private fun summary(status: OperationStatus) =
        OperationSummary(
            acceptedAt = BASE,
            correlationId = "operation:$OPERATION_ID",
            kind = "capture",
            operationId = OPERATION_ID,
            retryable = false,
            status = status,
            statusChangedAt = BASE,
            progressPercent = 25,
            stage = "extracting",
        )

    private fun snapshot(
        status: OperationStatus,
        minute: Int,
    ) = OperationSnapshot(
        acceptedAt = BASE,
        correlationId = "operation:$OPERATION_ID",
        kind = "capture",
        operationId = OPERATION_ID,
        retryable = false,
        status = status,
        statusChangedAt = BASE + minute.seconds,
        progressPercent = if (status == OperationStatus.SUCCEEDED) 100 else 25,
        stage = if (status == OperationStatus.SUCCEEDED) "done" else "extracting",
        terminatedAt = if (status == OperationStatus.SUCCEEDED) BASE + minute.seconds else null,
    )

    private companion object {
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
        val BASE = Instant.parse("2026-08-29T00:00:00Z")
    }
}
