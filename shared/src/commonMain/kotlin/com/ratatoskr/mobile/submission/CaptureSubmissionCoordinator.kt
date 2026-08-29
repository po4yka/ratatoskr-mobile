package com.ratatoskr.mobile.submission

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.identity.DeviceSessionManager
import com.ratatoskr.mobile.identity.IdentityResult
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueFailure
import com.ratatoskr.mobile.queue.QueueResult
import com.ratatoskr.mobile.queue.QueueState
import kotlin.time.Duration.Companion.minutes

enum class SubmissionDrainResult {
    NoWork,
    Accepted,
    RetryScheduled,
    PermanentFailure,
    AuthRequired,
}

class CaptureSubmissionCoordinator(
    private val queue: CaptureQueue,
    private val api: PlatformCaptureApi,
    private val authorizedRequests: AuthorizedRequestExecutor,
) {
    suspend fun drainOne(owner: CaptureOwner): SubmissionDrainResult {
        val claim = queue.claimReady(owner, CLAIM_LEASE) ?: return SubmissionDrainResult.NoWork
        val url = (claim.record.request.payload as? CapturePayload.Url)?.value
        if (url == null) {
            queue.recordFailure(claim.record.localId, claim.token, QueueFailure.Validation)
            return SubmissionDrainResult.PermanentFailure
        }
        val result =
            authorizedRequests.execute { authorization ->
                when (val response = api.submit(authorization, url, claim.record.idempotencyKey)) {
                    PlatformCaptureResult.Unauthorized -> AuthorizedResult.Unauthorized
                    else -> AuthorizedResult.Success(response)
                }
            }
        return when (result) {
            is AuthorizedResult.Success -> persist(claim.record.localId, claim.token, result.value)
            AuthorizedResult.Unauthorized -> {
                queue.recordFailure(claim.record.localId, claim.token, QueueFailure.Authentication)
                SubmissionDrainResult.AuthRequired
            }
            is AuthorizedResult.Failure -> {
                val failure =
                    if (result.retryable) QueueFailure.Connectivity else QueueFailure.InvalidResponse
                val updated = queue.recordFailure(claim.record.localId, claim.token, failure)
                if (updated is QueueResult.Success && updated.value.state == QueueState.RetryWait) {
                    SubmissionDrainResult.RetryScheduled
                } else {
                    SubmissionDrainResult.PermanentFailure
                }
            }
        }
    }

    private suspend fun persist(
        localId: String,
        claimToken: String,
        result: PlatformCaptureResult,
    ): SubmissionDrainResult =
        when (result) {
            is PlatformCaptureResult.Accepted -> {
                queue.recordAccepted(localId, result.operationId)
                SubmissionDrainResult.Accepted
            }
            is PlatformCaptureResult.Retryable -> {
                queue.recordFailure(localId, claimToken, result.failure.queueFailure(), result.retryAt)
                SubmissionDrainResult.RetryScheduled
            }
            is PlatformCaptureResult.Permanent -> {
                queue.recordFailure(localId, claimToken, result.failure.queueFailure())
                SubmissionDrainResult.PermanentFailure
            }
            PlatformCaptureResult.Unauthorized -> {
                queue.recordFailure(localId, claimToken, QueueFailure.Authentication)
                SubmissionDrainResult.AuthRequired
            }
        }

    private fun CaptureTransportFailure.queueFailure(): QueueFailure =
        when (this) {
            CaptureTransportFailure.Connectivity -> QueueFailure.Connectivity
            CaptureTransportFailure.ServerUnavailable -> QueueFailure.ServerUnavailable
            CaptureTransportFailure.RateLimited -> QueueFailure.RateLimited
            CaptureTransportFailure.Validation -> QueueFailure.Validation
            CaptureTransportFailure.Policy -> QueueFailure.Policy
            CaptureTransportFailure.InvalidResponse -> QueueFailure.InvalidResponse
        }

    private companion object {
        val CLAIM_LEASE = 2.minutes
    }
}

class DeviceAuthorizedRequestExecutor(
    private val sessions: DeviceSessionManager,
) : AuthorizedRequestExecutor {
    override suspend fun <T> execute(
        request: suspend (com.ratatoskr.mobile.identity.Authorization) -> AuthorizedResult<T>,
    ): AuthorizedResult<T> {
        val first = sessions.currentAuthorization()
        if (first !is IdentityResult.Success) return AuthorizedResult.Unauthorized
        return when (val result = request(first.value)) {
            AuthorizedResult.Unauthorized -> {
                when (val refreshed = sessions.refreshSession()) {
                    is IdentityResult.Success -> request(refreshed.value)
                    is IdentityResult.Failure -> AuthorizedResult.Unauthorized
                }
            }
            else -> result
        }
    }
}
