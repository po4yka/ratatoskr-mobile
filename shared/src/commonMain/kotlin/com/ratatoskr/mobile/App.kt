package com.ratatoskr.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.ratatoskr.mobile.identity.DeviceIdentityAction
import com.ratatoskr.mobile.identity.DeviceIdentityUiState
import com.ratatoskr.mobile.identity.DeviceIdentityViewModel
import com.ratatoskr.mobile.identity.DeviceSessionManager
import com.ratatoskr.mobile.identity.IdentityFailure
import com.ratatoskr.mobile.operation.OperationDetailRoute
import com.ratatoskr.mobile.operation.OperationDetailStore
import com.ratatoskr.mobile.operation.OperationDetailSurface
import com.ratatoskr.mobile.operation.OperationListRoute
import com.ratatoskr.mobile.operation.OperationListStore
import com.ratatoskr.mobile.operation.OperationListSurface
import com.ratatoskr.mobile.share.ShareIntakeRejection
import com.ratatoskr.mobile.share.ShareRejectionSurface
import com.ratatoskr.mobile.share.ShareStagingStore
import com.ratatoskr.mobile.share.ShareStagingSurface

@Composable
@Suppress("ktlint:standard:function-naming")
fun RatatoskrApp(
    sessionManager: DeviceSessionManager,
    shareStore: ShareStagingStore? = null,
    shareRejection: ShareIntakeRejection? = null,
    onDismissShareRejection: () -> Unit = {},
    operationListStore: OperationListStore? = null,
    initialOperationId: String? = null,
    operationDetailStore: ((String) -> OperationDetailStore)? = null,
    detailPollingVisible: Boolean = true,
    onDetailStoreActive: (OperationDetailStore?) -> Unit = {},
) {
    val identityViewModel = viewModel { DeviceIdentityViewModel(sessionManager) }
    val state by identityViewModel.uiState.collectAsState()
    var route: NavKey? by remember(initialOperationId) {
        mutableStateOf(initialOperationId?.let(::OperationDetailRoute))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (shareStore != null) {
            val stagingState by shareStore.state.collectAsState()
            ShareStagingSurface(stagingState, shareStore::dispatch)
        } else if (shareRejection != null) {
            ShareRejectionSurface(shareRejection, onDismissShareRejection)
        } else {
            when (val currentRoute = route) {
                OperationListRoute -> {
                    val store = operationListStore
                    if (store == null) {
                        IdentitySurface(state, identityViewModel::dispatch) { route = OperationListRoute }
                    } else {
                        val operations by store.state.collectAsState()
                        LaunchedEffect(store) { store.refresh() }
                        OperationListSurface(
                            state = operations,
                            onRefresh = store::refresh,
                            onOpen = { route = OperationDetailRoute(it) },
                        )
                    }
                }
                is OperationDetailRoute -> {
                    val store =
                        remember(currentRoute.operationId) {
                            operationDetailStore?.invoke(currentRoute.operationId)
                        }
                    if (store == null) {
                        IdentitySurface(state, identityViewModel::dispatch) { route = OperationListRoute }
                    } else {
                        val detail by store.state.collectAsState()
                        DisposableEffect(store) {
                            onDetailStoreActive(store)
                            onDispose {
                                store.setVisible(false)
                                onDetailStoreActive(null)
                            }
                        }
                        DisposableEffect(store, detailPollingVisible) {
                            store.setVisible(detailPollingVisible)
                            onDispose { store.setVisible(false) }
                        }
                        OperationDetailSurface(
                            state = detail,
                            onRetry = store::retry,
                            onPair = { route = null },
                        )
                    }
                }
                else -> IdentitySurface(state, identityViewModel::dispatch) { route = OperationListRoute }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun IdentitySurface(
    state: DeviceIdentityUiState,
    dispatch: (DeviceIdentityAction) -> Unit,
    onOpenOperations: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("Ratatoskr", style = TextStyle(fontSize = 30.sp))
        when (state) {
            is DeviceIdentityUiState.PairingForm -> PairingForm(state, dispatch)
            DeviceIdentityUiState.Working -> BasicText("Working…")
            is DeviceIdentityUiState.Paired -> PairedSession(state, dispatch, onOpenOperations)
            DeviceIdentityUiState.RePairingRequired -> {
                BasicText("This device session was revoked. Pair it again to continue.")
                ActionButton("Pair again") { dispatch(DeviceIdentityAction.SignOut) }
            }
            is DeviceIdentityUiState.Failed -> BasicText(state.failure.safeMessage())
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairingForm(
    state: DeviceIdentityUiState.PairingForm,
    dispatch: (DeviceIdentityAction) -> Unit,
) {
    BasicText("Pair this device with your Platform identity.")
    PairingTextField(
        value = state.origin,
        onValueChange = { dispatch(DeviceIdentityAction.OriginChanged(it)) },
        label = "Platform HTTPS origin",
    )
    PairingTextField(
        value = state.code,
        onValueChange = { dispatch(DeviceIdentityAction.CodeChanged(it)) },
        label = "Pairing code",
        visualTransformation = PasswordVisualTransformation(),
    )
    PairingTextField(
        value = state.displayName,
        onValueChange = { dispatch(DeviceIdentityAction.DisplayNameChanged(it)) },
        label = "Device name (optional)",
    )
    state.error?.let { BasicText(it.safeMessage(), style = TextStyle(color = Color(0xFFB00020))) }
    ActionButton(
        label = "Pair device",
        enabled = state.origin.isNotBlank() && state.code.isNotBlank(),
        onClick = { dispatch(DeviceIdentityAction.SubmitPairing) },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairedSession(
    state: DeviceIdentityUiState.Paired,
    dispatch: (DeviceIdentityAction) -> Unit,
    onOpenOperations: () -> Unit,
) {
    BasicText("Paired with ${state.origin}")
    BasicText(if (state.capabilitiesFresh) "Capabilities are current" else "Capabilities are unavailable or stale")
    if (state.capabilities.isNotEmpty()) {
        BasicText(state.capabilities.sorted().joinToString(separator = "\n"))
    }
    ActionButton("Refresh capabilities") { dispatch(DeviceIdentityAction.RefreshCapabilities) }
    ActionButton("Operations", onClick = onOpenOperations)
    ActionButton("Sign out on this device") { dispatch(DeviceIdentityAction.SignOut) }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun PairingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray)
                    .padding(12.dp),
            singleLine = true,
            visualTransformation = visualTransformation,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (enabled) Color(0xFF315C9D) else Color.LightGray
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(background)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = if (enabled) Color.White else Color.DarkGray))
    }
}

private fun IdentityFailure.safeMessage(): String =
    when (this) {
        IdentityFailure.InvalidOrigin -> "Enter a canonical HTTPS Platform origin."
        IdentityFailure.Validation -> "The pairing request is not valid."
        IdentityFailure.PairingRefused -> "The pairing code cannot be accepted."
        IdentityFailure.Unauthorized -> "Authorization is no longer available."
        is IdentityFailure.Unavailable -> "Platform is temporarily unavailable."
        IdentityFailure.Uncertain -> "The pairing outcome is uncertain. Request a new code."
        IdentityFailure.InvalidResponse -> "Platform returned an invalid response."
        IdentityFailure.SecureStorage -> "Secure device storage is unavailable."
    }
