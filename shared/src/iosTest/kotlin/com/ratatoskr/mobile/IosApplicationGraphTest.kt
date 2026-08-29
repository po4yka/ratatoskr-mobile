package com.ratatoskr.mobile

import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.identity.Authorization
import com.ratatoskr.mobile.library.FixtureIds
import com.ratatoskr.mobile.library.FixtureMutationResult
import com.ratatoskr.mobile.library.LibraryAccess
import com.ratatoskr.mobile.library.LibraryApplicationGraph
import com.ratatoskr.mobile.library.LibraryRepository
import com.ratatoskr.mobile.library.LibraryRepositoryResult
import com.ratatoskr.mobile.operation.OperationRepositoryResult
import com.ratatoskr.mobile.operation.OperationStatusRepository
import com.ratatoskr.mobile.queue.MutableQueueClock
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueState
import com.ratatoskr.mobile.queue.captureRequest
import com.ratatoskr.mobile.queue.success
import com.ratatoskr.mobile.queue.testQueue
import com.ratatoskr.mobile.submission.AuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.AuthorizedResult
import com.ratatoskr.mobile.submission.CaptureSubmissionCoordinator
import com.ratatoskr.mobile.submission.CaptureTransportFailure
import com.ratatoskr.mobile.submission.PlatformCaptureApi
import com.ratatoskr.mobile.submission.PlatformCaptureResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class IosApplicationGraphTest {
    @Test
    fun library_stores_share_live_authorization_and_fixture_authority_without_network_curation() =
        runTest {
            val live = CountingLibraryRepository()
            val graph =
                LibraryApplicationGraph(
                    liveRepository = live,
                    access = MutableStateFlow(LibraryAccess.Available(canReplaceReadState = true)),
                    scope = backgroundScope,
                )

            assertNotNull(graph.listStore)
            assertNotNull(graph.readerStore)
            assertTrue(graph.content.fixtures === graph.fixtures)
            assertTrue(graph.fixtures.toggleFavorite(FixtureIds.ARTICLE) is FixtureMutationResult.Success)
            assertEquals(0, live.calls)
        }

    @Test
    fun foreground_reconcile_drains_eligible_handoff() =
        runTest {
            val fixture = fixture(listOf(PlatformCaptureResult.Accepted(OPERATION_ID)))
            val stored = fixture.queue.enqueue(request(), IDEMPOTENCY_KEY).success()

            val result = fixture.coordinator.reconcile()

            assertEquals(IosDrainOutcome.MoreWork, result.outcome)
            assertEquals(QueueState.Accepted, fixture.queue.inspect(stored.localId)?.state)
            assertEquals(OPERATION_ID, fixture.queue.inspect(stored.localId)?.operationId)
            assertEquals(listOf(IDEMPOTENCY_KEY), fixture.api.observedKeys)
            assertEquals(NOW + 15.minutes, result.nextWakeAt)
        }

    @Test
    fun offline_result_preserves_next_queue_time() =
        runTest {
            val retryAt = NOW + 5.minutes
            val fixture =
                fixture(
                    listOf(
                        PlatformCaptureResult.Retryable(
                            CaptureTransportFailure.Connectivity,
                            retryAt,
                        ),
                    ),
                )
            val stored = fixture.queue.enqueue(request(), IDEMPOTENCY_KEY).success()

            val result = fixture.coordinator.reconcile()

            assertEquals(QueueState.RetryWait, fixture.queue.inspect(stored.localId)?.state)
            assertEquals(retryAt, result.nextWakeAt)
            assertEquals(retryAt, fixture.queue.inspect(stored.localId)?.nextEligibleAt)
        }

    @Test
    fun uncertain_restart_reuses_request_and_key() =
        runTest {
            val clock = MutableQueueClock(NOW)
            val fixture =
                fixture(
                    responses =
                        listOf(
                            PlatformCaptureResult.Retryable(CaptureTransportFailure.ServerUnavailable),
                            PlatformCaptureResult.Accepted(OPERATION_ID),
                        ),
                    clock = clock,
                )
            val stored = fixture.queue.enqueue(request(), IDEMPOTENCY_KEY).success()
            val first = fixture.coordinator.reconcile()
            clock.current = assertNotNull(first.nextWakeAt)

            fixture.coordinator.reconcile()

            assertEquals(listOf(IDEMPOTENCY_KEY, IDEMPOTENCY_KEY), fixture.api.observedKeys)
            assertEquals(listOf(URL, URL), fixture.api.observedUrls)
            assertEquals(QueueState.Accepted, fixture.queue.inspect(stored.localId)?.state)
        }

    @Test
    fun revocation_stops_authorization_retry() =
        runTest {
            val queue = testQueue(clock = MutableQueueClock(NOW))
            queue.enqueue(request(), IDEMPOTENCY_KEY).success()
            val api = RecordingCaptureApi(listOf(PlatformCaptureResult.Accepted(OPERATION_ID)))
            val coordinator =
                graph(
                    queue,
                    CaptureSubmissionCoordinator(queue, api, UnauthorizedExecutor),
                )

            val result = coordinator.reconcile()

            assertEquals(IosDrainOutcome.AuthRequired, result.outcome)
            assertTrue(api.observedKeys.isEmpty())
        }

    @Test
    fun background_expiration_leaves_claim_recoverable() =
        runTest {
            val clock = MutableQueueClock(NOW)
            val queue = testQueue(clock = clock)
            val stored = queue.enqueue(request(), IDEMPOTENCY_KEY).success()
            val api = SuspendingCaptureApi()
            val coordinator =
                graph(
                    queue,
                    CaptureSubmissionCoordinator(queue, api, SuccessExecutor),
                )
            val job = launch { coordinator.reconcile() }
            val started = withTimeoutOrNull(1_000) { api.started.await() } != null
            if (!started) job.cancelAndJoin()
            assertTrue(started, "reconcile must claim and begin the Platform request")

            job.cancelAndJoin()
            clock.current = NOW + 3.minutes

            val recovered = queue.claimReady(OWNER, 2.minutes)
            assertEquals(stored.localId, recovered?.record?.localId)
            assertEquals(IDEMPOTENCY_KEY, recovered?.record?.idempotencyKey)
        }

    private fun fixture(
        responses: List<PlatformCaptureResult>,
        clock: MutableQueueClock = MutableQueueClock(NOW),
    ): Fixture {
        val queue = testQueue(clock = clock)
        val api = RecordingCaptureApi(responses)
        val submission = CaptureSubmissionCoordinator(queue, api, SuccessExecutor)
        return Fixture(queue, api, graph(queue, submission))
    }

    private fun graph(
        queue: com.ratatoskr.mobile.queue.CaptureQueue,
        submission: CaptureSubmissionCoordinator,
    ) = IosQueueDrainCoordinator(
        queue = queue,
        submission = submission,
        owner = { OWNER },
        canSubmit = { true },
        operationRepository = EmptyOperationRepository,
        clock = QueueClock { NOW },
    )

    private fun request() =
        captureRequest(
            owner = OWNER,
            source = CaptureSource.IosShareExtension,
            createdAt = NOW,
        )

    private data class Fixture(
        val queue: com.ratatoskr.mobile.queue.CaptureQueue,
        val api: RecordingCaptureApi,
        val coordinator: IosQueueDrainCoordinator,
    )

    private class RecordingCaptureApi(
        responses: List<PlatformCaptureResult>,
    ) : PlatformCaptureApi {
        private val remaining = responses.toMutableList()
        val observedKeys = mutableListOf<String>()
        val observedUrls = mutableListOf<String>()

        override suspend fun submit(
            authorization: Authorization,
            url: String,
            idempotencyKey: String,
        ): PlatformCaptureResult {
            observedKeys += idempotencyKey
            observedUrls += url
            return remaining.removeFirst()
        }
    }

    private class SuspendingCaptureApi : PlatformCaptureApi {
        val started = CompletableDeferred<Unit>()

        override suspend fun submit(
            authorization: Authorization,
            url: String,
            idempotencyKey: String,
        ): PlatformCaptureResult {
            started.complete(Unit)
            awaitCancellation()
        }
    }

    private object SuccessExecutor : AuthorizedRequestExecutor {
        override suspend fun <T> execute(request: suspend (Authorization) -> AuthorizedResult<T>): AuthorizedResult<T> =
            request(Authorization("https://platform.example", "access"))
    }

    private object UnauthorizedExecutor : AuthorizedRequestExecutor {
        override suspend fun <T> execute(request: suspend (Authorization) -> AuthorizedResult<T>): AuthorizedResult<T> =
            AuthorizedResult.Unauthorized
    }

    private object EmptyOperationRepository : OperationStatusRepository {
        override suspend fun list(cursor: String?): OperationRepositoryResult<OperationList> =
            OperationRepositoryResult.Success(OperationList(emptyList(), null))

        override suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot> =
            OperationRepositoryResult.NotFoundOrNotOwned
    }

    private class CountingLibraryRepository : LibraryRepository {
        var calls = 0

        override suspend fun recent(): LibraryRepositoryResult<LibraryPage> {
            calls += 1
            return LibraryRepositoryResult.Success(
                LibraryPage(hasMore = false, items = emptyList(), limit = 25, offset = 0),
            )
        }

        override suspend fun replaceReadState(
            analysisId: String,
            readState: ReadState,
        ): LibraryRepositoryResult<ReadStateResource> {
            calls += 1
            return LibraryRepositoryResult.NotFoundOrNotOwned
        }
    }

    private companion object {
        val NOW = Instant.parse("2026-08-29T10:00:00Z")
        val OWNER = CaptureOwner("https://platform.example", "user-1")
        const val URL = "https://example.test/article"
        const val IDEMPOTENCY_KEY = "ios-share-handoff"
        const val OPERATION_ID = "1518c249-a3d3-4a9b-954a-5a110a3f9dcb"
    }
}
