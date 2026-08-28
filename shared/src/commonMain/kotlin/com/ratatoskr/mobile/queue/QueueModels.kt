package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.capture.CaptureCodec
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CaptureRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
enum class QueueState {
    Queued,
    InFlight,
    RetryWait,
    AuthRequired,
    PermanentFailure,
    Accepted,
    Tracking,
    Completed,
    ResolutionConflict,
    Cancelled,
}

@Serializable
enum class QueueFailure {
    Connectivity,
    ServerUnavailable,
    RateLimited,
    Authentication,
    Validation,
    Policy,
    Size,
    LocalFile,
}

@Serializable
data class OperationProjection(
    val operationId: String,
    val status: OperationStatus,
    val retryable: Boolean,
    val progressPercent: Int?,
    val stage: String?,
    val statusChangedAt: Instant,
    val terminatedAt: Instant?,
    val warningCount: Int,
    val errorCount: Int,
    val resultCount: Int,
)

object OperationProjector {
    fun apply(
        current: OperationProjection?,
        expectedOperationId: String,
        snapshot: OperationSnapshot,
    ): QueueResult<OperationProjection> {
        if (snapshot.operationId != expectedOperationId) {
            return QueueResult.Failure(QueueRejection.OperationMismatch)
        }
        val terminal = snapshot.status.isTerminal()
        if (snapshot.statusChangedAt < snapshot.acceptedAt || terminal != (snapshot.terminatedAt != null)) {
            return QueueResult.Failure(QueueRejection.ProjectionConflict)
        }
        if (current != null) {
            if (snapshot.statusChangedAt < current.statusChangedAt) return QueueResult.Success(current)
            if (snapshot.statusChangedAt == current.statusChangedAt) {
                return if (snapshot.status == current.status) {
                    QueueResult.Success(current)
                } else {
                    QueueResult.Failure(QueueRejection.ProjectionConflict)
                }
            }
            if (current.status.isTerminal()) return QueueResult.Success(current)
        }
        return QueueResult.Success(
            OperationProjection(
                operationId = snapshot.operationId,
                status = snapshot.status,
                retryable = snapshot.retryable,
                progressPercent = snapshot.progressPercent,
                stage = snapshot.stage,
                statusChangedAt = snapshot.statusChangedAt,
                terminatedAt = snapshot.terminatedAt,
                warningCount = snapshot.warnings?.size ?: 0,
                errorCount = snapshot.errors?.size ?: 0,
                resultCount = snapshot.results?.size ?: 0,
            ),
        )
    }

    private fun OperationStatus.isTerminal() =
        this == OperationStatus.SUCCEEDED ||
            this == OperationStatus.PARTIALLY_SUCCEEDED ||
            this == OperationStatus.FAILED ||
            this == OperationStatus.CANCELLED
}

@Serializable
data class QueueRecord(
    val localId: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
    val request: CaptureRequest,
    val sourceSequence: Long,
    val state: QueueState,
    val attemptCount: Int = 0,
    val nextEligibleAt: Instant,
    val claimToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val failure: QueueFailure? = null,
    val operationId: String? = null,
    val conflictingOperationId: String? = null,
    val projection: OperationProjection? = null,
)

data class QueueClaim(
    val record: QueueRecord,
    val token: String,
)

data class QueueLimits(
    val maxUnfinishedRecords: Int = 500,
    val maxInlinePayloadBytes: Int = 10 * 1024 * 1024,
    val maxTextBytes: Int = 100 * 1024,
    val maxUrlLength: Int = 2048,
    val maxStagedFileBytes: Long = 100L * 1024L * 1024L,
    val maxAttempts: Int = 12,
)

data class RetryPolicy(
    val baseDelay: Duration = 30.seconds,
    val maxDelay: Duration = 6.hours,
    val maxServerDelay: Duration = 24.hours,
)

data class RetryDecision(
    val nextEligibleAt: Instant?,
    val exhausted: Boolean,
)

class BackoffPolicy(
    private val policy: RetryPolicy = RetryPolicy(),
    private val maxAttempts: Int = QueueLimits().maxAttempts,
    private val jitter: QueueJitter,
) {
    fun schedule(
        attemptCount: Int,
        failure: QueueFailure,
        now: Instant,
        serverRetryAt: Instant? = null,
    ): RetryDecision {
        if (failure !in RETRYABLE_FAILURES) return RetryDecision(nextEligibleAt = null, exhausted = false)
        if (attemptCount >= maxAttempts) return RetryDecision(nextEligibleAt = null, exhausted = true)

        var ceiling = policy.baseDelay
        repeat((attemptCount - 1).coerceAtLeast(0)) {
            ceiling = (ceiling * 2).coerceAtMost(policy.maxDelay)
        }
        val fraction = jitter.fraction().coerceIn(0.0, 1.0)
        val localTime = now + ceiling / 2 + ceiling / 2 * fraction
        val boundedServerTime = serverRetryAt?.coerceAtMost(now + policy.maxServerDelay)
        return RetryDecision(
            nextEligibleAt = if (boundedServerTime != null && boundedServerTime > localTime) boundedServerTime else localTime,
            exhausted = false,
        )
    }

    private companion object {
        val RETRYABLE_FAILURES =
            setOf(
                QueueFailure.Connectivity,
                QueueFailure.ServerUnavailable,
                QueueFailure.RateLimited,
            )
    }
}

sealed interface QueueResult<out T> {
    data class Success<T>(
        val value: T,
    ) : QueueResult<T>

    data class Failure(
        val reason: QueueRejection,
    ) : QueueResult<Nothing>
}

enum class QueueRejection {
    InvalidCapture,
    CapacityExceeded,
    IdempotencyConflict,
    NotFound,
    StaleClaim,
    OperationMismatch,
    ProjectionConflict,
}

fun interface QueueClock {
    fun now(): Instant
}

fun interface QueueKeyGenerator {
    fun next(): String
}

fun interface QueueJitter {
    fun fraction(): Double
}

interface QueuePersistence {
    suspend fun <T> transaction(block: suspend QueueTransaction.() -> T): T

    fun close()
}

interface QueueTransaction {
    suspend fun records(): List<QueueRecord>

    suspend fun insert(record: QueueRecord)

    suspend fun update(record: QueueRecord)
}

class CaptureQueue(
    private val persistence: QueuePersistence,
    private val clock: QueueClock,
    private val keyGenerator: QueueKeyGenerator,
    private val jitter: QueueJitter,
    private val limits: QueueLimits = QueueLimits(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    private val mutex = Mutex()

    suspend fun enqueue(
        request: CaptureRequest,
        idempotencyKey: String? = null,
    ): QueueResult<QueueRecord> =
        mutex.withLock {
            val rejection = CaptureCodec.validate(request, limits)
            if (rejection != null) return@withLock QueueResult.Failure(rejection)
            val fingerprint = CaptureCodec.encode(request)
            persistence.transaction {
                val existing = records()
                val resolvedKey = idempotencyKey ?: keyGenerator.next()
                val matching = existing.firstOrNull { it.idempotencyKey == resolvedKey }
                if (matching != null) {
                    return@transaction if (matching.requestFingerprint == fingerprint) {
                        QueueResult.Success(matching)
                    } else {
                        QueueResult.Failure(QueueRejection.IdempotencyConflict)
                    }
                }
                val unfinished = existing.filterNot { it.state.isTerminalQueueState() }
                val inlineBytes = unfinished.sumOf { it.request.inlinePayloadBytes() } + request.inlinePayloadBytes()
                if (unfinished.size >= limits.maxUnfinishedRecords || inlineBytes > limits.maxInlinePayloadBytes) {
                    return@transaction QueueResult.Failure(QueueRejection.CapacityExceeded)
                }
                val sourceSequence =
                    existing
                        .asSequence()
                        .filter { it.request.owner == request.owner && it.request.source == request.source }
                        .maxOfOrNull { it.sourceSequence }
                        ?.plus(1) ?: 1
                val record =
                    QueueRecord(
                        localId = keyGenerator.next(),
                        idempotencyKey = resolvedKey,
                        requestFingerprint = fingerprint,
                        request = request,
                        sourceSequence = sourceSequence,
                        state = QueueState.Queued,
                        nextEligibleAt = request.createdAt,
                    )
                insert(record)
                QueueResult.Success(record)
            }
        }

    suspend fun inspect(localId: String): QueueRecord? =
        mutex.withLock {
            persistence.transaction { records().firstOrNull { it.localId == localId } }
        }

    suspend fun claimReady(
        owner: CaptureOwner,
        leaseDuration: Duration,
    ): QueueClaim? =
        mutex.withLock {
            if (leaseDuration <= Duration.ZERO) return@withLock null
            val now = clock.now()
            persistence.transaction {
                val ownerRecords = records().filter { it.request.owner == owner }
                val laneHeads =
                    ownerRecords
                        .filterNot { it.state.isTerminalQueueState() }
                        .groupBy { it.request.source }
                        .values
                        .mapNotNull { lane -> lane.minByOrNull { it.sourceSequence } }
                val selected =
                    laneHeads
                        .filter { it.isReadyAt(now) }
                        .minWithOrNull(
                            compareBy<QueueRecord> { it.nextEligibleAt }
                                .thenBy { it.request.createdAt }
                                .thenBy { it.localId },
                        ) ?: return@transaction null
                val token = keyGenerator.next()
                val claimed =
                    selected.copy(
                        state = QueueState.InFlight,
                        claimToken = token,
                        leaseExpiresAt = now + leaseDuration,
                    )
                update(claimed)
                QueueClaim(claimed, token)
            }
        }

    suspend fun recordFailure(
        localId: String,
        claimToken: String,
        failure: QueueFailure,
        serverRetryAt: Instant? = null,
    ): QueueResult<QueueRecord> =
        mutex.withLock {
            persistence.transaction {
                val current =
                    records().firstOrNull { it.localId == localId }
                        ?: return@transaction QueueResult.Failure(QueueRejection.NotFound)
                if (current.state != QueueState.InFlight || current.claimToken != claimToken) {
                    return@transaction QueueResult.Failure(QueueRejection.StaleClaim)
                }
                val attemptCount =
                    if (failure == QueueFailure.Authentication) {
                        current.attemptCount
                    } else {
                        current.attemptCount + 1
                    }
                val decision =
                    BackoffPolicy(
                        policy = retryPolicy,
                        maxAttempts = limits.maxAttempts,
                        jitter = jitter,
                    ).schedule(
                        attemptCount = attemptCount,
                        failure = failure,
                        now = clock.now(),
                        serverRetryAt = serverRetryAt,
                    )
                val state =
                    when {
                        failure == QueueFailure.Authentication -> QueueState.AuthRequired
                        decision.nextEligibleAt != null -> QueueState.RetryWait
                        else -> QueueState.PermanentFailure
                    }
                val updated =
                    current.copy(
                        state = state,
                        attemptCount = attemptCount,
                        nextEligibleAt = decision.nextEligibleAt ?: current.nextEligibleAt,
                        claimToken = null,
                        leaseExpiresAt = null,
                        failure = failure,
                    )
                update(updated)
                QueueResult.Success(updated)
            }
        }

    suspend fun recordAccepted(
        localId: String,
        operationId: String,
    ): QueueResult<QueueRecord> =
        mutex.withLock {
            persistence.transaction {
                val current =
                    records().firstOrNull { it.localId == localId }
                        ?: return@transaction QueueResult.Failure(QueueRejection.NotFound)
                val updated =
                    when (val existingOperationId = current.operationId) {
                        null ->
                            current.copy(
                                state = QueueState.Accepted,
                                claimToken = null,
                                leaseExpiresAt = null,
                                failure = null,
                                operationId = operationId,
                            )
                        operationId -> return@transaction QueueResult.Success(current)
                        else ->
                            current.copy(
                                state = QueueState.ResolutionConflict,
                                claimToken = null,
                                leaseExpiresAt = null,
                                conflictingOperationId = operationId,
                            )
                    }
                update(updated)
                QueueResult.Success(updated)
            }
        }

    suspend fun applySnapshot(
        localId: String,
        snapshot: OperationSnapshot,
    ): QueueResult<QueueRecord> =
        mutex.withLock {
            persistence.transaction {
                val current =
                    records().firstOrNull { it.localId == localId }
                        ?: return@transaction QueueResult.Failure(QueueRejection.NotFound)
                val operationId =
                    current.operationId
                        ?: return@transaction QueueResult.Failure(QueueRejection.OperationMismatch)
                when (val projected = OperationProjector.apply(current.projection, operationId, snapshot)) {
                    is QueueResult.Failure -> projected
                    is QueueResult.Success -> {
                        val projection = projected.value
                        val updated =
                            current.copy(
                                state =
                                    if (projection.status.isTerminalOperationStatus()) {
                                        QueueState.Completed
                                    } else {
                                        QueueState.Tracking
                                    },
                                projection = projection,
                            )
                        if (updated != current) update(updated)
                        QueueResult.Success(updated)
                    }
                }
            }
        }

    fun close() = persistence.close()
}

private fun QueueRecord.isReadyAt(now: Instant): Boolean =
    when (state) {
        QueueState.Queued -> nextEligibleAt <= now
        QueueState.RetryWait -> nextEligibleAt <= now
        QueueState.InFlight -> leaseExpiresAt?.let { it <= now } == true
        else -> false
    }

private fun QueueState.isTerminalQueueState(): Boolean =
    this == QueueState.PermanentFailure ||
        this == QueueState.Completed ||
        this == QueueState.ResolutionConflict ||
        this == QueueState.Cancelled

private fun OperationStatus.isTerminalOperationStatus(): Boolean =
    this == OperationStatus.SUCCEEDED ||
        this == OperationStatus.PARTIALLY_SUCCEEDED ||
        this == OperationStatus.FAILED ||
        this == OperationStatus.CANCELLED

private fun CaptureRequest.inlinePayloadBytes(): Int =
    when (val value = payload) {
        is com.ratatoskr.mobile.capture.CapturePayload.Url -> value.value.encodeToByteArray().size
        is com.ratatoskr.mobile.capture.CapturePayload.TextNote -> value.text.encodeToByteArray().size
        is com.ratatoskr.mobile.capture.CapturePayload.FileReference -> 0
    }
