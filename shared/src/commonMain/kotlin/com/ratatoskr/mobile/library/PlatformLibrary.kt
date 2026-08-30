package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
import com.ratatoskr.mobile.api.generated.model.ReplaceReadState
import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface PlatformLibraryResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PlatformLibraryResult<T>

    data object Unauthorized : PlatformLibraryResult<Nothing>

    data object NotFoundOrNotOwned : PlatformLibraryResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
    ) : PlatformLibraryResult<Nothing>
}

interface PlatformLibraryApi {
    suspend fun recent(
        authorization: Authorization,
        limit: Int = 25,
        offset: Int = 0,
    ): PlatformLibraryResult<LibraryPage>

    suspend fun search(
        authorization: Authorization,
        query: String,
        limit: Int = 25,
        offset: Int = 0,
    ): PlatformLibraryResult<LibraryPage> = PlatformLibraryResult.Unavailable(retryable = false)

    suspend fun replaceReadState(
        authorization: Authorization,
        analysisId: String,
        readState: ReadState,
    ): PlatformLibraryResult<ReadStateResource>
}

class KtorPlatformLibraryApi(
    private val client: HttpClient,
    private val json: Json = libraryJson,
) : PlatformLibraryApi {
    override suspend fun search(
        authorization: Authorization,
        query: String,
        limit: Int,
        offset: Int,
    ): PlatformLibraryResult<LibraryPage> {
        val normalized = query.trim()
        if (normalized.isEmpty() || normalized.scalarCount() > 512 || limit !in 1..100 || offset < 0) {
            return unavailable(retryable = false)
        }
        return pageRequest(authorization, normalized, limit, offset)
    }

    override suspend fun recent(
        authorization: Authorization,
        limit: Int,
        offset: Int,
    ): PlatformLibraryResult<LibraryPage> {
        if (limit !in 1..100 || offset < 0) return unavailable(retryable = false)
        return pageRequest(authorization, query = null, limit, offset)
    }

    private suspend fun pageRequest(
        authorization: Authorization,
        query: String?,
        limit: Int,
        offset: Int,
    ): PlatformLibraryResult<LibraryPage> {
        val response =
            request {
                client.get("${authorization.origin}/v1/library/search") {
                    bearerAuth(authorization.accessToken)
                    query?.let { parameter("q", it) }
                    parameter("limit", limit)
                    parameter("offset", offset)
                }
            } ?: return unavailable(retryable = true)
        if (response.status != HttpStatusCode.OK) return response.failure(nonEnumerating = false)
        val page = decode<LibraryPage>(response) ?: return unavailable(retryable = false)
        return if (page.limit == limit &&
            page.offset == offset &&
            page.items.all { item ->
                UUID_REGEX.matches(item.analysisId) &&
                    UUID_REGEX.matches(item.documentId) &&
                    item.title.scalarCount() <= 256 &&
                    item.snippet?.scalarCount()?.let { it <= 512 } != false &&
                    item.score?.let { it.isFinite() && it > 0f } != false
            }
        ) {
            PlatformLibraryResult.Success(page)
        } else {
            unavailable(retryable = false)
        }
    }

    override suspend fun replaceReadState(
        authorization: Authorization,
        analysisId: String,
        readState: ReadState,
    ): PlatformLibraryResult<ReadStateResource> {
        if (!UUID_REGEX.matches(analysisId)) return unavailable(retryable = false)
        val response =
            request {
                client.put("${authorization.origin}/v1/library/items/$analysisId/read-state") {
                    bearerAuth(authorization.accessToken)
                    setBody(TextContent(json.encodeToString(ReplaceReadState(readState)), ContentType.Application.Json))
                }
            } ?: return unavailable(retryable = true, outcomeUnknown = true)
        if (response.status != HttpStatusCode.OK) return response.failure(nonEnumerating = true)
        val resource = decode<ReadStateResource>(response) ?: return unavailable(retryable = false)
        return PlatformLibraryResult.Success(resource)
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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SerializationException) {
            null
        } catch (_: Throwable) {
            null
        }

    private fun <T> HttpResponse.failure(nonEnumerating: Boolean): PlatformLibraryResult<T> =
        when {
            status == HttpStatusCode.Unauthorized -> PlatformLibraryResult.Unauthorized
            nonEnumerating && (status == HttpStatusCode.NotFound || status == HttpStatusCode.Forbidden) ->
                PlatformLibraryResult.NotFoundOrNotOwned
            status == HttpStatusCode.TooManyRequests || status.value == 503 || status.value == 504 ->
                unavailable(retryable = true)
            else -> unavailable(retryable = false)
        }

    private fun <T> unavailable(
        retryable: Boolean,
        outcomeUnknown: Boolean = false,
    ): PlatformLibraryResult<T> = PlatformLibraryResult.Unavailable(retryable, outcomeUnknown)

    private companion object {
        val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val libraryJson = Json { ignoreUnknownKeys = true }
    }
}

internal fun String.scalarCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        val first = this[index]
        index +=
            if (first.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
                2
            } else {
                1
            }
        count += 1
    }
    return count
}
