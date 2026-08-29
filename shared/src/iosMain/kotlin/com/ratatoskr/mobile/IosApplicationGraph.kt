package com.ratatoskr.mobile

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.github.AuthorizedGithubRepository
import com.ratatoskr.mobile.github.GithubAccess
import com.ratatoskr.mobile.github.GithubActionIdentity
import com.ratatoskr.mobile.github.GithubActionIdentityFactory
import com.ratatoskr.mobile.github.GithubApplicationGraph
import com.ratatoskr.mobile.github.KtorPlatformGithubApi
import com.ratatoskr.mobile.identity.CapabilityState
import com.ratatoskr.mobile.identity.DeviceIdentityState
import com.ratatoskr.mobile.identity.DeviceSessionManager
import com.ratatoskr.mobile.identity.IosKeychainCredentialStorage
import com.ratatoskr.mobile.identity.KtorPlatformIdentityApi
import com.ratatoskr.mobile.identity.MobileCapability
import com.ratatoskr.mobile.library.AuthorizedLibraryRepository
import com.ratatoskr.mobile.library.ContentRouteResult
import com.ratatoskr.mobile.library.ContentRouteTable
import com.ratatoskr.mobile.library.KtorPlatformLibraryApi
import com.ratatoskr.mobile.library.LibraryAccess
import com.ratatoskr.mobile.library.LibraryApplicationGraph
import com.ratatoskr.mobile.library.routeIdOrNull
import com.ratatoskr.mobile.operation.AuthorizedOperationStatusRepository
import com.ratatoskr.mobile.operation.KtorPlatformOperationsApi
import com.ratatoskr.mobile.operation.OperationDetailStore
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.operation.OperationPollingDelay
import com.ratatoskr.mobile.operation.OperationRepositoryResult
import com.ratatoskr.mobile.operation.OperationStatusRepository
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueJitter
import com.ratatoskr.mobile.queue.QueueKeyGenerator
import com.ratatoskr.mobile.queue.createIosQueuePersistence
import com.ratatoskr.mobile.share.CurrentCaptureOwner
import com.ratatoskr.mobile.share.ShareIntake
import com.ratatoskr.mobile.share.ShareStagingStore
import com.ratatoskr.mobile.share.ShareSubmissionAccess
import com.ratatoskr.mobile.submission.CaptureSubmissionCoordinator
import com.ratatoskr.mobile.submission.DeviceAuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.KtorPlatformCaptureApi
import com.ratatoskr.mobile.submission.SubmissionDrainResult
import com.ratatoskr.mobile.submission.SubmissionScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import platform.Foundation.NSUUID
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal enum class IosDrainOutcome {
    Idle,
    MoreWork,
    AuthRequired,
}

internal data class IosReconcileResult(
    val outcome: IosDrainOutcome,
    val nextWakeAt: Instant?,
)

internal class IosQueueDrainCoordinator(
    private val queue: CaptureQueue,
    private val submission: CaptureSubmissionCoordinator,
    private val owner: () -> CaptureOwner?,
    private val canSubmit: () -> Boolean,
    private val operationRepository: OperationStatusRepository,
    private val clock: QueueClock,
) {
    suspend fun reconcile(): IosReconcileResult {
        val currentOwner = owner() ?: return IosReconcileResult(IosDrainOutcome.AuthRequired, null)
        if (!canSubmit()) return IosReconcileResult(IosDrainOutcome.Idle, queue.nextWakeAt(currentOwner))

        var outcome = IosDrainOutcome.Idle
        submissionLoop@ for (index in 0 until MAX_SUBMISSIONS_PER_RUN) {
            when (submission.drainOne(currentOwner)) {
                SubmissionDrainResult.Accepted,
                SubmissionDrainResult.PermanentFailure,
                -> outcome = IosDrainOutcome.MoreWork

                SubmissionDrainResult.NoWork -> break@submissionLoop
                SubmissionDrainResult.RetryScheduled -> break@submissionLoop
                SubmissionDrainResult.AuthRequired ->
                    return IosReconcileResult(IosDrainOutcome.AuthRequired, queue.nextWakeAt(currentOwner))
            }
        }

        queue.pendingOperationRefreshes(currentOwner, MAX_OPERATION_REFRESHES_PER_RUN).forEach { record ->
            val operationId = record.operationId ?: return@forEach
            when (val refreshed = operationRepository.read(operationId)) {
                is OperationRepositoryResult.Success -> queue.applySnapshot(record.localId, refreshed.value)
                OperationRepositoryResult.Unauthorized ->
                    return IosReconcileResult(IosDrainOutcome.AuthRequired, queue.nextWakeAt(currentOwner))

                OperationRepositoryResult.NotFoundOrNotOwned,
                is OperationRepositoryResult.Unavailable,
                -> Unit
            }
        }

        val queueWake = queue.nextWakeAt(currentOwner)
        val operationWake =
            if (queue.pendingOperationRefreshes(currentOwner, 1).isNotEmpty()) {
                clock.now() + OPERATION_REFRESH_INTERVAL
            } else {
                null
            }
        return IosReconcileResult(outcome, earliest(queueWake, operationWake))
    }

    private fun earliest(
        first: Instant?,
        second: Instant?,
    ): Instant? =
        when {
            first == null -> second
            second == null -> first
            first <= second -> first
            else -> second
        }

    private companion object {
        const val MAX_SUBMISSIONS_PER_RUN = 8
        const val MAX_OPERATION_REFRESHES_PER_RUN = 8
        val OPERATION_REFRESH_INTERVAL = 15.minutes
    }
}

/** One long-lived graph owned by the main iOS application, never by the Share Extension. */
class IosApplicationController(
    queuePath: String,
    keychainAccessGroup: String,
    private val scheduleNativeWake: (Long?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = QueueClock { now() }
    private val client = HttpClient(Darwin) { followRedirects = false }
    val sessions =
        DeviceSessionManager(
            api = KtorPlatformIdentityApi(client),
            storage = IosKeychainCredentialStorage(accessGroup = keychainAccessGroup),
        )
    private val submissionAccess: StateFlow<ShareSubmissionAccess> =
        combine(sessions.state, sessions.capabilities) { identity, capabilities ->
            when {
                identity !is DeviceIdentityState.Paired -> ShareSubmissionAccess.PairingRequired
                capabilities is CapabilityState.Ready &&
                    MobileCapability.ContentSubmit.wireName in capabilities.snapshot.names ->
                    ShareSubmissionAccess.Available
                else -> ShareSubmissionAccess.CapabilityUnavailable
            }
        }.stateIn(scope, SharingStarted.Eagerly, ShareSubmissionAccess.PairingRequired)
    private val queue =
        CaptureQueue(
            persistence = createIosQueuePersistence(queuePath),
            clock = clock,
            keyGenerator = QueueKeyGenerator { NSUUID.UUID().UUIDString.lowercase() },
            jitter = QueueJitter { Random.nextDouble() },
        )
    private val scheduler = SubmissionScheduler { instant -> scheduleNativeWake(instant?.toEpochMilliseconds()) }
    private val authorizedRequests = DeviceAuthorizedRequestExecutor(sessions)
    private val libraryAccess =
        combine(sessions.state, sessions.capabilities) { identity, capabilities ->
            when {
                identity !is DeviceIdentityState.Paired -> LibraryAccess.PairingRequired
                capabilities !is CapabilityState.Ready ||
                    MobileCapability.LibrarySearch.wireName !in capabilities.snapshot.names ->
                    LibraryAccess.CapabilityUnavailable
                else ->
                    LibraryAccess.Available(
                        canReplaceReadState =
                            MobileCapability.LibraryReadState.wireName in capabilities.snapshot.names,
                    )
            }
        }.stateIn(scope, SharingStarted.Eagerly, LibraryAccess.PairingRequired)
    internal val library =
        LibraryApplicationGraph(
            liveRepository = AuthorizedLibraryRepository(KtorPlatformLibraryApi(client), authorizedRequests),
            access = libraryAccess,
            scope = scope,
        )
    private val githubAccess =
        combine(sessions.state, sessions.capabilities) { identity, capabilities ->
            val github = (capabilities as? CapabilityState.Ready)?.snapshot?.github
            when {
                identity !is DeviceIdentityState.Paired -> GithubAccess.PairingRequired
                github == null -> GithubAccess.CapabilityUnavailable
                else -> GithubAccess.Available(github)
            }
        }.stateIn(scope, SharingStarted.Eagerly, GithubAccess.PairingRequired)
    internal val github =
        GithubApplicationGraph(
            repository = AuthorizedGithubRepository(KtorPlatformGithubApi(client), authorizedRequests),
            access = githubAccess,
            scope = scope,
            identityFactory =
                GithubActionIdentityFactory {
                    GithubActionIdentity(
                        confirmationEvidenceRef = "mobile-confirmation:${NSUUID.UUID().UUIDString.lowercase()}",
                        idempotencyKey = "mobile-github-action.${NSUUID.UUID().UUIDString.lowercase()}",
                    )
                },
        )
    private val submission =
        CaptureSubmissionCoordinator(
            queue = queue,
            api = KtorPlatformCaptureApi(client, now = clock::now),
            authorizedRequests = authorizedRequests,
        )
    private val operationRepository =
        AuthorizedOperationStatusRepository(
            api = KtorPlatformOperationsApi(client),
            authorizedRequests = authorizedRequests,
        )
    private val coordinator =
        IosQueueDrainCoordinator(
            queue = queue,
            submission = submission,
            owner = ::currentOwner,
            canSubmit = { sessions.isCapabilityAvailable(MobileCapability.ContentSubmit) },
            operationRepository = operationRepository,
            clock = clock,
        )
    internal val shareStore = MutableStateFlow<ShareStagingStore?>(null)
    internal val operationListStore = OperationListStore(operationRepository, scope)
    internal var activeDetailStore: OperationDetailStore? = null
    internal var sceneActive = true
    internal val libraryRoute = MutableStateFlow<ContentRouteResult?>(null)

    fun acceptLibraryLink(value: String): Boolean {
        val parsed = ContentRouteTable.parse(value)
        if (parsed !is ContentRouteResult.Accepted) return false
        libraryRoute.value = parsed
        return true
    }

    fun pendingLibraryRouteId(): String? = libraryRoute.value?.routeIdOrNull()

    fun start() {
        scope.launch {
            sessions.restore()
            reconcile()
        }
    }

    fun presentShare(
        handoffId: String,
        originalText: String,
        url: String?,
        capturedAtEpochMilliseconds: Long,
        onCommitted: () -> Unit,
        onCancelled: () -> Unit,
        onFailure: () -> Unit,
    ) {
        if (shareStore.value != null) return
        val intake =
            if (url == null) {
                ShareIntake.UnsupportedText(originalText)
            } else {
                ShareIntake.Url(originalText, url)
            }
        shareStore.value =
            ShareStagingStore(
                initialIntake = intake,
                owner = CurrentCaptureOwner(::currentOwner),
                queue = queue,
                scheduler = scheduler,
                clock = clock,
                scope = scope,
                submissionAccess = submissionAccess,
                captureSource = CaptureSource.IosShareExtension,
                captureCreatedAt = Instant.fromEpochMilliseconds(capturedAtEpochMilliseconds),
                idempotencyKey = "ios-share-$handoffId",
                onCommitted = {
                    onCommitted()
                    dismissShare()
                },
                onCancelled = {
                    onCancelled()
                    dismissShare()
                },
                onFailure = onFailure,
            )
    }

    suspend fun reconcile(): Long? {
        val result = coordinator.reconcile()
        val wake = result.nextWakeAt?.toEpochMilliseconds()
        scheduleNativeWake(wake)
        return wake
    }

    fun beginReconcile(
        onComplete: (Long?) -> Unit,
        onFailure: () -> Unit,
    ): IosReconcileHandle {
        val job =
            scope.launch {
                try {
                    onComplete(reconcile())
                } catch (_: Throwable) {
                    onFailure()
                }
            }
        return IosReconcileHandle(job)
    }

    fun setSceneActive(active: Boolean) {
        sceneActive = active
        activeDetailStore?.setVisible(active)
        if (active) scope.launch { reconcile() }
    }

    internal fun createOperationDetailStore(operationId: String) =
        OperationDetailStore(
            operationId = operationId,
            repository = operationRepository,
            scope = scope,
            pollingDelay = OperationPollingDelay { delay(it) },
        )

    fun close() {
        shareStore.value?.close()
        activeDetailStore?.setVisible(false)
        scope.cancel()
        queue.close()
        client.close()
    }

    private fun currentOwner(): CaptureOwner? =
        (sessions.state.value as? DeviceIdentityState.Paired)?.let { CaptureOwner(it.origin, it.userId) }

    private fun dismissShare() {
        shareStore.value?.close()
        shareStore.value = null
    }

    private fun now(): Instant = Clock.System.now()
}

class IosReconcileHandle internal constructor(
    private val job: Job,
) {
    fun cancel() = job.cancel()
}

fun createIosCaptureQueue(path: String): CaptureQueue =
    CaptureQueue(
        persistence = createIosQueuePersistence(path),
        clock = QueueClock { Clock.System.now() },
        keyGenerator = QueueKeyGenerator { NSUUID.UUID().UUIDString.lowercase() },
        jitter = QueueJitter { Random.nextDouble() },
    )
