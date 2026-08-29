package com.ratatoskr.mobile.operation

import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.identity.Authorization

sealed interface PlatformOperationsResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PlatformOperationsResult<T>

    data object Unauthorized : PlatformOperationsResult<Nothing>

    data object NotFoundOrNotOwned : PlatformOperationsResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
    ) : PlatformOperationsResult<Nothing>
}

interface PlatformOperationsApi {
    suspend fun list(
        authorization: Authorization,
        cursor: String? = null,
    ): PlatformOperationsResult<OperationList>

    suspend fun read(
        authorization: Authorization,
        operationId: String,
    ): PlatformOperationsResult<OperationSnapshot>
}
