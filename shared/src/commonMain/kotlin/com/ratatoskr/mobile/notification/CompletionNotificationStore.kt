package com.ratatoskr.mobile.notification

import androidx.navigation3.runtime.NavKey
import com.ratatoskr.mobile.diagnostics.MobileDiagnosticEvent
import com.ratatoskr.mobile.diagnostics.MobileDiagnosticOutcome
import com.ratatoskr.mobile.diagnostics.MobileDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object NotificationSettingsRoute : NavKey

enum class CompletionSubscriptionAvailability {
    IntegrationPending,
    Available,
}

enum class NativeNotificationPermissionState {
    NotDetermined,
    Granted,
    Denied,
}

enum class CompletionNotificationEffectiveState {
    Unpaired,
    IntegrationPending,
    Disabled,
    PermissionRequired,
    PermissionDenied,
    Enabling,
    Enabled,
}

data class CompletionNotificationState(
    val paired: Boolean,
    val availability: CompletionSubscriptionAvailability,
    val permission: NativeNotificationPermissionState,
    val enabledByUser: Boolean,
    val subscriptionHandlePresent: Boolean,
    val effective: CompletionNotificationEffectiveState,
)

interface NativeNotificationPermissionPort {
    suspend fun requestPermission(): NativeNotificationPermissionState

    suspend fun openSettings()

    suspend fun clearNotifications()
}

interface CompletionSubscriptionPort {
    suspend fun subscribe(): String

    suspend fun unsubscribe(handle: String)
}

object IntegrationPendingNativeNotificationPermissionPort : NativeNotificationPermissionPort {
    override suspend fun requestPermission(): NativeNotificationPermissionState =
        error("Native notification permission cannot be requested without a public subscription contract")

    override suspend fun openSettings() = Unit

    override suspend fun clearNotifications() = Unit
}

object IntegrationPendingCompletionSubscriptionPort : CompletionSubscriptionPort {
    override suspend fun subscribe(): String = error("Platform completion subscription is absent from the pinned public contract")

    override suspend fun unsubscribe(handle: String) = Unit
}

class CompletionNotificationStore(
    availability: CompletionSubscriptionAvailability,
    paired: Boolean,
    permission: NativeNotificationPermissionState,
    private val native: NativeNotificationPermissionPort,
    private val subscriptions: CompletionSubscriptionPort,
    private val scope: CoroutineScope,
    private val diagnostics: MobileDiagnostics = MobileDiagnostics(),
) {
    private var subscriptionHandle: String? = null
    private var operationInFlight = false
    private var settingsOpened = false
    private var generation = 0L
    private val mutableState =
        MutableStateFlow(
            CompletionNotificationState(
                paired = paired,
                availability = availability,
                permission = permission,
                enabledByUser = false,
                subscriptionHandlePresent = false,
                effective =
                    effective(
                        paired = paired,
                        availability = availability,
                        enabled = false,
                        permission = permission,
                        handle = null,
                        inFlight = false,
                    ),
            ),
        )
    val state: StateFlow<CompletionNotificationState> = mutableState

    fun enable() {
        val current = mutableState.value
        if (!current.paired) return
        if (!current.enabledByUser) publish(enabled = true)
        if (current.availability == CompletionSubscriptionAvailability.IntegrationPending) return
        when (mutableState.value.permission) {
            NativeNotificationPermissionState.NotDetermined -> requestPermission()
            NativeNotificationPermissionState.Granted -> subscribe()
            NativeNotificationPermissionState.Denied -> openSettingsOnce()
        }
    }

    fun disable() {
        generation += 1
        operationInFlight = false
        settingsOpened = false
        val staleHandle = subscriptionHandle
        subscriptionHandle = null
        publish(enabled = false)
        if (staleHandle != null) scope.launch { subscriptions.unsubscribe(staleHandle) }
    }

    fun updateAvailability(value: CompletionSubscriptionAvailability) {
        if (mutableState.value.availability == value) return
        generation += 1
        operationInFlight = false
        settingsOpened = false
        val staleHandle = subscriptionHandle
        subscriptionHandle = null
        publish(availability = value)
        if (staleHandle != null) scope.launch { subscriptions.unsubscribe(staleHandle) }
        if (value == CompletionSubscriptionAvailability.Available && mutableState.value.enabledByUser) enable()
    }

    fun updatePaired(value: Boolean) {
        if (mutableState.value.paired == value) return
        generation += 1
        operationInFlight = false
        settingsOpened = false
        val staleHandle = subscriptionHandle
        subscriptionHandle = null
        publish(paired = value, enabled = false)
        if (!value) {
            scope.launch {
                if (staleHandle != null) subscriptions.unsubscribe(staleHandle)
                native.clearNotifications()
            }
        }
    }

    private fun requestPermission() {
        if (operationInFlight) return
        operationInFlight = true
        publish()
        val expectedGeneration = generation
        scope.launch {
            val result = native.requestPermission()
            if (generation != expectedGeneration) return@launch
            operationInFlight = false
            publish(permission = result)
            if (result == NativeNotificationPermissionState.Granted) subscribe()
        }
    }

    private fun subscribe() {
        if (operationInFlight || subscriptionHandle != null) return
        val current = mutableState.value
        if (!current.paired ||
            !current.enabledByUser ||
            current.availability != CompletionSubscriptionAvailability.Available ||
            current.permission != NativeNotificationPermissionState.Granted
        ) {
            return
        }
        operationInFlight = true
        publish()
        val expectedGeneration = generation
        scope.launch {
            val newHandle = subscriptions.subscribe()
            if (generation != expectedGeneration) {
                subscriptions.unsubscribe(newHandle)
                return@launch
            }
            subscriptionHandle = newHandle
            operationInFlight = false
            publish()
        }
    }

    private fun openSettingsOnce() {
        if (settingsOpened) return
        settingsOpened = true
        publish()
        scope.launch { native.openSettings() }
    }

    private fun publish(
        paired: Boolean = mutableState.value.paired,
        availability: CompletionSubscriptionAvailability = mutableState.value.availability,
        permission: NativeNotificationPermissionState = mutableState.value.permission,
        enabled: Boolean = mutableState.value.enabledByUser,
    ) {
        mutableState.value =
            CompletionNotificationState(
                paired = paired,
                availability = availability,
                permission = permission,
                enabledByUser = enabled,
                subscriptionHandlePresent = subscriptionHandle != null,
                effective = effective(paired, availability, enabled, permission, subscriptionHandle, operationInFlight),
            )
        diagnostics.record(
            MobileDiagnosticEvent.NotificationStateChanged,
            when (mutableState.value.effective) {
                CompletionNotificationEffectiveState.Enabled -> MobileDiagnosticOutcome.Succeeded
                CompletionNotificationEffectiveState.PermissionDenied -> MobileDiagnosticOutcome.Rejected
                CompletionNotificationEffectiveState.IntegrationPending -> MobileDiagnosticOutcome.Unavailable
                else -> MobileDiagnosticOutcome.Started
            },
        )
    }

    private companion object {
        fun effective(
            paired: Boolean,
            availability: CompletionSubscriptionAvailability,
            enabled: Boolean,
            permission: NativeNotificationPermissionState,
            handle: String?,
            inFlight: Boolean,
        ): CompletionNotificationEffectiveState =
            when {
                !paired -> CompletionNotificationEffectiveState.Unpaired
                availability == CompletionSubscriptionAvailability.IntegrationPending ->
                    CompletionNotificationEffectiveState.IntegrationPending
                !enabled -> CompletionNotificationEffectiveState.Disabled
                handle != null -> CompletionNotificationEffectiveState.Enabled
                inFlight -> CompletionNotificationEffectiveState.Enabling
                permission == NativeNotificationPermissionState.Denied ->
                    CompletionNotificationEffectiveState.PermissionDenied
                else -> CompletionNotificationEffectiveState.PermissionRequired
            }
    }
}
