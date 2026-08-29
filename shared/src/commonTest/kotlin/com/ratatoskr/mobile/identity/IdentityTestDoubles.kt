package com.ratatoskr.mobile.identity

import com.ratatoskr.mobile.api.generated.model.CapabilityDocument
import com.ratatoskr.mobile.api.generated.model.MinimumClientVersions
import com.ratatoskr.mobile.api.generated.model.ServiceCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonNull

internal class MemoryCredentialStorage(
    var credentials: DeviceCredentials? = null,
) : SecureCredentialStorage {
    var saveCount = 0
    var clearCount = 0
    val saveHistory = mutableListOf<DeviceCredentials>()

    override fun load(): DeviceCredentials? = credentials

    override fun save(credentials: DeviceCredentials) {
        this.credentials = credentials
        saveHistory += credentials
        saveCount += 1
    }

    override fun clear() {
        credentials = null
        clearCount += 1
    }
}

internal class FakePlatformIdentityApi : PlatformIdentityApi {
    var pairResult: IdentityResult<DeviceCredentials> = IdentityResult.Failure(IdentityFailure.Uncertain)
    val refreshResults = ArrayDeque<IdentityResult<SessionCredentials>>()
    val recoveryResults = ArrayDeque<IdentityResult<RecoveredSession>>()
    val capabilityResults = ArrayDeque<IdentityResult<CapabilityDocument>>()
    val presentedRefreshTokens = mutableListOf<String>()
    val presentedAccessTokens = mutableListOf<String>()
    var refreshCount = 0
    var recoveryCount = 0
    var capabilityCount = 0
    var refreshEntered: CompletableDeferred<Unit>? = null
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun pair(
        origin: String,
        code: String,
        displayName: String?,
    ): IdentityResult<DeviceCredentials> = pairResult

    override suspend fun refresh(
        origin: String,
        refreshToken: String,
    ): IdentityResult<SessionCredentials> {
        refreshCount += 1
        presentedRefreshTokens += refreshToken
        refreshEntered?.complete(Unit)
        refreshGate?.await()
        return refreshResults.removeFirst()
    }

    override suspend fun recover(
        origin: String,
        deviceId: String,
        deviceSecret: String,
    ): IdentityResult<RecoveredSession> {
        recoveryCount += 1
        return recoveryResults.removeFirst()
    }

    override suspend fun capabilities(
        origin: String,
        accessToken: String,
    ): IdentityResult<CapabilityDocument> {
        capabilityCount += 1
        presentedAccessTokens += accessToken
        return capabilityResults.removeFirstOrNull() ?: IdentityResult.Success(capabilityDocument())
    }
}

internal fun deviceCredentials(
    accessToken: String = "access-old",
    refreshToken: String = "refresh-old",
) = DeviceCredentials(
    origin = "https://platform.example",
    userId = "user-1",
    deviceId = "device-1",
    deviceSecret = "root-secret",
    accessToken = accessToken,
    accessExpiresAt = "2026-08-28T11:00:00Z",
    refreshToken = refreshToken,
    refreshExpiresAt = "2026-09-28T11:00:00Z",
)

internal fun sessionCredentials(
    accessToken: String = "access-next",
    refreshToken: String = "refresh-next",
) = SessionCredentials(
    accessToken = accessToken,
    accessExpiresAt = "2026-08-28T12:00:00Z",
    refreshToken = refreshToken,
    refreshExpiresAt = "2026-09-28T12:00:00Z",
)

internal fun capabilityDocument(
    names: Array<out String> = emptyArray(),
    staleServices: Set<String> = emptySet(),
    freshServices: List<ServiceCapabilities> = emptyList(),
) = CapabilityDocument(
    apiVersion = "1",
    capabilities = names.toList(),
    minimumClientVersions = MinimumClientVersions(mobile = "1.0", web = "1.0"),
    services =
        freshServices +
            staleServices.map { service ->
                ServiceCapabilities(
                    document = JsonNull,
                    service = service,
                    stale = true,
                )
            },
)
