package com.ratatoskr.mobile.identity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceIdentityStoreTest {
    @Test
    fun fresh_installation_exposes_pairing_input() =
        runTest {
            val store =
                DeviceIdentityStore(
                    manager = DeviceSessionManager(FakePlatformIdentityApi(), MemoryCredentialStorage()),
                    scope = backgroundScope,
                )

            runCurrent()

            assertEquals(DeviceIdentityUiState.PairingForm(), store.uiState.value)
        }

    @Test
    fun accepted_credentials_expose_current_capabilities() =
        runTest {
            val api =
                FakePlatformIdentityApi().apply {
                    pairResult = IdentityResult.Success(deviceCredentials())
                    capabilityResults += IdentityResult.Success(capabilityDocument(names = arrayOf("search")))
                }
            val store =
                DeviceIdentityStore(
                    manager = DeviceSessionManager(api, MemoryCredentialStorage()),
                    scope = backgroundScope,
                )
            runCurrent()

            store.dispatch(DeviceIdentityAction.OriginChanged("https://platform.example"))
            store.dispatch(DeviceIdentityAction.CodeChanged("pairing-code"))
            store.dispatch(DeviceIdentityAction.DisplayNameChanged("My phone"))
            store.dispatch(DeviceIdentityAction.SubmitPairing)
            runCurrent()

            val paired = assertIs<DeviceIdentityUiState.Paired>(store.uiState.value)
            assertEquals("https://platform.example", paired.origin)
            assertTrue(paired.capabilitiesFresh)
            assertEquals(setOf("search"), paired.capabilities)
        }

    @Test
    fun revocation_returns_to_safe_repairing_state() =
        runTest {
            val api =
                FakePlatformIdentityApi().apply {
                    refreshResults += IdentityResult.Failure(IdentityFailure.Unauthorized)
                    recoveryResults += IdentityResult.Failure(IdentityFailure.Unauthorized)
                }
            val store =
                DeviceIdentityStore(
                    manager = DeviceSessionManager(api, MemoryCredentialStorage(deviceCredentials())),
                    scope = backgroundScope,
                )
            runCurrent()

            store.dispatch(DeviceIdentityAction.RetryAuthorization)
            runCurrent()

            assertEquals(DeviceIdentityUiState.RePairingRequired, store.uiState.value)
        }
}
