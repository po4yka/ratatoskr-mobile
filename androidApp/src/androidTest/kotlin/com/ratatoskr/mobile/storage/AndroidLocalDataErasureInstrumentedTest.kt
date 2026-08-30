package com.ratatoskr.mobile.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.identity.AndroidKeystoreCredentialStorage
import com.ratatoskr.mobile.identity.DeviceCredentials
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidLocalDataErasureInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val credentials = AndroidKeystoreCredentialStorage(context, namespace = PREF_CREDENTIALS)
    private val boundary = FakeBoundary()
    private val staging = File(context.noBackupFilesDir, "erase-staging")
    private val temporary = File(context.noBackupFilesDir, "erase-temp")
    private val ownedCache = File(context.cacheDir, "ratatoskr-erase")

    @After
    fun cleanup() {
        context.deleteDatabase(DATABASE)
        context.deleteSharedPreferences(PREF_FEATURES)
        context.deleteSharedPreferences(PREF_CREDENTIALS)
        staging.deleteRecursively()
        temporary.deleteRecursively()
        ownedCache.deleteRecursively()
        File(context.filesDir, "ratatoskr-erasure.marker").delete()
    }

    @Test
    fun complete_wipe_removes_every_registered_android_store() {
        seed()
        val eraser = eraser()

        assertTrue(eraser.begin("confirmed_clear_data"))

        assertTrue(eraser.inventory().empty)
        assertFalse(eraser.markerExists())
    }

    @Test
    fun interrupted_marker_finishes_before_store_reopen() {
        seed()
        File(context.filesDir, "ratatoskr-erasure.marker")
            .writeText("00000000-0000-4000-8000-000000000001:remote_revoke")

        val eraser = eraser()

        assertTrue(eraser.resumeIfNeeded())
        assertTrue(eraser.inventory().empty)
        assertFalse(eraser.markerExists())
    }

    private fun seed() {
        credentials.save(
            DeviceCredentials(
                "https://platform.example",
                "user-1",
                "device-1",
                "device-secret",
                "access-token",
                "2026-09-01T00:00:00Z",
                "refresh-token",
                "2026-10-01T00:00:00Z",
            ),
        )
        context.getDatabasePath(DATABASE).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        File("${context.getDatabasePath(DATABASE).path}-wal").writeBytes(byteArrayOf(4))
        File("${context.getDatabasePath(DATABASE).path}-shm").writeBytes(byteArrayOf(5))
        staging.mkdirs()
        File(staging, "artifact-1").writeBytes(byteArrayOf(1))
        temporary.mkdirs()
        File(temporary, ".partial").writeBytes(byteArrayOf(2))
        ownedCache.mkdirs()
        File(ownedCache, "cache-1").writeBytes(byteArrayOf(3))
        context
            .getSharedPreferences(PREF_FEATURES, Context.MODE_PRIVATE)
            .edit()
            .putString("state", "seeded")
            .commit()
        boundary.residue = 2
    }

    private fun eraser() =
        AndroidLocalDataErasure(
            context = context,
            credentials = credentials,
            closeQueue = {},
            databaseNames = listOf(DATABASE),
            stagedRoots = listOf(staging, temporary),
            preferenceNames = listOf(PREF_FEATURES),
            cacheRoots = listOf(ownedCache),
            boundary = boundary,
        )

    private class FakeBoundary : AndroidEraseBoundary {
        var residue = 0

        override fun cancelWorkAndNotifications() {
            residue = 0
        }

        override fun residueCount(): Int = residue
    }

    private companion object {
        const val DATABASE = "erase-queue.db"
        const val PREF_FEATURES = "erase-features"
        const val PREF_CREDENTIALS = "erase-credentials"
    }
}
