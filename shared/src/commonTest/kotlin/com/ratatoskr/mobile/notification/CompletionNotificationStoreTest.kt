package com.ratatoskr.mobile.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CompletionNotificationStoreTest {
    @Test
    fun missing_public_subscribe_contract_is_integration_pending_and_calls_nothing() =
        runTest {
            val fixture = fixture(this, CompletionSubscriptionAvailability.IntegrationPending)

            fixture.store.enable()
            advanceUntilIdle()

            assertEquals(CompletionNotificationEffectiveState.IntegrationPending, fixture.store.state.value.effective)
            assertEquals(0, fixture.native.requestCalls)
            assertEquals(0, fixture.subscriptions.subscribeCalls)
        }

    @Test
    fun explicit_enable_requests_permission_once_when_contract_fixture_is_available() =
        runTest {
            val fixture = fixture(this, CompletionSubscriptionAvailability.Available)

            fixture.store.enable()
            fixture.store.enable()
            advanceUntilIdle()

            assertEquals(1, fixture.native.requestCalls)
            assertEquals(1, fixture.subscriptions.subscribeCalls)
            assertEquals(CompletionNotificationEffectiveState.Enabled, fixture.store.state.value.effective)
        }

    @Test
    fun denied_permission_never_claims_delivery_or_reprompts() =
        runTest {
            val fixture =
                fixture(
                    scope = this,
                    availability = CompletionSubscriptionAvailability.Available,
                    permission = NativeNotificationPermissionState.Denied,
                )

            fixture.store.enable()
            fixture.store.enable()
            advanceUntilIdle()

            assertEquals(0, fixture.native.requestCalls)
            assertEquals(1, fixture.native.settingsCalls)
            assertEquals(0, fixture.subscriptions.subscribeCalls)
            assertEquals(CompletionNotificationEffectiveState.PermissionDenied, fixture.store.state.value.effective)
        }

    @Test
    fun capability_loss_clears_stale_handle() =
        runTest {
            val fixture =
                fixture(
                    scope = this,
                    availability = CompletionSubscriptionAvailability.Available,
                    permission = NativeNotificationPermissionState.Granted,
                )
            fixture.store.enable()
            advanceUntilIdle()

            fixture.store.updateAvailability(CompletionSubscriptionAvailability.IntegrationPending)
            advanceUntilIdle()

            assertEquals(listOf("fixture-handle"), fixture.subscriptions.unsubscribed)
            assertFalse(fixture.store.state.value.subscriptionHandlePresent)
            assertEquals(CompletionNotificationEffectiveState.IntegrationPending, fixture.store.state.value.effective)
        }

    @Test
    fun revocation_returns_empty_unpaired_state() =
        runTest {
            val fixture =
                fixture(
                    scope = this,
                    availability = CompletionSubscriptionAvailability.Available,
                    permission = NativeNotificationPermissionState.Granted,
                )
            fixture.store.enable()
            advanceUntilIdle()

            fixture.store.updatePaired(false)
            advanceUntilIdle()

            val state = fixture.store.state.value
            assertEquals(CompletionNotificationEffectiveState.Unpaired, state.effective)
            assertFalse(state.enabledByUser)
            assertFalse(state.subscriptionHandlePresent)
            assertEquals(1, fixture.native.clearCalls)
            assertEquals(listOf("fixture-handle"), fixture.subscriptions.unsubscribed)
        }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        availability: CompletionSubscriptionAvailability,
        permission: NativeNotificationPermissionState = NativeNotificationPermissionState.NotDetermined,
    ): Fixture {
        val native = FakeNativePermission(permission)
        val subscriptions = FakeSubscriptions()
        val store =
            CompletionNotificationStore(
                availability = availability,
                paired = true,
                permission = permission,
                native = native,
                subscriptions = subscriptions,
                scope = scope,
            )
        return Fixture(store, native, subscriptions)
    }

    private data class Fixture(
        val store: CompletionNotificationStore,
        val native: FakeNativePermission,
        val subscriptions: FakeSubscriptions,
    )

    private class FakeNativePermission(
        private val result: NativeNotificationPermissionState,
    ) : NativeNotificationPermissionPort {
        var requestCalls = 0
        var settingsCalls = 0
        var clearCalls = 0

        override suspend fun requestPermission(): NativeNotificationPermissionState {
            requestCalls += 1
            return if (result == NativeNotificationPermissionState.NotDetermined) {
                NativeNotificationPermissionState.Granted
            } else {
                result
            }
        }

        override suspend fun openSettings() {
            settingsCalls += 1
        }

        override suspend fun clearNotifications() {
            clearCalls += 1
        }
    }

    private class FakeSubscriptions : CompletionSubscriptionPort {
        var subscribeCalls = 0
        val unsubscribed = mutableListOf<String>()

        override suspend fun subscribe(): String {
            subscribeCalls += 1
            return "fixture-handle"
        }

        override suspend fun unsubscribe(handle: String) {
            unsubscribed += handle
        }
    }
}
