package com.ratatoskr.mobile.storage

import com.ratatoskr.mobile.transfer.FileTransferAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LocalStorageState {
    data class Ready(
        val usage: StorageUsage,
        val fileTransferAvailability: FileTransferAvailability,
    ) : LocalStorageState

    data class ConfirmClear(
        val usage: StorageUsage,
    ) : LocalStorageState

    data object Erasing : LocalStorageState

    data object Empty : LocalStorageState

    data class Error(
        val message: String,
    ) : LocalStorageState
}

sealed interface LocalStorageAction {
    data object RequestClear : LocalStorageAction

    data object ConfirmClear : LocalStorageAction

    data object CancelClear : LocalStorageAction

    data object Cleanup : LocalStorageAction
}

class LocalStorageStore(
    initialUsage: StorageUsage,
    private val availability: FileTransferAvailability,
    private val cleanup: suspend () -> StorageUsage,
    private val erasure: suspend (Boolean) -> ClearDataResult,
    private val scope: CoroutineScope,
) {
    private var latestUsage = initialUsage
    private val mutableState =
        MutableStateFlow<LocalStorageState>(LocalStorageState.Ready(initialUsage, availability))
    val state: StateFlow<LocalStorageState> = mutableState.asStateFlow()

    fun dispatch(action: LocalStorageAction) {
        when (action) {
            LocalStorageAction.RequestClear -> {
                if (mutableState.value is LocalStorageState.Ready) {
                    mutableState.value = LocalStorageState.ConfirmClear(latestUsage)
                }
            }
            LocalStorageAction.CancelClear -> {
                if (mutableState.value is LocalStorageState.ConfirmClear) publishReady()
            }
            LocalStorageAction.ConfirmClear -> {
                if (mutableState.value !is LocalStorageState.ConfirmClear) return
                mutableState.value = LocalStorageState.Erasing
                scope.launch {
                    mutableState.value =
                        when (erasure(true)) {
                            is ClearDataResult.Completed -> LocalStorageState.Empty
                            is ClearDataResult.Failed -> LocalStorageState.Error("Local data could not be erased completely.")
                            ClearDataResult.Cancelled -> LocalStorageState.Error("Local data erasure was cancelled unexpectedly.")
                        }
                }
            }
            LocalStorageAction.Cleanup -> {
                if (mutableState.value !is LocalStorageState.Ready) return
                scope.launch {
                    latestUsage = cleanup()
                    publishReady()
                }
            }
        }
    }

    private fun publishReady() {
        mutableState.value = LocalStorageState.Ready(latestUsage, availability)
    }
}
