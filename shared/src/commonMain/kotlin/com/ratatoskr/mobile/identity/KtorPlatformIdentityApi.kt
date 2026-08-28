package com.ratatoskr.mobile.identity

import com.ratatoskr.mobile.api.generated.model.CapabilityDocument
import com.ratatoskr.mobile.api.generated.model.DeviceSessionOpened
import com.ratatoskr.mobile.api.generated.model.OpenDeviceSession
import com.ratatoskr.mobile.api.generated.model.PairDevice
import com.ratatoskr.mobile.api.generated.model.Paired
import com.ratatoskr.mobile.api.generated.model.RefreshSession
import com.ratatoskr.mobile.api.generated.model.RotatedCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
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

class KtorPlatformIdentityApi(
    private val client: HttpClient,
    private val json: Json = identityJson,
) : PlatformIdentityApi {
    override suspend fun pair(
        origin: String,
        code: String,
        displayName: String?,
    ): IdentityResult<DeviceCredentials> {
        val canonicalOrigin = canonicalHttpsOrigin(origin) ?: return invalidOrigin()
        val response =
            post(
                url = "$canonicalOrigin/v1/devices/pair",
                body = json.encodeToString(PairDevice(code = code, kind = MOBILE_DEVICE_KIND, displayName = displayName)),
            ) ?: return uncertain()
        return when (response.status) {
            HttpStatusCode.Created ->
                decode<Paired>(response)?.let { paired ->
                    IdentityResult.Success(
                        DeviceCredentials(
                            origin = canonicalOrigin,
                            userId = paired.userId,
                            deviceId = paired.deviceId,
                            deviceSecret = paired.deviceSecret,
                            accessToken = paired.credential,
                            accessExpiresAt = paired.expiresAt,
                            refreshToken = paired.refreshToken,
                            refreshExpiresAt = paired.refreshExpiresAt,
                        ),
                    )
                } ?: invalidResponse()
            HttpStatusCode.BadRequest -> validation()
            HttpStatusCode.Unauthorized -> IdentityResult.Failure(IdentityFailure.PairingRefused)
            HttpStatusCode.GatewayTimeout -> unavailable(retryable = true)
            else -> unavailable(response.status.value >= 500)
        }
    }

    override suspend fun refresh(
        origin: String,
        refreshToken: String,
    ): IdentityResult<SessionCredentials> {
        val canonicalOrigin = canonicalHttpsOrigin(origin) ?: return invalidOrigin()
        val response =
            post(
                url = "$canonicalOrigin/v1/sessions/refresh",
                body = json.encodeToString(RefreshSession(refreshToken)),
            ) ?: return uncertain()
        return when (response.status) {
            HttpStatusCode.OK ->
                decode<RotatedCredentials>(response)?.let { rotated ->
                    IdentityResult.Success(
                        SessionCredentials(
                            accessToken = rotated.credential,
                            accessExpiresAt = rotated.expiresAt,
                            refreshToken = rotated.refreshToken,
                            refreshExpiresAt = rotated.refreshExpiresAt,
                        ),
                    )
                } ?: invalidResponse()
            HttpStatusCode.BadRequest -> validation()
            HttpStatusCode.Unauthorized -> unauthorized()
            HttpStatusCode.GatewayTimeout -> unavailable(retryable = true)
            else -> unavailable(response.status.value >= 500)
        }
    }

    override suspend fun recover(
        origin: String,
        deviceId: String,
        deviceSecret: String,
    ): IdentityResult<RecoveredSession> {
        val canonicalOrigin = canonicalHttpsOrigin(origin) ?: return invalidOrigin()
        val response =
            post(
                url = "$canonicalOrigin/v1/sessions/device",
                body = json.encodeToString(OpenDeviceSession(deviceId, deviceSecret)),
            ) ?: return uncertain()
        return when (response.status) {
            HttpStatusCode.Created ->
                decode<DeviceSessionOpened>(response)?.let { opened ->
                    IdentityResult.Success(
                        RecoveredSession(
                            userId = opened.userId,
                            deviceId = opened.deviceId,
                            session =
                                SessionCredentials(
                                    accessToken = opened.credential,
                                    accessExpiresAt = opened.expiresAt,
                                    refreshToken = opened.refreshToken,
                                    refreshExpiresAt = opened.refreshExpiresAt,
                                ),
                        ),
                    )
                } ?: invalidResponse()
            HttpStatusCode.BadRequest -> validation()
            HttpStatusCode.Unauthorized -> unauthorized()
            HttpStatusCode.GatewayTimeout -> unavailable(retryable = true)
            else -> unavailable(response.status.value >= 500)
        }
    }

    override suspend fun capabilities(
        origin: String,
        accessToken: String,
    ): IdentityResult<CapabilityDocument> {
        val canonicalOrigin = canonicalHttpsOrigin(origin) ?: return invalidOrigin()
        val response =
            try {
                client.get("$canonicalOrigin/v1/capabilities") {
                    bearerAuth(accessToken)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return uncertain()
            }
        return when (response.status) {
            HttpStatusCode.OK ->
                decode<CapabilityDocument>(response)?.let { document ->
                    IdentityResult.Success(document)
                }
                    ?: invalidResponse()
            HttpStatusCode.Unauthorized -> unauthorized()
            HttpStatusCode.TooManyRequests -> unavailable(retryable = true)
            else -> unavailable(response.status.value >= 500)
        }
    }

    private suspend fun post(
        url: String,
        body: String,
    ): HttpResponse? =
        try {
            client.post(url) {
                setBody(TextContent(body, ContentType.Application.Json))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

    private suspend inline fun <reified T> decode(response: HttpResponse): T? =
        try {
            json.decodeFromString(response.bodyAsText())
        } catch (_: SerializationException) {
            null
        }

    private fun canonicalHttpsOrigin(raw: String): String? {
        if (!raw.startsWith("https://") || raw != raw.trim()) return null
        val authority = raw.removePrefix("https://").removeSuffix("/")
        if (authority.isBlank() || authority.any { it == '/' || it == '?' || it == '#' || it == '@' }) return null
        return "https://$authority"
    }

    private fun invalidOrigin() = IdentityResult.Failure(IdentityFailure.InvalidOrigin)

    private fun validation() = IdentityResult.Failure(IdentityFailure.Validation)

    private fun unauthorized() = IdentityResult.Failure(IdentityFailure.Unauthorized)

    private fun unavailable(retryable: Boolean) = IdentityResult.Failure(IdentityFailure.Unavailable(retryable))

    private fun uncertain() = IdentityResult.Failure(IdentityFailure.Uncertain)

    private fun invalidResponse() = IdentityResult.Failure(IdentityFailure.InvalidResponse)

    private companion object {
        const val MOBILE_DEVICE_KIND = "mobile"
        val identityJson = Json { ignoreUnknownKeys = true }
    }
}
