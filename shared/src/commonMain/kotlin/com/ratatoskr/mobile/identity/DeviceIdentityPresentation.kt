package com.ratatoskr.mobile.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DeviceIdentityUiState {
    data class PairingForm(
        val origin: String = "",
        val code: String = "",
        val displayName: String = "",
        val error: IdentityFailure? = null,
    ) : DeviceIdentityUiState

    data object Working : DeviceIdentityUiState

    data class Paired(
        val origin: String,
        val capabilities: Set<String>,
        val capabilitiesFresh: Boolean,
    ) : DeviceIdentityUiState

    data object RePairingRequired : DeviceIdentityUiState

    data class Failed(
        val failure: IdentityFailure,
    ) : DeviceIdentityUiState
}

sealed interface DeviceIdentityAction {
    data class OriginChanged(
        val value: String,
    ) : DeviceIdentityAction

    data class CodeChanged(
        val value: String,
    ) : DeviceIdentityAction

    data class DisplayNameChanged(
        val value: String,
    ) : DeviceIdentityAction

    data object SubmitPairing : DeviceIdentityAction

    data object RetryAuthorization : DeviceIdentityAction

    data object RefreshCapabilities : DeviceIdentityAction

    data object SignOut : DeviceIdentityAction
}

class DeviceIdentityStore(
    private val manager: DeviceSessionManager,
    private val scope: CoroutineScope,
) {
    private val pairingForm = MutableStateFlow(DeviceIdentityUiState.PairingForm())
    val uiState: StateFlow<DeviceIdentityUiState> =
        combine(
            manager.state,
            manager.capabilities,
            pairingForm,
            ::projectUiState,
        ).stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DeviceIdentityUiState.Working,
        )

    init {
        scope.launch { manager.restore() }
    }

    fun dispatch(action: DeviceIdentityAction) {
        when (action) {
            is DeviceIdentityAction.OriginChanged ->
                pairingForm.value =
                    pairingForm.value.copy(
                        origin = action.value,
                        error = null,
                    )
            is DeviceIdentityAction.CodeChanged ->
                pairingForm.value =
                    pairingForm.value.copy(
                        code = action.value,
                        error = null,
                    )
            is DeviceIdentityAction.DisplayNameChanged ->
                pairingForm.value =
                    pairingForm.value.copy(
                        displayName = action.value,
                        error = null,
                    )
            DeviceIdentityAction.SubmitPairing -> submitPairing()
            DeviceIdentityAction.RetryAuthorization -> scope.launch { manager.refreshSession() }
            DeviceIdentityAction.RefreshCapabilities -> scope.launch { manager.refreshCapabilities() }
            DeviceIdentityAction.SignOut -> scope.launch { manager.signOut() }
        }
    }

    private fun submitPairing() {
        val submitted = pairingForm.value
        pairingForm.value = submitted.copy(code = "", error = null)
        scope.launch {
            manager.pair(
                origin = submitted.origin,
                code = submitted.code,
                displayName = submitted.displayName.ifBlank { null },
            )
        }
    }

    private fun projectUiState(
        identity: DeviceIdentityState,
        capabilities: CapabilityState,
        form: DeviceIdentityUiState.PairingForm,
    ): DeviceIdentityUiState =
        when (identity) {
            DeviceIdentityState.SignedOut -> form
            DeviceIdentityState.Restoring,
            DeviceIdentityState.Pairing,
            DeviceIdentityState.Refreshing,
            -> DeviceIdentityUiState.Working
            is DeviceIdentityState.Paired ->
                when (capabilities) {
                    is CapabilityState.Ready ->
                        DeviceIdentityUiState.Paired(
                            origin = identity.origin,
                            capabilities = capabilities.snapshot.names,
                            capabilitiesFresh = true,
                        )
                    is CapabilityState.Stale ->
                        DeviceIdentityUiState.Paired(
                            origin = identity.origin,
                            capabilities = capabilities.snapshot?.names.orEmpty(),
                            capabilitiesFresh = false,
                        )
                    CapabilityState.Empty,
                    CapabilityState.Loading,
                    ->
                        DeviceIdentityUiState.Paired(
                            origin = identity.origin,
                            capabilities = emptySet(),
                            capabilitiesFresh = false,
                        )
                }
            DeviceIdentityState.RePairingRequired -> DeviceIdentityUiState.RePairingRequired
            is DeviceIdentityState.Failed ->
                when (identity.failure) {
                    IdentityFailure.InvalidOrigin,
                    IdentityFailure.PairingRefused,
                    IdentityFailure.Uncertain,
                    IdentityFailure.Validation,
                    is IdentityFailure.Unavailable,
                    -> form.copy(error = identity.failure)
                    IdentityFailure.InvalidResponse,
                    IdentityFailure.SecureStorage,
                    IdentityFailure.Unauthorized,
                    -> DeviceIdentityUiState.Failed(identity.failure)
                }
        }
}

class DeviceIdentityViewModel(
    manager: DeviceSessionManager,
) : ViewModel() {
    private val store = DeviceIdentityStore(manager, viewModelScope)
    val uiState: StateFlow<DeviceIdentityUiState> = store.uiState

    fun dispatch(action: DeviceIdentityAction) = store.dispatch(action)
}
