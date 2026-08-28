package com.ratatoskr.mobile.identity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStorageTest {
    private val namespace = "instrumentation"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = AndroidKeystoreCredentialStorage(context, namespace)

    @Before
    @After
    fun clearStorage() {
        runCatching { storage.clear() }
        context
            .getSharedPreferences(
                AndroidKeystoreCredentialStorage.preferencesName(namespace),
                Context.MODE_PRIVATE,
            ).edit()
            .clear()
            .commit()
    }

    @Test
    fun keystore_round_trip_replace_and_delete() {
        val first = credentials(accessToken = "access-first", refreshToken = "refresh-first")
        val replacement = credentials(accessToken = "access-next", refreshToken = "refresh-next")

        storage.save(first)
        assertEquals(first, storage.load())
        val persisted =
            context
                .getSharedPreferences(
                    AndroidKeystoreCredentialStorage.preferencesName(namespace),
                    Context.MODE_PRIVATE,
                ).all.values
                .joinToString()
        assertFalse(persisted.contains(first.deviceSecret))
        assertFalse(persisted.contains(first.accessToken))
        assertFalse(persisted.contains(first.refreshToken))

        storage.save(replacement)
        assertEquals(replacement, storage.load())

        storage.clear()
        assertNull(storage.load())
    }

    private fun credentials(
        accessToken: String,
        refreshToken: String,
    ) = DeviceCredentials(
        origin = "https://platform.example",
        userId = "user-1",
        deviceId = "device-1",
        deviceSecret = "root-secret",
        accessToken = accessToken,
        accessExpiresAt = "2026-08-28T11:00:00Z",
        refreshToken = refreshToken,
        refreshExpiresAt = "2026-09-28T11:00:00Z",
    )
}
