package com.ratatoskr.mobile.storage

import com.ratatoskr.mobile.transfer.FileTransferAvailability
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalStorageStoreTest {
    @Test
    fun usage_projection_is_truthful_and_content_free() =
        runTest {
            val store = store()

            val ready = assertIs<LocalStorageState.Ready>(store.state.value)
            assertEquals(USAGE, ready.usage)
            assertEquals(FileTransferAvailability.IntegrationPending, ready.fileTransferAvailability)
            assertEquals(8, ready.usage.artifactCount)
        }

    @Test
    fun clear_requires_one_shot_confirmation() =
        runTest {
            var eraseCalls = 0
            val store =
                store {
                    eraseCalls += 1
                    ClearDataResult.Completed("erase-1")
                }

            store.dispatch(LocalStorageAction.RequestClear)
            assertIs<LocalStorageState.ConfirmClear>(store.state.value)
            assertEquals(0, eraseCalls)

            store.dispatch(LocalStorageAction.ConfirmClear)
            runCurrent()

            assertEquals(1, eraseCalls)
            assertEquals(LocalStorageState.Empty, store.state.value)
        }

    @Test
    fun cancel_changes_nothing() =
        runTest {
            var eraseCalls = 0
            val store =
                store {
                    eraseCalls += 1
                    ClearDataResult.Completed("erase-1")
                }
            store.dispatch(LocalStorageAction.RequestClear)

            store.dispatch(LocalStorageAction.CancelClear)

            assertIs<LocalStorageState.Ready>(store.state.value)
            assertEquals(0, eraseCalls)
        }

    @Test
    fun erase_failure_stays_visible() =
        runTest {
            val store = store { ClearDataResult.Failed("erase-1") }
            store.dispatch(LocalStorageAction.RequestClear)
            store.dispatch(LocalStorageAction.ConfirmClear)
            runCurrent()

            assertIs<LocalStorageState.Error>(store.state.value)
        }

    private fun kotlinx.coroutines.test.TestScope.store(
        erase: suspend (Boolean) -> ClearDataResult = { ClearDataResult.Completed("erase-1") },
    ) = LocalStorageStore(
        initialUsage = USAGE,
        availability = FileTransferAvailability.IntegrationPending,
        cleanup = { USAGE },
        erasure = erase,
        scope = this,
    )

    private companion object {
        val USAGE = StorageUsage(360, 8, 60, 0, 70, 150, 512L * 1024L * 1024L, 64)
    }
}
