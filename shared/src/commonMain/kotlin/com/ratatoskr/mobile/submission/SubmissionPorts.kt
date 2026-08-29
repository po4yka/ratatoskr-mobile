package com.ratatoskr.mobile.submission

import com.ratatoskr.mobile.identity.Authorization
import kotlin.time.Instant

fun interface SubmissionScheduler {
    fun schedule(nextEligibleAt: Instant?)
}

sealed interface PlatformCaptureResult {
    data class Accepted(
        val operationId: String,
    ) : PlatformCaptureResult

    data class Retryable(
        val failure: CaptureTransportFailure,
        val retryAt: Instant? = null,
    ) : PlatformCaptureResult

    data class Permanent(
        val failure: CaptureTransportFailure,
    ) : PlatformCaptureResult

    data object Unauthorized : PlatformCaptureResult
}

enum class CaptureTransportFailure {
    Connectivity,
    ServerUnavailable,
    RateLimited,
    Validation,
    Policy,
    InvalidResponse,
}

interface PlatformCaptureApi {
    suspend fun submit(
        authorization: Authorization,
        url: String,
        idempotencyKey: String,
    ): PlatformCaptureResult
}

interface AuthorizedRequestExecutor {
    suspend fun <T> execute(request: suspend (Authorization) -> AuthorizedResult<T>): AuthorizedResult<T>
}

sealed interface AuthorizedResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AuthorizedResult<T>

    data object Unauthorized : AuthorizedResult<Nothing>

    data class Failure(
        val retryable: Boolean,
    ) : AuthorizedResult<Nothing>
}
