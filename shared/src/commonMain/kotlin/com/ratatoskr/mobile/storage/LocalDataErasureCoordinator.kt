package com.ratatoskr.mobile.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ErasureReason {
    ConfirmedClearData,
    ProvenRemoteRevocation,
}

data class ErasureMarker(
    val generation: String,
    val reason: ErasureReason,
)

data class LocalDataInventory(
    val itemCount: Int,
    val bytes: Long,
) {
    val empty: Boolean get() = itemCount == 0 && bytes == 0L
}

interface ErasureMarkerStore {
    suspend fun load(): ErasureMarker?

    suspend fun write(marker: ErasureMarker)

    suspend fun remove(generation: String)
}

interface LocalDataErasureParticipant {
    val id: String

    suspend fun erase(generation: String)

    suspend fun inventory(): LocalDataInventory
}

sealed interface LocalDataErasureState {
    data object Idle : LocalDataErasureState

    data class Erasing(
        val generation: String,
    ) : LocalDataErasureState

    data class Complete(
        val generation: String,
    ) : LocalDataErasureState

    data class Failed(
        val generation: String,
        val participants: Set<String>,
    ) : LocalDataErasureState
}

sealed interface ClearDataResult {
    data object Cancelled : ClearDataResult

    data class Completed(
        val generation: String,
    ) : ClearDataResult

    data class Failed(
        val generation: String,
    ) : ClearDataResult
}

class LocalDataErasureCoordinator(
    private val markerStore: ErasureMarkerStore,
    private val participants: List<LocalDataErasureParticipant>,
    private val generation: () -> String,
) {
    private val mutex = Mutex()
    private var activeGeneration: String? = null
    private val mutableState = MutableStateFlow<LocalDataErasureState>(LocalDataErasureState.Idle)
    val state: StateFlow<LocalDataErasureState> = mutableState.asStateFlow()

    suspend fun clearData(confirmed: Boolean): ClearDataResult =
        if (confirmed) {
            erase(
                ErasureMarker(generation(), ErasureReason.ConfirmedClearData),
                writeMarker = true,
            )
        } else {
            ClearDataResult.Cancelled
        }

    suspend fun provenRevocation(): ClearDataResult =
        erase(ErasureMarker(generation(), ErasureReason.ProvenRemoteRevocation), writeMarker = true)

    suspend fun resumeIfNeeded(): ClearDataResult? = markerStore.load()?.let { erase(it, writeMarker = false) }

    fun acceptsCallback(generation: String): Boolean = activeGeneration == generation

    private suspend fun erase(
        marker: ErasureMarker,
        writeMarker: Boolean,
    ): ClearDataResult =
        mutex.withLock {
            if (writeMarker) markerStore.write(marker)
            activeGeneration = marker.generation
            mutableState.value = LocalDataErasureState.Erasing(marker.generation)
            val residue = mutableSetOf<String>()
            participants.forEach { participant ->
                val erased = runCatching { participant.erase(marker.generation) }.isSuccess
                val empty = runCatching { participant.inventory().empty }.getOrDefault(false)
                if (!erased || !empty) residue += participant.id
            }
            if (residue.isNotEmpty()) {
                mutableState.value = LocalDataErasureState.Failed(marker.generation, residue)
                ClearDataResult.Failed(marker.generation)
            } else {
                markerStore.remove(marker.generation)
                mutableState.value = LocalDataErasureState.Complete(marker.generation)
                ClearDataResult.Completed(marker.generation)
            }
        }
}
