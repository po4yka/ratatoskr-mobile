package com.ratatoskr.mobile.library

import com.ratatoskr.mobile.api.generated.model.LibraryPage
import com.ratatoskr.mobile.api.generated.model.ReadState
import com.ratatoskr.mobile.api.generated.model.ReadStateResource
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

class PlatformLibraryApiTest {
    @Test
    fun blank_query_uses_bounded_library_path() =
        runTest {
            var request: HttpRequestData? = null

            val result = api(HttpStatusCode.OK, PAGE) { request = it }.recent(AUTHORIZATION, limit = 25)

            val page = assertIs<PlatformLibraryResult.Success<LibraryPage>>(result).value
            assertEquals(listOf(ANALYSIS_2, ANALYSIS_1), page.items.map { it.analysisId })
            assertEquals("https://platform.example.test/v1/library/search?limit=25&offset=0", request?.url.toString())
            assertEquals("Bearer access-token", request?.headers?.get(HttpHeaders.Authorization))
            assertEquals(HttpMethod.Get, request?.method)
        }

    @Test
    fun read_state_put_uses_exact_generated_body() =
        runTest {
            var request: HttpRequestData? = null

            val result =
                api(HttpStatusCode.OK, """{"read_state":"read"}""") { request = it }
                    .replaceReadState(AUTHORIZATION, ANALYSIS_1, ReadState.READ)

            assertEquals(ReadState.READ, assertIs<PlatformLibraryResult.Success<ReadStateResource>>(result).value.readState)
            assertEquals(
                "https://platform.example.test/v1/library/items/$ANALYSIS_1/read-state",
                request?.url.toString(),
            )
            assertEquals(HttpMethod.Put, request?.method)
            assertEquals("""{"read_state":"read"}""", (request?.body as TextContent).text)
        }

    @Test
    fun authorization_and_unavailable_responses_are_distinct() =
        runTest {
            assertEquals(PlatformLibraryResult.Unauthorized, api(HttpStatusCode.Unauthorized).recent(AUTHORIZATION))
            assertEquals(
                PlatformLibraryResult.Unavailable(retryable = true),
                api(HttpStatusCode.ServiceUnavailable).recent(AUTHORIZATION),
            )
            assertEquals(
                PlatformLibraryResult.NotFoundOrNotOwned,
                api(HttpStatusCode.NotFound).replaceReadState(AUTHORIZATION, ANALYSIS_1, ReadState.READ),
            )
        }

    @Test
    fun redirect_is_refused() =
        runTest {
            var requests = 0
            val result = api(HttpStatusCode.Found) { requests += 1 }.recent(AUTHORIZATION)

            assertEquals(PlatformLibraryResult.Unavailable(retryable = false), result)
            assertEquals(1, requests)
        }

    private fun api(
        status: HttpStatusCode,
        body: String = "{}",
        observe: (HttpRequestData) -> Unit = {},
    ): PlatformLibraryApi =
        KtorPlatformLibraryApi(
            HttpClient(
                MockEngine { request ->
                    observe(request)
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                followRedirects = false
            },
        )

    private companion object {
        const val ANALYSIS_1 = "00000000-0000-4000-8000-000000000001"
        const val ANALYSIS_2 = "00000000-0000-4000-8000-000000000002"
        val AUTHORIZATION = Authorization("https://platform.example.test", "access-token")
        const val PAGE =
            """{"items":[{"analysis_id":"$ANALYSIS_2","document_id":"00000000-0000-4000-8000-000000000012","title":"Second","read_state":"unread"},{"analysis_id":"$ANALYSIS_1","document_id":"00000000-0000-4000-8000-000000000011","title":"First","read_state":"read"}],"limit":25,"offset":0,"has_more":false}"""
    }
}
