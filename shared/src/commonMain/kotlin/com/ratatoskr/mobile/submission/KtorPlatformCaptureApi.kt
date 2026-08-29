package com.ratatoskr.mobile.submission

import com.ratatoskr.mobile.api.generated.model.CaptureAccepted
import com.ratatoskr.mobile.api.generated.model.SubmitCapture
import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class KtorPlatformCaptureApi(
    private val client: HttpClient,
    private val now: () -> Instant,
    private val json: Json = captureJson,
) : PlatformCaptureApi {
    override suspend fun submit(
        authorization: Authorization,
        url: String,
        idempotencyKey: String,
    ): PlatformCaptureResult {
        val response =
            try {
                client.post("${authorization.origin}/v1/captures") {
                    bearerAuth(authorization.accessToken)
                    header(IDEMPOTENCY_HEADER, idempotencyKey)
                    setBody(TextContent(json.encodeToString(SubmitCapture(url)), ContentType.Application.Json))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return PlatformCaptureResult.Retryable(CaptureTransportFailure.Connectivity)
            }
        return when (response.status) {
            HttpStatusCode.Accepted -> decodeAccepted(response.bodyAsText())
            HttpStatusCode.BadRequest -> permanent(CaptureTransportFailure.Validation)
            HttpStatusCode.Unauthorized -> PlatformCaptureResult.Unauthorized
            HttpStatusCode.Forbidden -> permanent(CaptureTransportFailure.Policy)
            HttpStatusCode.TooManyRequests ->
                PlatformCaptureResult.Retryable(
                    CaptureTransportFailure.RateLimited,
                    retryAt(response.headers[HttpHeaders.RetryAfter]),
                )
            HttpStatusCode.Conflict,
            HttpStatusCode.GatewayTimeout,
            -> PlatformCaptureResult.Retryable(CaptureTransportFailure.ServerUnavailable)
            else ->
                if (response.status.value >= 500) {
                    PlatformCaptureResult.Retryable(CaptureTransportFailure.ServerUnavailable)
                } else {
                    permanent(CaptureTransportFailure.InvalidResponse)
                }
        }
    }

    private fun decodeAccepted(body: String): PlatformCaptureResult {
        val accepted =
            try {
                json.decodeFromString<CaptureAccepted>(body)
            } catch (_: SerializationException) {
                return permanent(CaptureTransportFailure.InvalidResponse)
            }
        return if (accepted.status == ACCEPTED_MARKER && UUID_REGEX.matches(accepted.operationId)) {
            PlatformCaptureResult.Accepted(accepted.operationId)
        } else {
            permanent(CaptureTransportFailure.InvalidResponse)
        }
    }

    private fun retryAt(raw: String?): Instant? {
        val seconds = raw?.toLongOrNull()?.coerceIn(0, MAX_RETRY_SECONDS) ?: return null
        return now() + seconds.seconds
    }

    private fun permanent(failure: CaptureTransportFailure) = PlatformCaptureResult.Permanent(failure)

    private companion object {
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"
        const val ACCEPTED_MARKER = "accepted"
        const val MAX_RETRY_SECONDS = 86_400L
        val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val captureJson = Json { ignoreUnknownKeys = true }
    }
}
