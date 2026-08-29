package com.ratatoskr.mobile.github

import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlatformGithubApiTest {
    @Test
    fun preview_posts_canonical_contract_body_to_platform() =
        runTest {
            var request: HttpRequestData? = null

            val result = api(HttpStatusCode.OK, PREVIEW) { request = it }.preview(AUTHORIZATION, REPOSITORY_URL)

            assertIs<PlatformGithubResult.Success<GithubRepositoryPreview>>(result)
            assertEquals("https://platform.example.test/v1/gh/repositories/preview", request?.url.toString())
            assertEquals(HttpMethod.Post, request?.method)
            assertEquals("Bearer access-token", request?.headers?.get(HttpHeaders.Authorization))
            assertEquals("""{"repository_url":"$REPOSITORY_URL"}""", (request?.body as TextContent).text)
        }

    @Test
    fun action_posts_exact_target_confirmation_and_idempotency() =
        runTest {
            var request: HttpRequestData? = null

            val result = api(HttpStatusCode.OK, PARTIAL_ACTION) { request = it }.action(AUTHORIZATION, ACTION)

            assertIs<PlatformGithubResult.Success<GithubActionResult>>(result)
            assertEquals("https://platform.example.test/v1/gh/repositories/actions", request?.url.toString())
            assertEquals(HttpMethod.Post, request?.method)
            assertEquals(ACTION_BODY, (request?.body as TextContent).text)
        }

    @Test
    fun redirect_and_invalid_contract_responses_fail_closed() =
        runTest {
            assertEquals(
                PlatformGithubResult.Unavailable(retryable = false),
                api(HttpStatusCode.Found).preview(AUTHORIZATION, REPOSITORY_URL),
            )
            assertEquals(
                PlatformGithubResult.InvalidResponse,
                api(HttpStatusCode.OK, "{}").action(AUTHORIZATION, ACTION),
            )
        }

    @Test
    fun action_disconnect_after_send_is_outcome_unknown() =
        runTest {
            val api =
                KtorPlatformGithubApi(
                    HttpClient(MockEngine { throw IllegalStateException("synthetic disconnect") }) {
                        followRedirects = false
                    },
                )

            assertEquals(
                PlatformGithubResult.Unavailable(retryable = true, outcomeUnknown = true),
                api.action(AUTHORIZATION, ACTION),
            )
        }

    @Test
    fun unauthorized_recovery_and_revocation_are_distinct() =
        runTest {
            assertEquals(
                PlatformGithubResult.Unauthorized,
                api(HttpStatusCode.Unauthorized).preview(AUTHORIZATION, REPOSITORY_URL),
            )
            assertEquals(
                PlatformGithubResult.Unavailable(retryable = true),
                api(HttpStatusCode.ServiceUnavailable).preview(AUTHORIZATION, REPOSITORY_URL),
            )
        }

    private fun api(
        status: HttpStatusCode,
        body: String = "{}",
        observe: (HttpRequestData) -> Unit = {},
    ): PlatformGithubApi =
        KtorPlatformGithubApi(
            HttpClient(
                MockEngine { request ->
                    observe(request)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                followRedirects = false
            },
        )

    private companion object {
        const val REPOSITORY_URL = "https://github.com/owner/repository"
        val AUTHORIZATION = Authorization("https://platform.example.test", "access-token")
        val TARGET = GithubRepositoryTarget(42, "owner/repository", REPOSITORY_URL)
        val ACTION =
            GithubActionRequest(
                mode = GithubActionMode.Star,
                target = TARGET,
                accountRef = "github-account:018f0000-0000-7000-8000-000000000604",
                confirmationEvidenceRef = "mobile-confirmation:018f0000-0000-7000-8000-000000000605",
                idempotencyKey = "mobile-github-action.018f0000-0000-7000-8000-000000000606",
            )
        const val PREVIEW =
            """{"target":{"github_repository_numeric_id":42,"repository_full_name":"owner/repository","canonical_url":"$REPOSITORY_URL"},"description":"A small repository description.","stargazer_count":123,"primary_language":"Rust","account_ref":"github-account:018f0000-0000-7000-8000-000000000604","available_actions":["metadata","track","star"]}"""
        const val PARTIAL_ACTION =
            """{"aggregate":"partial","metadata":{"status":"succeeded"},"provider_star":{"status":"succeeded"},"desired_backup":{"status":"failed","reason":"dependency_unavailable"}}"""
        const val ACTION_BODY =
            """{"mode":"star","target":{"github_repository_numeric_id":42,"repository_full_name":"owner/repository","canonical_url":"$REPOSITORY_URL"},"account_ref":"github-account:018f0000-0000-7000-8000-000000000604","confirmation_evidence_ref":"mobile-confirmation:018f0000-0000-7000-8000-000000000605","idempotency_key":"mobile-github-action.018f0000-0000-7000-8000-000000000606"}"""
    }
}
