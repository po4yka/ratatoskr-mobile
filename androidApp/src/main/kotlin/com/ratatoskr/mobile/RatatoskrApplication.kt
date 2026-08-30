package com.ratatoskr.mobile

import android.app.Application
import android.app.NotificationManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.github.AuthorizedGithubRepository
import com.ratatoskr.mobile.github.GithubAccess
import com.ratatoskr.mobile.github.GithubActionIdentity
import com.ratatoskr.mobile.github.GithubActionIdentityFactory
import com.ratatoskr.mobile.github.GithubApplicationGraph
import com.ratatoskr.mobile.github.KtorPlatformGithubApi
import com.ratatoskr.mobile.identity.AndroidKeystoreCredentialStorage
import com.ratatoskr.mobile.identity.CapabilityState
import com.ratatoskr.mobile.identity.DeviceIdentityState
import com.ratatoskr.mobile.identity.DeviceSessionManager
import com.ratatoskr.mobile.identity.KtorPlatformIdentityApi
import com.ratatoskr.mobile.identity.MobileCapability
import com.ratatoskr.mobile.library.AuthorizedLibraryRepository
import com.ratatoskr.mobile.library.KtorPlatformLibraryApi
import com.ratatoskr.mobile.library.LibraryAccess
import com.ratatoskr.mobile.library.LibraryApplicationGraph
import com.ratatoskr.mobile.notification.CompletionNotificationStore
import com.ratatoskr.mobile.notification.CompletionSubscriptionAvailability
import com.ratatoskr.mobile.notification.IntegrationPendingCompletionSubscriptionPort
import com.ratatoskr.mobile.notification.IntegrationPendingNativeNotificationPermissionPort
import com.ratatoskr.mobile.notification.NativeNotificationPermissionState
import com.ratatoskr.mobile.notification.createAndroidCaptureStatusNotifier
import com.ratatoskr.mobile.operation.AuthorizedOperationStatusRepository
import com.ratatoskr.mobile.operation.KtorPlatformOperationsApi
import com.ratatoskr.mobile.operation.OperationDetailStore
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.operation.OperationPollingDelay
import com.ratatoskr.mobile.operation.OperationRepositoryResult
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueJitter
import com.ratatoskr.mobile.queue.QueueKeyGenerator
import com.ratatoskr.mobile.queue.createAndroidQueuePersistence
import com.ratatoskr.mobile.share.AndroidStagedArtifactStore
import com.ratatoskr.mobile.share.ContentResolverAndroidContentSource
import com.ratatoskr.mobile.share.CurrentCaptureOwner
import com.ratatoskr.mobile.share.ShareIntake
import com.ratatoskr.mobile.share.ShareStagingStore
import com.ratatoskr.mobile.share.ShareSubmissionAccess
import com.ratatoskr.mobile.storage.AndroidArtifactLedger
import com.ratatoskr.mobile.storage.AndroidEraseBoundary
import com.ratatoskr.mobile.storage.AndroidEraseGenerationStore
import com.ratatoskr.mobile.storage.AndroidLocalDataErasure
import com.ratatoskr.mobile.storage.ArtifactCleanupCoordinator
import com.ratatoskr.mobile.storage.ArtifactRetentionPolicy
import com.ratatoskr.mobile.storage.ClearDataResult
import com.ratatoskr.mobile.storage.LocalStorageStore
import com.ratatoskr.mobile.submission.CaptureSubmissionCoordinator
import com.ratatoskr.mobile.submission.DeviceAuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.KtorPlatformCaptureApi
import com.ratatoskr.mobile.submission.QueueDrainOutcome
import com.ratatoskr.mobile.submission.QueueDrainer
import com.ratatoskr.mobile.submission.QueueDrainerProvider
import com.ratatoskr.mobile.submission.SubmissionDrainResult
import com.ratatoskr.mobile.submission.WorkManagerSubmissionScheduler
import com.ratatoskr.mobile.transfer.FileUploadDrainOutcome
import com.ratatoskr.mobile.transfer.FileUploadDrainerProvider
import com.ratatoskr.mobile.transfer.FileUploadWorkScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RatatoskrApplication :
    Application(),
    QueueDrainerProvider,
    FileUploadDrainerProvider {
    lateinit var container: AndroidApplicationContainer
        private set

    override val queueDrainer: QueueDrainer
        get() = container.queueDrainer

    override fun currentEraseGeneration(): String = container.eraseGeneration.current()

    override suspend fun drainFileUpload(opaqueOwnerWorkKey: String): FileUploadDrainOutcome = container.drainFileUpload(opaqueOwnerWorkKey)

    override fun onCreate() {
        super.onCreate()
        val startupCredentials = AndroidKeystoreCredentialStorage(this)
        check(
            AndroidLocalDataErasure(
                context = this,
                credentials = startupCredentials,
                closeQueue = {},
                databaseNames = listOf(QUEUE_DATABASE),
                stagedRoots = listOf(java.io.File(noBackupFilesDir, STAGING_DIRECTORY)),
                preferenceNames = emptyList(),
                cacheRoots = listOf(cacheDir),
                boundary = LiveAndroidEraseBoundary(this),
            ).resumeIfNeeded(),
        ) { "Local data erasure must complete before application stores open" }
        container = AndroidApplicationContainer(this)
        container.start()
    }

    private companion object {
        const val QUEUE_DATABASE = "ratatoskr-capture-queue.db"
        const val STAGING_DIRECTORY = "ratatoskr-staging"
    }
}

class AndroidApplicationContainer(
    application: Application,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = QueueClock { Instant.fromEpochMilliseconds(System.currentTimeMillis()) }
    private val client =
        HttpClient(OkHttp) {
            followRedirects = false
        }
    private val credentials = AndroidKeystoreCredentialStorage(application)
    val queue =
        CaptureQueue(
            persistence = createAndroidQueuePersistence(application),
            clock = clock,
            keyGenerator = QueueKeyGenerator { UUID.randomUUID().toString() },
            jitter = QueueJitter { Random.nextDouble() },
        )
    val eraseGeneration = AndroidEraseGenerationStore(application)
    private val eraser =
        AndroidLocalDataErasure(
            context = application,
            credentials = credentials,
            closeQueue = queue::close,
            databaseNames = listOf("ratatoskr-capture-queue.db"),
            stagedRoots = listOf(java.io.File(application.noBackupFilesDir, "ratatoskr-staging")),
            preferenceNames = emptyList(),
            cacheRoots = listOf(application.cacheDir),
            boundary = LiveAndroidEraseBoundary(application),
            generationStore = eraseGeneration,
        )
    val sessions =
        DeviceSessionManager(
            KtorPlatformIdentityApi(client),
            credentials,
            onProvenRevocation = { eraser.begin("proven_remote_revocation") },
        )
    private val authorizedRequests = DeviceAuthorizedRequestExecutor(sessions)
    private val productionShareSubmissionAccess =
        combine(sessions.state, sessions.capabilities) { identity, capabilities ->
            when {
                identity !is DeviceIdentityState.Paired -> ShareSubmissionAccess.PairingRequired
                capabilities is CapabilityState.Ready &&
                    MobileCapability.ContentSubmit.wireName in capabilities.snapshot.names ->
                    ShareSubmissionAccess.Available
                else -> ShareSubmissionAccess.CapabilityUnavailable
            }
        }.stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = ShareSubmissionAccess.PairingRequired,
        )
    internal var shareSubmissionAccess: StateFlow<ShareSubmissionAccess> =
        productionShareSubmissionAccess
    val scheduler = WorkManagerSubmissionScheduler(application)
    val artifactStore =
        AndroidStagedArtifactStore(
            root = java.io.File(application.noBackupFilesDir, "ratatoskr-staging"),
            source = ContentResolverAndroidContentSource(application.contentResolver),
            idFactory = { UUID.randomUUID().toString() },
        )
    private val artifactLedger = AndroidArtifactLedger(queue, artifactStore)
    private val cleanup = ArtifactCleanupCoordinator(artifactLedger)
    val localStorageStore =
        LocalStorageStore(
            initialUsage = ArtifactRetentionPolicy().usage(runBlocking { artifactLedger.inventory() }),
            availability = com.ratatoskr.mobile.transfer.FileTransferAvailability.IntegrationPending,
            cleanup = { cleanup.cleanup(clock.now()).usage },
            erasure = { confirmed ->
                if (!confirmed) {
                    ClearDataResult.Cancelled
                } else if (eraser.begin("confirmed_clear_data")) {
                    sessions.signOut()
                    ClearDataResult.Completed(eraseGeneration.current())
                } else {
                    ClearDataResult.Failed(eraseGeneration.current())
                }
            },
            scope = appScope,
        )
    val notificationStore =
        CompletionNotificationStore(
            availability = CompletionSubscriptionAvailability.IntegrationPending,
            paired = false,
            permission = NativeNotificationPermissionState.NotDetermined,
            native = IntegrationPendingNativeNotificationPermissionPort,
            subscriptions = IntegrationPendingCompletionSubscriptionPort,
            scope = appScope,
        )

    suspend fun drainFileUpload(opaqueOwnerWorkKey: String): FileUploadDrainOutcome {
        val record = queue.inspect(opaqueOwnerWorkKey) ?: return FileUploadDrainOutcome.Complete
        return if (record.uploadReceipt != null) {
            FileUploadDrainOutcome.Complete
        } else {
            FileUploadDrainOutcome.IntegrationPending
        }
    }

    private val notifier = createAndroidCaptureStatusNotifier(application)
    private val submission =
        CaptureSubmissionCoordinator(
            queue = queue,
            api = KtorPlatformCaptureApi(client, now = { clock.now() }),
            authorizedRequests = authorizedRequests,
        )
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
        }.stateIn(appScope, SharingStarted.Eagerly, LibraryAccess.PairingRequired)
    val library =
        LibraryApplicationGraph(
            liveRepository =
                AuthorizedLibraryRepository(
                    KtorPlatformLibraryApi(client),
                    authorizedRequests,
                ),
            access = libraryAccess,
            scope = appScope,
        )
    private val githubAccess =
        combine(sessions.state, sessions.capabilities) { identity, capabilities ->
            val github = (capabilities as? CapabilityState.Ready)?.snapshot?.github
            when {
                identity !is DeviceIdentityState.Paired -> GithubAccess.PairingRequired
                github == null ->
                    GithubAccess.CapabilityUnavailable
                else -> GithubAccess.Available(github)
            }
        }.stateIn(appScope, SharingStarted.Eagerly, GithubAccess.PairingRequired)
    val github =
        GithubApplicationGraph(
            repository = AuthorizedGithubRepository(KtorPlatformGithubApi(client), authorizedRequests),
            access = githubAccess,
            scope = appScope,
            identityFactory =
                GithubActionIdentityFactory {
                    GithubActionIdentity(
                        confirmationEvidenceRef = "mobile-confirmation:${UUID.randomUUID()}",
                        idempotencyKey = "mobile-github-action.${UUID.randomUUID()}",
                    )
                },
        )
    internal var operationRepository: com.ratatoskr.mobile.operation.OperationStatusRepository =
        AuthorizedOperationStatusRepository(
            api = KtorPlatformOperationsApi(client),
            authorizedRequests = DeviceAuthorizedRequestExecutor(sessions),
        )
    val queueDrainer =
        QueueDrainer {
            val paired =
                sessions.state.value as? DeviceIdentityState.Paired
                    ?: return@QueueDrainer QueueDrainOutcome.AuthRequired
            if (!sessions.isCapabilityAvailable(MobileCapability.ContentSubmit)) {
                return@QueueDrainer QueueDrainOutcome.Idle
            }
            val owner = CaptureOwner(paired.origin, paired.userId)
            var outcome = QueueDrainOutcome.Idle
            for (index in 0 until MAX_SUBMISSIONS_PER_RUN) {
                when (submission.drainOne(owner)) {
                    SubmissionDrainResult.Accepted -> outcome = QueueDrainOutcome.MoreWork
                    SubmissionDrainResult.PermanentFailure -> outcome = QueueDrainOutcome.MoreWork
                    SubmissionDrainResult.NoWork -> break
                    SubmissionDrainResult.RetryScheduled -> break
                    SubmissionDrainResult.AuthRequired -> return@QueueDrainer QueueDrainOutcome.AuthRequired
                }
            }
            queue.pendingOperationRefreshes(owner, MAX_OPERATION_REFRESHES_PER_RUN).forEach { record ->
                val operationId = record.operationId ?: return@forEach
                if (record.projection == null) notifier.accepted(operationId)
                when (val refreshed = operationRepository.read(operationId)) {
                    is OperationRepositoryResult.Success -> {
                        queue.applySnapshot(record.localId, refreshed.value)
                        if (refreshed.value.status.isTerminal()) notifier.terminal(operationId)
                    }
                    OperationRepositoryResult.Unauthorized ->
                        return@QueueDrainer QueueDrainOutcome.AuthRequired
                    OperationRepositoryResult.NotFoundOrNotOwned,
                    is OperationRepositoryResult.Unavailable,
                    -> Unit
                }
            }
            val hasTrackedOperations = queue.pendingOperationRefreshes(owner, 1).isNotEmpty()
            scheduler.schedule(
                queue.nextWakeAt(owner)
                    ?: if (hasTrackedOperations) clock.now() + OPERATION_REFRESH_INTERVAL else null,
            )
            outcome
        }

    fun start() {
        appScope.launch { sessions.restore() }
        appScope.launch {
            sessions.state.collectLatest { identity ->
                notificationStore.updatePaired(identity is DeviceIdentityState.Paired)
            }
        }
        appScope.launch {
            productionShareSubmissionAccess.collectLatest { access ->
                if (access == ShareSubmissionAccess.Available) scheduler.schedule(null)
            }
        }
    }

    fun createShareStore(
        intake: ShareIntake,
        scope: CoroutineScope,
    ) = ShareStagingStore(
        initialIntake = intake,
        owner =
            CurrentCaptureOwner {
                (sessions.state.value as? DeviceIdentityState.Paired)?.let {
                    CaptureOwner(it.origin, it.userId)
                }
            },
        queue = queue,
        scheduler = scheduler,
        clock = clock,
        scope = scope,
        submissionAccess = shareSubmissionAccess,
        onCancelled = {
            (intake as? ShareIntake.File)?.let { artifactStore.deleteUnreferenced(it.stagedFileId) }
        },
    )

    fun createOperationListStore(scope: CoroutineScope) = OperationListStore(operationRepository, scope)

    fun createOperationDetailStore(
        operationId: String,
        scope: CoroutineScope,
    ) = OperationDetailStore(
        operationId = operationId,
        repository = operationRepository,
        scope = scope,
        pollingDelay = OperationPollingDelay { delay(it) },
    )

    private companion object {
        const val MAX_SUBMISSIONS_PER_RUN = 8
        const val MAX_OPERATION_REFRESHES_PER_RUN = 8
        val OPERATION_REFRESH_INTERVAL = 15.minutes
    }
}

private class LiveAndroidEraseBoundary(
    context: Application,
) : AndroidEraseBoundary {
    private val workManager = WorkManager.getInstance(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    override fun cancelWorkAndNotifications() {
        workManager.cancelAllWorkByTag(WorkManagerSubmissionScheduler.UNIQUE_WORK_NAME).result.get()
        workManager.cancelAllWorkByTag(FileUploadWorkScheduler.FILE_UPLOAD_TAG).result.get()
        notifications.cancelAll()
    }

    override fun residueCount(): Int {
        val work =
            listOf(
                WorkManagerSubmissionScheduler.UNIQUE_WORK_NAME,
                FileUploadWorkScheduler.FILE_UPLOAD_TAG,
            ).sumOf { tag ->
                workManager.getWorkInfosByTag(tag).get().count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            }
        return work + notifications.activeNotifications.size
    }
}

private fun OperationStatus.isTerminal(): Boolean =
    this == OperationStatus.SUCCEEDED ||
        this == OperationStatus.PARTIALLY_SUCCEEDED ||
        this == OperationStatus.FAILED ||
        this == OperationStatus.CANCELLED
