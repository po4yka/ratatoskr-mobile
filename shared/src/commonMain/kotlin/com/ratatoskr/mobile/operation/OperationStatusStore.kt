package com.ratatoskr.mobile.operation

import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.api.generated.model.OperationSummary
import com.ratatoskr.mobile.submission.AuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.AuthorizedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface OperationRepositoryResult<out T> {
    data class Success<T>(
        val value: T,
    ) : OperationRepositoryResult<T>

    data object Unauthorized : OperationRepositoryResult<Nothing>

    data object NotFoundOrNotOwned : OperationRepositoryResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
    ) : OperationRepositoryResult<Nothing>
}

interface OperationStatusRepository {
    suspend fun list(cursor: String? = null): OperationRepositoryResult<OperationList>

    suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot>
}

fun interface OperationPollingDelay {
    suspend fun wait(duration: Duration)
}

data class OperationPresentation(
    val operationId: String,
    val kind: String,
    val status: OperationStatus,
    val statusChangedAt: Instant,
    val progressPercent: Int?,
    val stage: String?,
    val warningCount: Int,
    val errorCount: Int,
    val resultCount: Int,
)

sealed interface OperationListState {
    data object Idle : OperationListState

    data object Loading : OperationListState

    data object Empty : OperationListState

    data class Content(
        val operations: List<OperationPresentation>,
    ) : OperationListState

    data object Offline : OperationListState

    data object RePairingRequired : OperationListState

    data class Failed(
        val canRetry: Boolean,
    ) : OperationListState
}

sealed interface OperationDetailState {
    data object Idle : OperationDetailState

    data object Loading : OperationDetailState

    data class Content(
        val operation: OperationPresentation,
    ) : OperationDetailState

    data object Offline : OperationDetailState

    data object RePairingRequired : OperationDetailState

    data object NotFoundOrNotOwned : OperationDetailState

    data class Failed(
        val requiresManualRetry: Boolean,
    ) : OperationDetailState
}

class OperationListStore(
    private val repository: OperationStatusRepository,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<OperationListState>(OperationListState.Idle)
    val state: StateFlow<OperationListState> = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value == OperationListState.Loading) return
        mutableState.value = OperationListState.Loading
        scope.launch {
            mutableState.value =
                when (val result = repository.list()) {
                    is OperationRepositoryResult.Success -> {
                        val rows = result.value.operations.map { it.presentation() }
                        if (rows.isEmpty()) OperationListState.Empty else OperationListState.Content(rows)
                    }
                    OperationRepositoryResult.Unauthorized -> OperationListState.RePairingRequired
                    OperationRepositoryResult.NotFoundOrNotOwned -> OperationListState.Failed(canRetry = false)
                    is OperationRepositoryResult.Unavailable ->
                        if (result.retryable) OperationListState.Offline else OperationListState.Failed(canRetry = true)
                }
        }
    }
}

class OperationDetailStore(
    val operationId: String,
    private val repository: OperationStatusRepository,
    private val scope: CoroutineScope,
    private val pollingDelay: OperationPollingDelay,
    private val failureCap: Int = 3,
) {
    private val mutableState = MutableStateFlow<OperationDetailState>(OperationDetailState.Idle)
    val state: StateFlow<OperationDetailState> = mutableState.asStateFlow()
    private var visible = false
    private var pollingJob: Job? = null
    private var consecutiveFailures = 0

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) startPolling() else pollingJob?.cancel()
    }

    fun retry() {
        consecutiveFailures = 0
        pollingJob?.cancel()
        startPolling(force = true)
    }

    private fun startPolling(force: Boolean = false) {
        if (!visible && !force) return
        if (pollingJob?.isActive == true) return
        pollingJob =
            scope.launch {
                if (mutableState.value !is OperationDetailState.Content) {
                    mutableState.value = OperationDetailState.Loading
                }
                while (visible || force) {
                    when (val result = repository.read(operationId)) {
                        is OperationRepositoryResult.Success -> {
                            consecutiveFailures = 0
                            val candidate = result.value.presentation()
                            val current =
                                (mutableState.value as? OperationDetailState.Content)?.operation
                            if (current == null || candidate.statusChangedAt > current.statusChangedAt) {
                                mutableState.value = OperationDetailState.Content(candidate)
                            }
                            val visibleOperation =
                                (mutableState.value as? OperationDetailState.Content)?.operation
                            if (visibleOperation?.status?.isTerminal() == true) break
                        }
                        OperationRepositoryResult.Unauthorized -> {
                            mutableState.value = OperationDetailState.RePairingRequired
                            break
                        }
                        OperationRepositoryResult.NotFoundOrNotOwned -> {
                            mutableState.value = OperationDetailState.NotFoundOrNotOwned
                            break
                        }
                        is OperationRepositoryResult.Unavailable -> {
                            consecutiveFailures += 1
                            if (consecutiveFailures >= failureCap || !result.retryable) {
                                mutableState.value = OperationDetailState.Failed(requiresManualRetry = true)
                                break
                            }
                            mutableState.value = OperationDetailState.Offline
                        }
                    }
                    pollingDelay.wait(POLL_INTERVAL)
                    if (force) break
                }
            }
    }

    private companion object {
        val POLL_INTERVAL = 2.seconds
    }
}

class AuthorizedOperationStatusRepository(
    private val api: PlatformOperationsApi,
    private val authorizedRequests: AuthorizedRequestExecutor,
) : OperationStatusRepository {
    override suspend fun list(cursor: String?): OperationRepositoryResult<OperationList> =
        execute { authorization -> api.list(authorization, cursor) }

    override suspend fun read(operationId: String): OperationRepositoryResult<OperationSnapshot> =
        execute { authorization -> api.read(authorization, operationId) }

    private suspend fun <T> execute(
        request: suspend (com.ratatoskr.mobile.identity.Authorization) -> PlatformOperationsResult<T>,
    ): OperationRepositoryResult<T> =
        when (
            val result =
                authorizedRequests.execute { authorization ->
                    when (val response = request(authorization)) {
                        PlatformOperationsResult.Unauthorized -> AuthorizedResult.Unauthorized
                        else -> AuthorizedResult.Success(response)
                    }
                }
        ) {
            is AuthorizedResult.Success -> result.value.repositoryResult()
            AuthorizedResult.Unauthorized -> OperationRepositoryResult.Unauthorized
            is AuthorizedResult.Failure -> OperationRepositoryResult.Unavailable(result.retryable)
        }

    private fun <T> PlatformOperationsResult<T>.repositoryResult(): OperationRepositoryResult<T> =
        when (this) {
            is PlatformOperationsResult.Success -> OperationRepositoryResult.Success(value)
            PlatformOperationsResult.Unauthorized -> OperationRepositoryResult.Unauthorized
            PlatformOperationsResult.NotFoundOrNotOwned -> OperationRepositoryResult.NotFoundOrNotOwned
            is PlatformOperationsResult.Unavailable -> OperationRepositoryResult.Unavailable(retryable)
        }
}

private fun OperationSummary.presentation() =
    OperationPresentation(
        operationId = operationId,
        kind = kind,
        status = status,
        statusChangedAt = statusChangedAt,
        progressPercent = progressPercent,
        stage = stage,
        warningCount = 0,
        errorCount = 0,
        resultCount = 0,
    )

private fun OperationSnapshot.presentation() =
    OperationPresentation(
        operationId = operationId,
        kind = kind,
        status = status,
        statusChangedAt = statusChangedAt,
        progressPercent = progressPercent,
        stage = stage,
        warningCount = warnings?.size ?: 0,
        errorCount = errors?.size ?: 0,
        resultCount = results?.size ?: 0,
    )

private fun OperationStatus.isTerminal(): Boolean =
    this == OperationStatus.SUCCEEDED ||
        this == OperationStatus.PARTIALLY_SUCCEEDED ||
        this == OperationStatus.FAILED ||
        this == OperationStatus.CANCELLED
