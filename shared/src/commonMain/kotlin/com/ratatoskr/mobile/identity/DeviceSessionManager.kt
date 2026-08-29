package com.ratatoskr.mobile.identity

import com.ratatoskr.mobile.api.generated.model.CapabilityDocument
import com.ratatoskr.mobile.api.generated.model.ServiceCapabilities
import com.ratatoskr.mobile.github.GithubActionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

sealed interface DeviceIdentityState {
    data object SignedOut : DeviceIdentityState

    data object Restoring : DeviceIdentityState

    data object Pairing : DeviceIdentityState

    data object Refreshing : DeviceIdentityState

    data class Paired(
        val origin: String,
        val userId: String,
        val deviceId: String,
    ) : DeviceIdentityState

    data object RePairingRequired : DeviceIdentityState

    data class Failed(
        val failure: IdentityFailure,
    ) : DeviceIdentityState
}

sealed interface CapabilityState {
    data object Empty : CapabilityState

    data object Loading : CapabilityState

    data class Ready(
        val snapshot: CapabilitySnapshot,
    ) : CapabilityState

    data class Stale(
        val snapshot: CapabilitySnapshot?,
    ) : CapabilityState
}

data class CapabilitySnapshot(
    val apiVersion: String,
    val minimumMobileVersion: String,
    val names: Set<String>,
    val staleServices: Set<String>,
    val github: GithubServiceCapability? = null,
)

data class GithubServiceCapability(
    val repositoryPreview: Boolean,
    val actions: Set<GithubActionMode>,
)

data class Authorization(
    val origin: String,
    val accessToken: String,
)

enum class MobileCapability(
    val wireName: String,
) {
    ContentSubmit("content.submit"),
    VaultSnapshots("vault.snapshots"),
    SocialX("social.x"),
    SocialInstagram("social.instagram"),
    SocialThreads("social.threads"),
    ArchiveChatgpt("archive.chatgpt"),
    ArchiveClaude("archive.claude"),
    LibrarySearch("library.search"),
    LibraryReadState("library.read_state"),
    TelegramIntegration("telegram.integration"),
    Search("search"),
}

class DeviceSessionManager(
    private val api: PlatformIdentityApi,
    private val storage: SecureCredentialStorage,
) {
    private val mutationMutex = Mutex()
    private var currentCredentials: DeviceCredentials? = null
    private val mutableState = MutableStateFlow<DeviceIdentityState>(DeviceIdentityState.SignedOut)
    val state: StateFlow<DeviceIdentityState> = mutableState.asStateFlow()

    private val mutableCapabilities = MutableStateFlow<CapabilityState>(CapabilityState.Empty)
    val capabilities: StateFlow<CapabilityState> = mutableCapabilities.asStateFlow()

    suspend fun restore() {
        mutationMutex.withLock {
            mutableState.value = DeviceIdentityState.Restoring
            val stored =
                try {
                    storage.load()
                } catch (_: SecureCredentialStorageException) {
                    currentCredentials = null
                    mutableCapabilities.value = CapabilityState.Empty
                    mutableState.value = DeviceIdentityState.Failed(IdentityFailure.SecureStorage)
                    return@withLock
                }
            currentCredentials = stored
            mutableState.value = stored?.pairedState() ?: DeviceIdentityState.SignedOut
            if (stored == null) {
                mutableCapabilities.value = CapabilityState.Empty
            } else if (!stored.refreshTokenUsable) {
                mutableCapabilities.value = CapabilityState.Empty
                mutableState.value = DeviceIdentityState.Refreshing
                recoverSession(stored, discoverAfterRecovery = true)
            } else {
                discoverCapabilities(stored)
            }
        }
    }

    suspend fun pair(
        origin: String,
        code: String,
        displayName: String?,
    ): IdentityResult<Unit> =
        mutationMutex.withLock {
            mutableState.value = DeviceIdentityState.Pairing
            mutableCapabilities.value = CapabilityState.Empty
            when (val result = api.pair(origin, code, displayName)) {
                is IdentityResult.Success -> {
                    try {
                        storage.save(result.value)
                    } catch (_: SecureCredentialStorageException) {
                        currentCredentials = null
                        mutableState.value = DeviceIdentityState.Failed(IdentityFailure.SecureStorage)
                        return@withLock IdentityResult.Failure(IdentityFailure.SecureStorage)
                    }
                    currentCredentials = result.value
                    mutableState.value = result.value.pairedState()
                    discoverCapabilities(result.value)
                    IdentityResult.Success(Unit)
                }
                is IdentityResult.Failure -> {
                    currentCredentials = null
                    mutableState.value = DeviceIdentityState.Failed(result.error)
                    result
                }
            }
        }

    suspend fun refreshSession(): IdentityResult<Authorization> {
        val observedRefreshToken =
            currentCredentials?.refreshToken
                ?: return IdentityResult.Failure(IdentityFailure.Unauthorized)
        return mutationMutex.withLock {
            refreshSession(observedRefreshToken, discoverAfterRecovery = true)
        }
    }

    suspend fun currentAuthorization(): IdentityResult<Authorization> =
        mutationMutex.withLock {
            currentCredentials?.let { IdentityResult.Success(it.authorization()) }
                ?: IdentityResult.Failure(IdentityFailure.Unauthorized)
        }

    suspend fun refreshCapabilities(): IdentityResult<CapabilityDocument> =
        mutationMutex.withLock {
            val existing =
                currentCredentials
                    ?: return@withLock IdentityResult.Failure(IdentityFailure.Unauthorized)
            discoverCapabilities(existing)
        }

    fun isCapabilityAvailable(capability: MobileCapability): Boolean =
        (mutableCapabilities.value as? CapabilityState.Ready)
            ?.snapshot
            ?.names
            ?.contains(capability.wireName) == true

    suspend fun signOut() {
        mutationMutex.withLock {
            clearAuthorization(DeviceIdentityState.SignedOut)
        }
    }

    private suspend fun refreshSession(
        observedRefreshToken: String,
        discoverAfterRecovery: Boolean,
    ): IdentityResult<Authorization> {
        val existing =
            currentCredentials
                ?: return IdentityResult.Failure(IdentityFailure.Unauthorized)
        if (existing.refreshToken != observedRefreshToken) {
            return IdentityResult.Success(existing.authorization())
        }
        mutableState.value = DeviceIdentityState.Refreshing
        if (!existing.refreshTokenUsable) {
            return recoverSession(existing, discoverAfterRecovery)
        }
        val marked =
            markRefreshTokenUnusable(existing)
                ?: return IdentityResult.Failure(IdentityFailure.SecureStorage)
        return when (val result = api.refresh(existing.origin, existing.refreshToken)) {
            is IdentityResult.Success -> replaceSession(marked, result.value)
            is IdentityResult.Failure -> recoverSession(marked, discoverAfterRecovery)
        }
    }

    private fun markRefreshTokenUnusable(existing: DeviceCredentials): DeviceCredentials? {
        val marked = existing.copy(refreshTokenUsable = false)
        return try {
            storage.save(marked)
            currentCredentials = marked
            marked
        } catch (_: SecureCredentialStorageException) {
            mutableState.value = DeviceIdentityState.Failed(IdentityFailure.SecureStorage)
            null
        }
    }

    private suspend fun recoverSession(
        existing: DeviceCredentials,
        discoverAfterRecovery: Boolean,
    ): IdentityResult<Authorization> =
        when (
            val recovery =
                api.recover(
                    origin = existing.origin,
                    deviceId = existing.deviceId,
                    deviceSecret = existing.deviceSecret,
                )
        ) {
            is IdentityResult.Success -> {
                if (
                    recovery.value.userId != existing.userId ||
                    recovery.value.deviceId != existing.deviceId
                ) {
                    mutableState.value = DeviceIdentityState.Failed(IdentityFailure.InvalidResponse)
                    IdentityResult.Failure(IdentityFailure.InvalidResponse)
                } else {
                    mutableCapabilities.value = CapabilityState.Empty
                    when (val replacement = replaceSession(existing, recovery.value.session)) {
                        is IdentityResult.Success -> {
                            if (discoverAfterRecovery) {
                                currentCredentials?.let { discoverCapabilities(it) }
                            }
                            replacement
                        }
                        is IdentityResult.Failure -> replacement
                    }
                }
            }
            is IdentityResult.Failure -> {
                if (recovery.error == IdentityFailure.Unauthorized) {
                    clearAuthorization(DeviceIdentityState.RePairingRequired)
                } else {
                    mutableState.value = DeviceIdentityState.Failed(recovery.error)
                    recovery
                }
            }
        }

    private fun clearAuthorization(nextState: DeviceIdentityState): IdentityResult.Failure =
        try {
            storage.clear()
            currentCredentials = null
            mutableCapabilities.value = CapabilityState.Empty
            mutableState.value = nextState
            IdentityResult.Failure(IdentityFailure.Unauthorized)
        } catch (_: SecureCredentialStorageException) {
            mutableState.value = DeviceIdentityState.Failed(IdentityFailure.SecureStorage)
            IdentityResult.Failure(IdentityFailure.SecureStorage)
        }

    private fun replaceSession(
        existing: DeviceCredentials,
        session: SessionCredentials,
    ): IdentityResult<Authorization> {
        val replacement = existing.withSession(session)
        try {
            storage.save(replacement)
        } catch (_: SecureCredentialStorageException) {
            mutableState.value = DeviceIdentityState.Failed(IdentityFailure.SecureStorage)
            return IdentityResult.Failure(IdentityFailure.SecureStorage)
        }
        currentCredentials = replacement
        mutableState.value = replacement.pairedState()
        return IdentityResult.Success(replacement.authorization())
    }

    private suspend fun discoverCapabilities(credentials: DeviceCredentials): IdentityResult<CapabilityDocument> {
        val previous =
            when (val state = mutableCapabilities.value) {
                is CapabilityState.Ready -> state.snapshot
                is CapabilityState.Stale -> state.snapshot
                CapabilityState.Empty,
                CapabilityState.Loading,
                -> null
            }
        mutableCapabilities.value = CapabilityState.Loading
        val firstResult =
            api.capabilities(
                origin = credentials.origin,
                accessToken = credentials.accessToken,
            )
        val result =
            if (firstResult == IdentityResult.Failure(IdentityFailure.Unauthorized)) {
                when (
                    refreshSession(
                        observedRefreshToken = credentials.refreshToken,
                        discoverAfterRecovery = false,
                    )
                ) {
                    is IdentityResult.Success -> {
                        val replacement = currentCredentials
                        if (replacement == null) {
                            IdentityResult.Failure(IdentityFailure.Unauthorized)
                        } else {
                            api.capabilities(
                                origin = replacement.origin,
                                accessToken = replacement.accessToken,
                            )
                        }
                    }
                    is IdentityResult.Failure -> IdentityResult.Failure(IdentityFailure.Unauthorized)
                }
            } else {
                firstResult
            }
        return when (result) {
            is IdentityResult.Success -> {
                mutableCapabilities.value = CapabilityState.Ready(result.value.toSnapshot())
                result
            }
            is IdentityResult.Failure -> {
                if (mutableState.value !is DeviceIdentityState.RePairingRequired) {
                    mutableCapabilities.value = CapabilityState.Stale(previous)
                }
                result
            }
        }
    }

    private fun CapabilityDocument.toSnapshot() =
        CapabilitySnapshot(
            apiVersion = apiVersion,
            minimumMobileVersion = minimumClientVersions.mobile,
            names = capabilities.sorted().toSet(),
            staleServices = services.filter { it.stale }.map { it.service }.toSet(),
            github = services.githubCapability(),
        )

    private fun DeviceCredentials.withSession(session: SessionCredentials) =
        copy(
            accessToken = session.accessToken,
            accessExpiresAt = session.accessExpiresAt,
            refreshToken = session.refreshToken,
            refreshExpiresAt = session.refreshExpiresAt,
            refreshTokenUsable = true,
        )

    private fun DeviceCredentials.pairedState() =
        DeviceIdentityState.Paired(
            origin = origin,
            userId = userId,
            deviceId = deviceId,
        )

    private fun DeviceCredentials.authorization() =
        Authorization(
            origin = origin,
            accessToken = accessToken,
        )
}

private fun List<ServiceCapabilities>.githubCapability(): GithubServiceCapability? {
    val service = filter { it.service == "github" }.singleOrNull() ?: return null
    if (service.stale || service.observedAt.isNullOrBlank() || service.staleSince != null) return null
    val document = service.document as? JsonObject ?: return null
    if (document.keys != setOf("repository_preview", "repository_actions")) return null
    if ((document["repository_preview"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val actionValues =
        (document["repository_actions"] as? JsonArray)
            ?.map { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            } ?: return null
    if (actionValues.size > GithubActionMode.entries.size || actionValues.size != actionValues.toSet().size) return null
    val actions =
        actionValues
            .map { wireName ->
                GithubActionMode.entries.firstOrNull { it.wireName == wireName } ?: return null
            }.toSet()
    return GithubServiceCapability(repositoryPreview = true, actions = actions)
}
