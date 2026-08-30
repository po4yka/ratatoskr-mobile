package com.ratatoskr.mobile.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.ratatoskr.mobile.presentation.AccessibleAction
import com.ratatoskr.mobile.transfer.FileTransferAvailability
import kotlinx.serialization.Serializable

@Serializable
data object LocalStorageRoute : NavKey

@Composable
@Suppress("ktlint:standard:function-naming")
fun LocalStorageSurface(
    state: LocalStorageState,
    dispatch: (LocalStorageAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText("Local storage", Modifier.semantics { heading() }, TextStyle(fontSize = 28.sp))
        when (state) {
            is LocalStorageState.Ready -> {
                val usage = state.usage
                BasicText("${usage.artifactCount} staged items · ${usage.totalBytes} bytes")
                BasicText("Queued: ${usage.queuedBytes} bytes")
                BasicText("Uploading: ${usage.uploadingBytes} bytes")
                BasicText("Awaiting Platform acceptance: ${usage.receiptPendingBytes} bytes")
                BasicText("Reclaimable: ${usage.reclaimableBytes} bytes")
                BasicText("Limit: ${usage.capacityBytes} bytes / ${usage.capacityCount} items")
                if (state.fileTransferAvailability == FileTransferAvailability.IntegrationPending) {
                    BasicText("File submission is integration pending; staged files stay local.")
                }
                StorageButton("Clean up reclaimable files") { dispatch(LocalStorageAction.Cleanup) }
                StorageButton("Clear all local Ratatoskr data") { dispatch(LocalStorageAction.RequestClear) }
            }
            is LocalStorageState.ConfirmClear -> {
                BasicText(
                    "Erase ${state.usage.artifactCount} staged items (${state.usage.totalBytes} bytes), " +
                        "queued captures, credentials, caches, and preferences from this device?",
                )
                StorageButton("Erase all local data") { dispatch(LocalStorageAction.ConfirmClear) }
                StorageButton("Cancel") { dispatch(LocalStorageAction.CancelClear) }
            }
            LocalStorageState.Erasing -> BasicText("Erasing local data…")
            LocalStorageState.Empty ->
                BasicText("All local Ratatoskr data was erased. Restart Ratatoskr before pairing again.")
            is LocalStorageState.Error -> BasicText(state.message)
        }
        StorageButton("Back", onClick = onBack)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun StorageButton(
    label: String,
    onClick: () -> Unit,
) {
    AccessibleAction(label, onClick = onClick)
}
