package com.ratatoskr.mobile.submission

import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlatformCaptureApiTest {
    @Test
    fun submit_sends_generated_body_and_persisted_key() =
        runTest {
            var body = ""
            var bearer = ""
            var key = ""
            val api =
                apiResponding(HttpStatusCode.Accepted, ACCEPTED) { request ->
                    body = (request.body as TextContent).text
                    bearer = request.headers[HttpHeaders.Authorization].orEmpty()
                    key = request.headers["Idempotency-Key"].orEmpty()
                }

            api.submit(AUTHORIZATION, "https://example.test/article", "persisted-key")

            assertEquals("""{"url":"https://example.test/article"}""", body)
            assertEquals("Bearer access-token", bearer)
            assertEquals("persisted-key", key)
        }

    @Test
    fun accepted_fixture_decodes() =
        runTest {
            val result = apiResponding(HttpStatusCode.Accepted, ACCEPTED).submit(AUTHORIZATION, URL, "key")

            assertEquals(PlatformCaptureResult.Accepted(OPERATION_ID), result)
        }

    @Test
    fun retryable_statuses_and_retry_hint_are_classified() =
        runTest {
            val rateLimited =
                apiResponding(
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "120"),
                ).submit(AUTHORIZATION, URL, "key")
            assertEquals(
                PlatformCaptureResult.Retryable(CaptureTransportFailure.RateLimited, NOW + 120.seconds),
                rateLimited,
            )
            assertEquals(
                PlatformCaptureResult.Retryable(CaptureTransportFailure.ServerUnavailable),
                apiResponding(HttpStatusCode.GatewayTimeout).submit(AUTHORIZATION, URL, "key"),
            )
            assertEquals(
                PlatformCaptureResult.Retryable(CaptureTransportFailure.ServerUnavailable),
                apiResponding(HttpStatusCode.Conflict).submit(AUTHORIZATION, URL, "key"),
            )
        }

    @Test
    fun permanent_validation_is_classified() =
        runTest {
            assertEquals(
                PlatformCaptureResult.Permanent(CaptureTransportFailure.Validation),
                apiResponding(HttpStatusCode.BadRequest).submit(AUTHORIZATION, URL, "key"),
            )
        }

    @Test
    fun invalid_success_fails_closed() =
        runTest {
            val wrongMarker =
                """{"operation_id":"$OPERATION_ID","status":"completed"}"""
            assertEquals(
                PlatformCaptureResult.Permanent(CaptureTransportFailure.InvalidResponse),
                apiResponding(HttpStatusCode.Accepted, wrongMarker).submit(AUTHORIZATION, URL, "key"),
            )
            assertIs<PlatformCaptureResult.Permanent>(
                apiResponding(HttpStatusCode.Accepted, "{}").submit(AUTHORIZATION, URL, "key"),
            )
        }

    private fun apiResponding(
        status: HttpStatusCode,
        body: String = "{}",
        headers: io.ktor.http.Headers = headersOf(HttpHeaders.ContentType, "application/json"),
        observe: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): PlatformCaptureApi =
        KtorPlatformCaptureApi(
            client =
                HttpClient(
                    MockEngine { request ->
                        observe(request)
                        respond(content = body, status = status, headers = headers)
                    },
                ),
            now = { NOW },
        )

    private companion object {
        const val URL = "https://example.test/article"
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
        val NOW = Instant.parse("2026-08-29T00:00:00Z")
        val AUTHORIZATION = Authorization("https://platform.example.test", "access-token")
        const val ACCEPTED =
            """{"operation_id":"0198f4b0-8f9a-7000-8000-000000000001","status":"accepted"}"""
    }
}
