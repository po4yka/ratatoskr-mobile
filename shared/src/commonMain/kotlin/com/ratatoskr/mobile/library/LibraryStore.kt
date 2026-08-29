package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import com.ratatoskr.mobile.submission.AuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.AuthorizedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ContentAuthority {
    LivePlatform,
    ContractFixture,
    Unavailable,
}

sealed interface LibraryRepositoryResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LibraryRepositoryResult<T>

    data object Unauthorized : LibraryRepositoryResult<Nothing>

    data object NotFoundOrNotOwned : LibraryRepositoryResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
    ) : LibraryRepositoryResult<Nothing>
}

interface LibraryRepository {
    suspend fun recent(): LibraryRepositoryResult<LibraryPage>

    suspend fun replaceReadState(
        analysisId: String,
        readState: ReadState,
    ): LibraryRepositoryResult<ReadStateResource>
}

class AuthorizedLibraryRepository(
    private val api: PlatformLibraryApi,
    private val authorizedRequests: AuthorizedRequestExecutor,
) : LibraryRepository {
    override suspend fun recent(): LibraryRepositoryResult<LibraryPage> = execute { authorization -> api.recent(authorization) }

    override suspend fun replaceReadState(
        analysisId: String,
        readState: ReadState,
    ): LibraryRepositoryResult<ReadStateResource> = execute { authorization -> api.replaceReadState(authorization, analysisId, readState) }

    private suspend fun <T> execute(
        request: suspend (com.ratatoskr.mobile.identity.Authorization) -> PlatformLibraryResult<T>,
    ): LibraryRepositoryResult<T> =
        when (
            val authorized =
                authorizedRequests.execute { authorization ->
                    when (val result = request(authorization)) {
                        PlatformLibraryResult.Unauthorized -> AuthorizedResult.Unauthorized
                        else -> AuthorizedResult.Success(result)
                    }
                }
        ) {
            AuthorizedResult.Unauthorized -> LibraryRepositoryResult.Unauthorized
            is AuthorizedResult.Failure -> LibraryRepositoryResult.Unavailable(authorized.retryable)
            is AuthorizedResult.Success -> authorized.value.toRepositoryResult()
        }

    private fun <T> PlatformLibraryResult<T>.toRepositoryResult(): LibraryRepositoryResult<T> =
        when (this) {
            is PlatformLibraryResult.Success -> LibraryRepositoryResult.Success(value)
            PlatformLibraryResult.Unauthorized -> LibraryRepositoryResult.Unauthorized
            PlatformLibraryResult.NotFoundOrNotOwned -> LibraryRepositoryResult.NotFoundOrNotOwned
            is PlatformLibraryResult.Unavailable ->
                LibraryRepositoryResult.Unavailable(retryable, outcomeUnknown)
        }
}

sealed interface LibraryAccess {
    data object PairingRequired : LibraryAccess

    data object CapabilityUnavailable : LibraryAccess

    data class Available(
        val canReplaceReadState: Boolean,
    ) : LibraryAccess
}

data class LibraryItemPresentation(
    val analysisId: String,
    val documentId: String,
    val title: String,
    val readState: ReadState,
    val snippet: String?,
    val authority: ContentAuthority = ContentAuthority.LivePlatform,
)

enum class LibraryMutationError {
    OutcomeUnknown,
    ItemUnavailable,
    Unavailable,
}

sealed interface LibraryListState {
    data object Idle : LibraryListState

    data object Loading : LibraryListState

    data object Empty : LibraryListState

    data object CapabilityUnavailable : LibraryListState

    data object Offline : LibraryListState

    data object RePairingRequired : LibraryListState

    data class Failed(
        val canRetry: Boolean,
    ) : LibraryListState

    data class Content(
        val items: List<LibraryItemPresentation>,
        val canReplaceReadState: Boolean,
        val mutatingAnalysisId: String? = null,
        val mutationError: LibraryMutationError? = null,
    ) : LibraryListState
}

class LibraryListStore(
    private val repository: LibraryRepository,
    private val access: StateFlow<LibraryAccess>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<LibraryListState>(LibraryListState.Idle)
    val state: StateFlow<LibraryListState> = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value == LibraryListState.Loading) return
        when (val currentAccess = access.value) {
            LibraryAccess.PairingRequired -> mutableState.value = LibraryListState.RePairingRequired
            LibraryAccess.CapabilityUnavailable -> mutableState.value = LibraryListState.CapabilityUnavailable
            is LibraryAccess.Available -> {
                mutableState.value = LibraryListState.Loading
                scope.launch {
                    mutableState.value =
                        when (val result = repository.recent()) {
                            is LibraryRepositoryResult.Success -> {
                                val items =
                                    result.value.items.map { item ->
                                        LibraryItemPresentation(
                                            analysisId = item.analysisId,
                                            documentId = item.documentId,
                                            title = item.title,
                                            readState = item.readState,
                                            snippet = item.snippet,
                                        )
                                    }
                                if (items.isEmpty()) {
                                    LibraryListState.Empty
                                } else {
                                    LibraryListState.Content(items, currentAccess.canReplaceReadState)
                                }
                            }
                            LibraryRepositoryResult.Unauthorized -> LibraryListState.RePairingRequired
                            LibraryRepositoryResult.NotFoundOrNotOwned -> LibraryListState.Failed(canRetry = false)
                            is LibraryRepositoryResult.Unavailable ->
                                if (result.retryable) LibraryListState.Offline else LibraryListState.Failed(canRetry = true)
                        }
                }
            }
        }
    }

    fun replaceReadState(
        analysisId: String,
        readState: ReadState,
    ) {
        val current = mutableState.value as? LibraryListState.Content ?: return
        val currentAccess = access.value as? LibraryAccess.Available ?: return
        if (!currentAccess.canReplaceReadState || current.mutatingAnalysisId != null) return
        if (current.items.none { it.analysisId == analysisId }) return
        mutableState.value = current.copy(mutatingAnalysisId = analysisId, mutationError = null)
        scope.launch {
            val confirmed = mutableState.value as? LibraryListState.Content ?: return@launch
            mutableState.value =
                when (val result = repository.replaceReadState(analysisId, readState)) {
                    is LibraryRepositoryResult.Success ->
                        confirmed.copy(
                            items =
                                confirmed.items.map { item ->
                                    if (item.analysisId == analysisId) item.copy(readState = result.value.readState) else item
                                },
                            mutatingAnalysisId = null,
                        )
                    LibraryRepositoryResult.Unauthorized -> LibraryListState.RePairingRequired
                    LibraryRepositoryResult.NotFoundOrNotOwned ->
                        confirmed.copy(mutatingAnalysisId = null, mutationError = LibraryMutationError.ItemUnavailable)
                    is LibraryRepositoryResult.Unavailable ->
                        confirmed.copy(
                            mutatingAnalysisId = null,
                            mutationError =
                                if (result.outcomeUnknown) {
                                    LibraryMutationError.OutcomeUnknown
                                } else {
                                    LibraryMutationError.Unavailable
                                },
                        )
                }
        }
    }
}
