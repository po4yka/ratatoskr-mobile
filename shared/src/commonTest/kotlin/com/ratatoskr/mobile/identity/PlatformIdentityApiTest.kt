package com.ratatoskr.mobile.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlatformIdentityApiTest {
    @Test
    fun pairing_handshake_matrix_maps_contract_outcomes() =
        runTest {
            var requestedUrl = ""
            var requestedBody = ""
            val success =
                apiResponding(
                    HttpStatusCode.Created,
                    """{"device_id":"device-1","user_id":"user-1","device_secret":"root-secret","credential":"access-1","expires_at":"2026-08-28T11:00:00Z","refresh_token":"refresh-1","refresh_expires_at":"2026-09-28T11:00:00Z"}""",
                    observeRequest = { url, body ->
                        requestedUrl = url
                        requestedBody = body
                    },
                ).pair("https://platform.example", "approved-code", "Pixel")
            val credentials = assertIs<IdentityResult.Success<DeviceCredentials>>(success).value
            assertEquals("device-1", credentials.deviceId)
            assertEquals("access-1", credentials.accessToken)
            assertEquals("https://platform.example/v1/devices/pair", requestedUrl)
            assertEquals(
                """{"code":"approved-code","kind":"mobile","display_name":"Pixel"}""",
                requestedBody,
            )

            assertEquals(
                IdentityResult.Failure(IdentityFailure.Validation),
                apiResponding(HttpStatusCode.BadRequest).pair("https://platform.example", "code", null),
            )
            assertEquals(
                IdentityResult.Failure(IdentityFailure.PairingRefused),
                apiResponding(HttpStatusCode.Unauthorized).pair("https://platform.example", "code", null),
            )
            assertEquals(
                IdentityResult.Failure(IdentityFailure.Unavailable(retryable = true)),
                apiResponding(HttpStatusCode.GatewayTimeout).pair("https://platform.example", "code", null),
            )
        }

    @Test
    fun transport_rejects_non_https_origin_without_request() =
        runTest {
            var requests = 0
            val api =
                KtorPlatformIdentityApi(
                    HttpClient(
                        MockEngine {
                            requests += 1
                            respond("{}")
                        },
                    ),
                )

            assertEquals(
                IdentityResult.Failure(IdentityFailure.InvalidOrigin),
                api.pair("http://platform.example", "must-not-leave-device", null),
            )
            assertEquals(0, requests)
        }

    private fun apiResponding(
        status: HttpStatusCode,
        body: String = "{}",
        observeRequest: (String, String) -> Unit = { _, _ -> },
    ): PlatformIdentityApi =
        KtorPlatformIdentityApi(
            HttpClient(
                MockEngine { request ->
                    val requestBody = request.body as TextContent
                    assertEquals(ContentType.Application.Json, requestBody.contentType)
                    observeRequest(request.url.toString(), requestBody.text)
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ),
        )
}
