package com.ratatoskr.mobile.identity

import com.ratatoskr.mobile.api.generated.model.CapabilityDocument
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCredentials(
    val origin: String,
    val userId: String,
    val deviceId: String,
    val deviceSecret: String,
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val refreshTokenUsable: Boolean = true,
)

data class SessionCredentials(
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
)

data class RecoveredSession(
    val userId: String,
    val deviceId: String,
    val session: SessionCredentials,
)

sealed interface IdentityResult<out T> {
    data class Success<T>(
        val value: T,
    ) : IdentityResult<T>

    data class Failure(
        val error: IdentityFailure,
    ) : IdentityResult<Nothing>
}

sealed interface IdentityFailure {
    data object InvalidOrigin : IdentityFailure

    data object Validation : IdentityFailure

    data object PairingRefused : IdentityFailure

    data object Unauthorized : IdentityFailure

    data class Unavailable(
        val retryable: Boolean,
    ) : IdentityFailure

    data object Uncertain : IdentityFailure

    data object InvalidResponse : IdentityFailure

    data object SecureStorage : IdentityFailure
}

interface PlatformIdentityApi {
    suspend fun pair(
        origin: String,
        code: String,
        displayName: String?,
    ): IdentityResult<DeviceCredentials>

    suspend fun refresh(
        origin: String,
        refreshToken: String,
    ): IdentityResult<SessionCredentials>

    suspend fun recover(
        origin: String,
        deviceId: String,
        deviceSecret: String,
    ): IdentityResult<RecoveredSession>

    suspend fun capabilities(
        origin: String,
        accessToken: String,
    ): IdentityResult<CapabilityDocument>
}

interface SecureCredentialStorage {
    @Throws(SecureCredentialStorageException::class)
    fun load(): DeviceCredentials?

    @Throws(SecureCredentialStorageException::class)
    fun save(credentials: DeviceCredentials)

    @Throws(SecureCredentialStorageException::class)
    fun clear()
}

class SecureCredentialStorageException internal constructor(
    operation: String? = null,
    status: Int? = null,
) : Exception(
        buildString {
            append("Secure credential storage is unavailable")
            if (operation != null) append(" during ").append(operation)
            if (status != null) append(" (status ").append(status).append(')')
        },
    )
