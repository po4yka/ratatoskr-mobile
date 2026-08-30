package com.ratatoskr.mobile.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileUploadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun maps_connected_battery_not_low_and_storage_not_low_constraints() {
        val request = FileUploadWorkScheduler(context).createRequest("owner-opaque", "generation-1")

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
        assertTrue(request.workSpec.constraints.requiresStorageNotLow())
        assertEquals(
            setOf(FileUploadWorkScheduler.INPUT_OWNER_WORK_KEY, FileUploadWorkScheduler.INPUT_GENERATION),
            request.workSpec.input.keyValueMap.keys,
        )
        assertFalse(
            request.workSpec.input.keyValueMap.values
                .any { it.toString().contains("token") },
        )
    }

    @Test
    fun stale_worker_generation_cannot_write_after_cancel() {
        var current = "generation-1"
        val fence = FileUploadWorkGenerationFence { current }
        assertTrue(fence.accepts("generation-1"))

        current = "generation-2"

        assertFalse(fence.accepts("generation-1"))
        assertTrue(fence.accepts("generation-2"))
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun initializeWorkManager() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build(),
            )
        }
    }
}
