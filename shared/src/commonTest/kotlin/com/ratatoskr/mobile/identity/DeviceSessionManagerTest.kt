package com.ratatoskr.mobile.identity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceSessionManagerTest {
    @Test
    fun refresh_replaces_access_and_refresh_credentials_atomically() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    refreshResults += IdentityResult.Success(sessionCredentials())
                }
            val manager = DeviceSessionManager(api, storage)

            manager.restore()
            val authorization = assertIs<IdentityResult.Success<Authorization>>(manager.refreshSession()).value

            assertEquals("access-next", authorization.accessToken)
            assertEquals("access-next", storage.credentials?.accessToken)
            assertEquals("refresh-next", storage.credentials?.refreshToken)
            assertEquals(1, api.refreshCount)
            assertEquals(2, storage.saveCount)
            assertEquals(listOf(false, true), storage.saveHistory.map { it.refreshTokenUsable })
            assertEquals(
                DeviceIdentityState.Paired(
                    origin = "https://platform.example",
                    userId = "user-1",
                    deviceId = "device-1",
                ),
                manager.state.value,
            )
        }

    @Test
    fun concurrent_refresh_callers_share_one_rotation() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            val api =
                FakePlatformIdentityApi().apply {
                    refreshEntered = entered
                    refreshGate = gate
                    refreshResults += IdentityResult.Success(sessionCredentials())
                    refreshResults +=
                        IdentityResult.Success(
                            sessionCredentials(accessToken = "access-raced", refreshToken = "refresh-raced"),
                        )
                }
            val manager = DeviceSessionManager(api, storage)
            manager.restore()

            val first = async { manager.refreshSession() }
            entered.await()
            val second = async { manager.refreshSession() }
            gate.complete(Unit)
            val results = awaitAll(first, second)

            assertEquals(1, api.refreshCount)
            assertEquals(listOf("refresh-old"), api.presentedRefreshTokens)
            assertEquals(2, storage.saveCount)
            assertEquals(
                listOf("access-next", "access-next"),
                results.map { assertIs<IdentityResult.Success<Authorization>>(it).value.accessToken },
            )
        }

    @Test
    fun uncertain_refresh_recovers_without_replaying_the_link() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    refreshResults += IdentityResult.Failure(IdentityFailure.Uncertain)
                    recoveryResults +=
                        IdentityResult.Success(
                            RecoveredSession(
                                userId = "user-1",
                                deviceId = "device-1",
                                session =
                                    sessionCredentials(
                                        accessToken = "access-recovered",
                                        refreshToken = "refresh-recovered",
                                    ),
                            ),
                        )
                }
            val manager = DeviceSessionManager(api, storage)
            manager.restore()

            val authorization = assertIs<IdentityResult.Success<Authorization>>(manager.refreshSession()).value

            assertEquals("access-recovered", authorization.accessToken)
            assertEquals(listOf("refresh-old"), api.presentedRefreshTokens)
            assertEquals(1, api.refreshCount)
            assertEquals(1, api.recoveryCount)
            assertEquals("refresh-recovered", storage.credentials?.refreshToken)
            assertEquals(2, storage.saveCount)
        }

    @Test
    fun paired_elsewhere_revocation_clears_session_gracefully() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    refreshResults += IdentityResult.Failure(IdentityFailure.Unauthorized)
                    recoveryResults += IdentityResult.Failure(IdentityFailure.Unauthorized)
                }
            val manager = DeviceSessionManager(api, storage)
            manager.restore()

            val result = manager.refreshSession()

            assertEquals(IdentityResult.Failure(IdentityFailure.Unauthorized), result)
            assertNull(storage.credentials)
            assertEquals(1, storage.clearCount)
            assertEquals(DeviceIdentityState.RePairingRequired, manager.state.value)
            assertEquals(CapabilityState.Empty, manager.capabilities.value)
        }

    @Test
    fun local_sign_out_clears_only_local_authorization() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api = FakePlatformIdentityApi()
            val manager = DeviceSessionManager(api, storage)
            manager.restore()

            manager.signOut()

            assertNull(storage.credentials)
            assertEquals(1, storage.clearCount)
            assertEquals(DeviceIdentityState.SignedOut, manager.state.value)
            assertEquals(CapabilityState.Empty, manager.capabilities.value)
            assertEquals(0, api.refreshCount)
            assertEquals(0, api.recoveryCount)
        }

    @Test
    fun capability_discovery_exposes_only_fresh_known_availability() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    capabilityResults +=
                        IdentityResult.Success(
                            capabilityDocument(
                                names = arrayOf("content.submit", "unknown.future"),
                                staleServices = setOf("social"),
                            ),
                        )
                }
            val manager = DeviceSessionManager(api, storage)

            manager.restore()

            val snapshot = assertIs<CapabilityState.Ready>(manager.capabilities.value).snapshot
            assertEquals(setOf("content.submit", "unknown.future"), snapshot.names)
            assertEquals(setOf("social"), snapshot.staleServices)
            assertTrue(manager.isCapabilityAvailable(MobileCapability.ContentSubmit))
            assertFalse(manager.isCapabilityAvailable(MobileCapability.Search))
        }

    @Test
    fun failed_capability_refresh_keeps_context_but_fails_closed() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    capabilityResults += IdentityResult.Success(capabilityDocument(names = arrayOf("content.submit")))
                    capabilityResults += IdentityResult.Failure(IdentityFailure.Unavailable(retryable = true))
                }
            val manager = DeviceSessionManager(api, storage)
            manager.restore()

            manager.refreshCapabilities()

            val stale = assertIs<CapabilityState.Stale>(manager.capabilities.value)
            assertEquals(setOf("content.submit"), stale.snapshot?.names)
            assertFalse(manager.isCapabilityAvailable(MobileCapability.ContentSubmit))
        }

    @Test
    fun recovered_session_replaces_capability_cache() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    capabilityResults += IdentityResult.Success(capabilityDocument(names = arrayOf("content.submit")))
                    refreshResults += IdentityResult.Failure(IdentityFailure.Uncertain)
                    recoveryResults +=
                        IdentityResult.Success(
                            RecoveredSession(
                                userId = "user-1",
                                deviceId = "device-1",
                                session = sessionCredentials(),
                            ),
                        )
                    capabilityResults += IdentityResult.Success(capabilityDocument(names = arrayOf("search")))
                }
            val manager = DeviceSessionManager(api, storage)
            manager.restore()
            assertTrue(manager.isCapabilityAvailable(MobileCapability.ContentSubmit))

            manager.refreshSession()

            val snapshot = assertIs<CapabilityState.Ready>(manager.capabilities.value).snapshot
            assertEquals(setOf("search"), snapshot.names)
            assertFalse(manager.isCapabilityAvailable(MobileCapability.ContentSubmit))
            assertTrue(manager.isCapabilityAvailable(MobileCapability.Search))
        }

    @Test
    fun capability_unauthorized_rotates_once_and_retries_with_replacement_access() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials())
            val api =
                FakePlatformIdentityApi().apply {
                    capabilityResults += IdentityResult.Failure(IdentityFailure.Unauthorized)
                    refreshResults += IdentityResult.Success(sessionCredentials())
                    capabilityResults += IdentityResult.Success(capabilityDocument(names = arrayOf("search")))
                }
            val manager = DeviceSessionManager(api, storage)

            manager.restore()

            assertEquals(1, api.refreshCount)
            assertEquals(0, api.recoveryCount)
            assertEquals(listOf("access-old", "access-next"), api.presentedAccessTokens)
            assertTrue(manager.isCapabilityAvailable(MobileCapability.Search))
        }

    @Test
    fun restart_with_consumed_refresh_marker_recovers_without_replay() =
        runTest {
            val storage = MemoryCredentialStorage(deviceCredentials().copy(refreshTokenUsable = false))
            val api =
                FakePlatformIdentityApi().apply {
                    recoveryResults +=
                        IdentityResult.Success(
                            RecoveredSession(
                                userId = "user-1",
                                deviceId = "device-1",
                                session = sessionCredentials(),
                            ),
                        )
                }
            val manager = DeviceSessionManager(api, storage)

            manager.restore()

            assertEquals(0, api.refreshCount)
            assertEquals(1, api.recoveryCount)
            assertEquals("refresh-next", storage.credentials?.refreshToken)
            assertEquals(true, storage.credentials?.refreshTokenUsable)
        }
}
