package com.ratatoskr.mobile.operation

import com.ratatoskr.mobile.api.generated.model.OperationList
import com.ratatoskr.mobile.api.generated.model.OperationSnapshot
import com.ratatoskr.mobile.api.generated.model.OperationStatus
import com.ratatoskr.mobile.identity.Authorization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlatformOperationsApiTest {
    @Test
    fun operation_list_fixture_preserves_platform_order() =
        runTest {
            var requestedUrl = ""
            val result = api(HttpStatusCode.OK, LIST_FIXTURE) { requestedUrl = it }.list(AUTHORIZATION, "opaque+/=")

            val list = assertIs<PlatformOperationsResult.Success<OperationList>>(result).value
            assertEquals(listOf(OPERATION_2, OPERATION_1), list.operations.map { it.operationId })
            assertEquals("next-page", list.nextCursor)
            assertEquals(
                "https://platform.example.test/v1/operations?cursor=opaque%2B%2F%3D",
                requestedUrl,
            )
        }

    @Test
    fun detail_fixtures_decode_all_terminal_outcomes() =
        runTest {
            val fixtures =
                listOf(
                    terminal("succeeded"),
                    terminal("partially_succeeded", "\"warnings\":[{\"code\":\"partial\",\"message\":\"One step was unavailable\"}],"),
                    terminal("failed", "\"errors\":[{\"code\":\"capture.failed\",\"message\":\"Capture failed\",\"retryable\":false}],"),
                    terminal("cancelled"),
                )

            val statuses =
                fixtures.map { fixture ->
                    val result = api(HttpStatusCode.OK, fixture).read(AUTHORIZATION, OPERATION_1)
                    assertIs<PlatformOperationsResult.Success<OperationSnapshot>>(result).value.status
                }

            assertEquals(
                listOf(
                    OperationStatus.SUCCEEDED,
                    OperationStatus.PARTIALLY_SUCCEEDED,
                    OperationStatus.FAILED,
                    OperationStatus.CANCELLED,
                ),
                statuses,
            )
        }

    @Test
    fun not_found_is_non_enumerating() =
        runTest {
            assertEquals(
                PlatformOperationsResult.NotFoundOrNotOwned,
                api(HttpStatusCode.NotFound).read(AUTHORIZATION, OPERATION_1),
            )
            assertEquals(
                PlatformOperationsResult.NotFoundOrNotOwned,
                api(HttpStatusCode.Forbidden).read(AUTHORIZATION, OPERATION_1),
            )
        }

    @Test
    fun invalid_snapshot_fails_closed() =
        runTest {
            val invalid =
                """{"accepted_at":"2026-08-29T00:00:00Z","correlation_id":"operation:$OPERATION_1","kind":"capture","operation_id":"$OPERATION_1","retryable":false,"status":"running","status_changed_at":"2026-08-29T00:01:00Z","terminated_at":"2026-08-29T00:01:00Z"}"""

            assertEquals(
                PlatformOperationsResult.Unavailable(retryable = false),
                api(HttpStatusCode.OK, invalid).read(AUTHORIZATION, OPERATION_1),
            )
            assertEquals(
                PlatformOperationsResult.Unavailable(retryable = false),
                api(HttpStatusCode.OK, "{}").list(AUTHORIZATION),
            )
        }

    private fun api(
        status: HttpStatusCode,
        body: String = "{}",
        observeUrl: (String) -> Unit = {},
    ): PlatformOperationsApi =
        KtorPlatformOperationsApi(
            HttpClient(
                MockEngine { request ->
                    observeUrl(request.url.toString())
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ),
        )

    private fun terminal(
        status: String,
        extra: String = "",
    ) =
        """{"accepted_at":"2026-08-29T00:00:00Z","correlation_id":"operation:$OPERATION_1",$extra"kind":"capture","operation_id":"$OPERATION_1","retryable":false,"status":"$status","status_changed_at":"2026-08-29T00:01:00Z","terminated_at":"2026-08-29T00:01:00Z"}"""

    private companion object {
        const val OPERATION_1 = "0198f4b0-8f9a-7000-8000-000000000001"
        const val OPERATION_2 = "0198f4b0-8f9a-7000-8000-000000000002"
        val AUTHORIZATION = Authorization("https://platform.example.test", "access-token")
        const val LIST_FIXTURE =
            """{"operations":[{"accepted_at":"2026-08-29T00:02:00Z","correlation_id":"operation:$OPERATION_2","kind":"capture","operation_id":"$OPERATION_2","retryable":false,"status":"running","status_changed_at":"2026-08-29T00:03:00Z","progress_percent":40,"stage":"extracting"},{"accepted_at":"2026-08-29T00:00:00Z","correlation_id":"operation:$OPERATION_1","kind":"capture","operation_id":"$OPERATION_1","retryable":false,"status":"succeeded","status_changed_at":"2026-08-29T00:01:00Z","terminated_at":"2026-08-29T00:01:00Z"}],"next_cursor":"next-page"}"""
    }
}
