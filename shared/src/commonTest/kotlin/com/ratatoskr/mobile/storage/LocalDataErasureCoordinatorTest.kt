package com.ratatoskr.mobile.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalDataErasureCoordinatorTest {
    @Test
    fun confirmed_clear_erases_every_registered_store() =
        runTest {
            val marker = MemoryMarkerStore()
            val stores = listOf(FakeStore("credentials", 1, 10), FakeStore("queue", 2, 20), FakeStore("files", 3, 30))
            val coordinator = coordinator(marker, stores)

            val result = coordinator.clearData(confirmed = true)

            assertIs<ClearDataResult.Completed>(result)
            assertTrue(stores.all { it.inventory().empty })
            assertNull(marker.value)
            assertIs<LocalDataErasureState.Complete>(coordinator.state.value)
        }

    @Test
    fun cancel_is_noop() =
        runTest {
            val marker = MemoryMarkerStore()
            val store = FakeStore("queue", 2, 20)
            val coordinator = coordinator(marker, listOf(store))

            assertEquals(ClearDataResult.Cancelled, coordinator.clearData(confirmed = false))
            assertEquals(LocalDataInventory(2, 20), store.inventory())
            assertNull(marker.value)
        }

    @Test
    fun restart_completes_interrupted_marker() =
        runTest {
            val marker = MemoryMarkerStore(ErasureMarker("erase-old", ErasureReason.ConfirmedClearData))
            val store = FakeStore("files", 3, 30)
            val coordinator = coordinator(marker, listOf(store))

            val result = coordinator.resumeIfNeeded()

            assertIs<ClearDataResult.Completed>(result)
            assertTrue(store.inventory().empty)
            assertNull(marker.value)
        }

    @Test
    fun stale_callback_cannot_recreate_data() =
        runTest {
            val marker = MemoryMarkerStore()
            val coordinator = coordinator(marker, listOf(FakeStore("queue", 1, 1)))
            val oldGeneration = "before-wipe"

            coordinator.provenRevocation()

            assertFalse(coordinator.acceptsCallback(oldGeneration))
            assertTrue(coordinator.acceptsCallback("erase-new"))
        }

    private fun coordinator(
        marker: MemoryMarkerStore,
        stores: List<FakeStore>,
    ) = LocalDataErasureCoordinator(marker, stores) { "erase-new" }

    private class MemoryMarkerStore(
        var value: ErasureMarker? = null,
    ) : ErasureMarkerStore {
        override suspend fun load(): ErasureMarker? = value

        override suspend fun write(marker: ErasureMarker) {
            value = marker
        }

        override suspend fun remove(generation: String) {
            if (value?.generation == generation) value = null
        }
    }

    private class FakeStore(
        override val id: String,
        private var count: Int,
        private var bytes: Long,
    ) : LocalDataErasureParticipant {
        override suspend fun erase(generation: String) {
            count = 0
            bytes = 0
        }

        override suspend fun inventory() = LocalDataInventory(count, bytes)
    }
}
