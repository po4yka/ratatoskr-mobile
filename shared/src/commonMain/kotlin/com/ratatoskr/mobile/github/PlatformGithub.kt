package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.Authorization
import com.ratatoskr.mobile.submission.AuthorizedRequestExecutor
import com.ratatoskr.mobile.submission.AuthorizedResult
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException

data class GithubActionRequest(
    val mode: GithubActionMode,
    val target: GithubRepositoryTarget,
    val accountRef: String?,
    val confirmationEvidenceRef: String,
    val idempotencyKey: String,
)

sealed interface PlatformGithubResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PlatformGithubResult<T>

    data object Unauthorized : PlatformGithubResult<Nothing>

    data object InvalidResponse : PlatformGithubResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
    ) : PlatformGithubResult<Nothing>
}

interface PlatformGithubApi {
    suspend fun preview(
        authorization: Authorization,
        canonicalUrl: String,
    ): PlatformGithubResult<GithubRepositoryPreview>

    suspend fun action(
        authorization: Authorization,
        request: GithubActionRequest,
    ): PlatformGithubResult<GithubActionResult>
}

class KtorPlatformGithubApi(
    private val client: HttpClient,
) : PlatformGithubApi {
    override suspend fun preview(
        authorization: Authorization,
        canonicalUrl: String,
    ): PlatformGithubResult<GithubRepositoryPreview> {
        val body = GithubContractCodec.encodePreviewRequest(canonicalUrl) ?: return unavailable(retryable = false)
        val response =
            performRequest(outcomeUnknown = false) {
                client.post("${authorization.origin}/v1/gh/repositories/preview") {
                    bearerAuth(authorization.accessToken)
                    setBody(TextContent(body, ContentType.Application.Json))
                }
            }
        return when (response) {
            is RequestOutcome.Response -> decodePreview(response.value)
            is RequestOutcome.Failed -> unavailable(response.retryable, response.outcomeUnknown)
        }
    }

    override suspend fun action(
        authorization: Authorization,
        request: GithubActionRequest,
    ): PlatformGithubResult<GithubActionResult> {
        val body = GithubContractCodec.encodeActionRequest(request) ?: return unavailable(retryable = false)
        val response =
            performRequest(outcomeUnknown = true) {
                client.post("${authorization.origin}/v1/gh/repositories/actions") {
                    bearerAuth(authorization.accessToken)
                    setBody(TextContent(body, ContentType.Application.Json))
                }
            }
        return when (response) {
            is RequestOutcome.Response -> decodeAction(response.value)
            is RequestOutcome.Failed -> unavailable(response.retryable, response.outcomeUnknown)
        }
    }

    private suspend fun decodePreview(response: HttpResponse): PlatformGithubResult<GithubRepositoryPreview> {
        if (response.status != HttpStatusCode.OK) return response.failure()
        val body = boundedBody(response) ?: return PlatformGithubResult.InvalidResponse
        return GithubContractCodec.decodePreview(body)?.let { PlatformGithubResult.Success(it) }
            ?: PlatformGithubResult.InvalidResponse
    }

    private suspend fun decodeAction(response: HttpResponse): PlatformGithubResult<GithubActionResult> {
        if (response.status != HttpStatusCode.OK) return response.failure()
        val body = boundedBody(response) ?: return PlatformGithubResult.InvalidResponse
        return GithubContractCodec.decodeActionResult(body)?.let { PlatformGithubResult.Success(it) }
            ?: PlatformGithubResult.InvalidResponse
    }

    private suspend fun boundedBody(response: HttpResponse): String? {
        val body = response.bodyAsText()
        return body.takeIf { it.length <= MAX_RESPONSE_CHARS }
    }

    private suspend fun performRequest(
        outcomeUnknown: Boolean,
        block: suspend () -> HttpResponse,
    ): RequestOutcome =
        try {
            RequestOutcome.Response(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            RequestOutcome.Failed(retryable = true, outcomeUnknown = outcomeUnknown)
        }

    private fun <T> HttpResponse.failure(): PlatformGithubResult<T> =
        when {
            status == HttpStatusCode.Unauthorized -> PlatformGithubResult.Unauthorized
            status == HttpStatusCode.TooManyRequests || status.value == 503 || status.value == 504 ->
                unavailable(retryable = true)
            else -> unavailable(retryable = false)
        }

    private fun <T> unavailable(
        retryable: Boolean,
        outcomeUnknown: Boolean = false,
    ): PlatformGithubResult<T> = PlatformGithubResult.Unavailable(retryable, outcomeUnknown)

    private sealed interface RequestOutcome {
        data class Response(
            val value: HttpResponse,
        ) : RequestOutcome

        data class Failed(
            val retryable: Boolean,
            val outcomeUnknown: Boolean,
        ) : RequestOutcome
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64 * 1024
    }
}

sealed interface GithubRepositoryResult<out T> {
    data class Success<T>(
        val value: T,
    ) : GithubRepositoryResult<T>

    data object Unauthorized : GithubRepositoryResult<Nothing>

    data object InvalidResponse : GithubRepositoryResult<Nothing>

    data class Unavailable(
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
    ) : GithubRepositoryResult<Nothing>
}

interface GithubRepository {
    suspend fun preview(canonicalUrl: String): GithubRepositoryResult<GithubRepositoryPreview>

    suspend fun action(request: GithubActionRequest): GithubRepositoryResult<GithubActionResult>
}

class AuthorizedGithubRepository(
    private val api: PlatformGithubApi,
    private val authorizedRequests: AuthorizedRequestExecutor,
) : GithubRepository {
    override suspend fun preview(canonicalUrl: String): GithubRepositoryResult<GithubRepositoryPreview> =
        execute { authorization -> api.preview(authorization, canonicalUrl) }

    override suspend fun action(request: GithubActionRequest): GithubRepositoryResult<GithubActionResult> =
        execute { authorization -> api.action(authorization, request) }

    private suspend fun <T> execute(request: suspend (Authorization) -> PlatformGithubResult<T>): GithubRepositoryResult<T> =
        when (
            val authorized =
                authorizedRequests.execute { authorization ->
                    when (val response = request(authorization)) {
                        PlatformGithubResult.Unauthorized -> AuthorizedResult.Unauthorized
                        else -> AuthorizedResult.Success(response)
                    }
                }
        ) {
            AuthorizedResult.Unauthorized -> GithubRepositoryResult.Unauthorized
            is AuthorizedResult.Failure -> GithubRepositoryResult.Unavailable(authorized.retryable)
            is AuthorizedResult.Success -> authorized.value.toRepositoryResult()
        }

    private fun <T> PlatformGithubResult<T>.toRepositoryResult(): GithubRepositoryResult<T> =
        when (this) {
            is PlatformGithubResult.Success -> GithubRepositoryResult.Success(value)
            PlatformGithubResult.Unauthorized -> GithubRepositoryResult.Unauthorized
            PlatformGithubResult.InvalidResponse -> GithubRepositoryResult.InvalidResponse
            is PlatformGithubResult.Unavailable -> GithubRepositoryResult.Unavailable(retryable, outcomeUnknown)
        }
}
