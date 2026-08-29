package com.ratatoskr.mobile.operation

import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.api.generated.model.OperationSummary
import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class KtorPlatformOperationsApi(
    private val client: HttpClient,
    private val json: Json = operationJson,
) : PlatformOperationsApi {
    override suspend fun list(
        authorization: Authorization,
        cursor: String?,
    ): PlatformOperationsResult<OperationList> {
        val response =
            request {
                client.get("${authorization.origin}/v1/operations") {
                    bearerAuth(authorization.accessToken)
                    cursor?.let { parameter("cursor", it) }
                }
            } ?: return unavailable(retryable = true)
        if (response.status != HttpStatusCode.OK) return response.failure(nonEnumerating = false)
        val list = decode<OperationList>(response) ?: return unavailable(retryable = false)
        return if (list.operations.all { it.isValid() }) {
            PlatformOperationsResult.Success(list)
        } else {
            unavailable(retryable = false)
        }
    }

    override suspend fun read(
        authorization: Authorization,
        operationId: String,
    ): PlatformOperationsResult<OperationSnapshot> {
        if (!UUID_REGEX.matches(operationId)) return unavailable(retryable = false)
        val response =
            request {
                client.get("${authorization.origin}/v1/operations/$operationId") {
                    bearerAuth(authorization.accessToken)
                }
            } ?: return unavailable(retryable = true)
        if (response.status != HttpStatusCode.OK) return response.failure(nonEnumerating = true)
        val snapshot = decode<OperationSnapshot>(response) ?: return unavailable(retryable = false)
        return if (snapshot.operationId == operationId && snapshot.isValid()) {
            PlatformOperationsResult.Success(snapshot)
        } else {
            unavailable(retryable = false)
        }
    }

    private suspend fun request(block: suspend () -> HttpResponse): HttpResponse? =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

    private suspend inline fun <reified T> decode(response: HttpResponse): T? =
        try {
            json.decodeFromString<T>(response.bodyAsText())
        } catch (_: SerializationException) {
            null
        }

    private fun <T> HttpResponse.failure(nonEnumerating: Boolean): PlatformOperationsResult<T> =
        when {
            status == HttpStatusCode.Unauthorized -> PlatformOperationsResult.Unauthorized
            nonEnumerating && (status == HttpStatusCode.NotFound || status == HttpStatusCode.Forbidden) ->
                PlatformOperationsResult.NotFoundOrNotOwned
            status == HttpStatusCode.TooManyRequests || status.value >= 500 -> unavailable(retryable = true)
            else -> unavailable(retryable = false)
        }

    private fun <T> unavailable(retryable: Boolean): PlatformOperationsResult<T> = PlatformOperationsResult.Unavailable(retryable)

    private fun OperationSummary.isValid(): Boolean =
        UUID_REGEX.matches(operationId) &&
            statusChangedAt >= acceptedAt &&
            (terminatedAt != null) == status.isTerminal() &&
            progressPercent?.let { it in 1..100 } != false

    private fun OperationSnapshot.isValid(): Boolean {
        val terminal = status.isTerminal()
        if (statusChangedAt < acceptedAt || (terminatedAt != null) != terminal) return false
        if (terminatedAt?.let { it < acceptedAt } == true) return false
        if (progressPercent?.let { it !in 1..100 } == true) return false
        val errors = errors.orEmpty()
        val warnings = warnings.orEmpty()
        if (status == OperationStatus.FAILED && errors.isEmpty()) return false
        if (status == OperationStatus.SUCCEEDED && errors.isNotEmpty()) return false
        if (status == OperationStatus.PARTIALLY_SUCCEEDED && errors.isEmpty() && warnings.isEmpty()) return false
        return true
    }

    private fun OperationStatus.isTerminal(): Boolean =
        this == OperationStatus.SUCCEEDED ||
            this == OperationStatus.PARTIALLY_SUCCEEDED ||
            this == OperationStatus.FAILED ||
            this == OperationStatus.CANCELLED

    private companion object {
        val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val operationJson = Json { ignoreUnknownKeys = true }
    }
}
