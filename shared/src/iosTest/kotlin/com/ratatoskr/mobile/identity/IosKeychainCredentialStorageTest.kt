package com.ratatoskr.mobile.identity

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosKeychainCredentialStorageTest {
    private val storage =
        IosKeychainCredentialStorage(
            service = "com.ratatoskr.mobile.tests.device-identity",
            account = "round-trip",
        )

    @BeforeTest
    @AfterTest
    fun clearStorage() {
        runCatching { storage.clear() }
    }

    @Test
    // The unhosted Kotlin/Native runner receives errSecNotAvailable; app-hosted XCTest owns runtime proof.
    @Ignore
    fun keychain_round_trip_replace_and_delete() {
        val first = credentials(accessToken = "access-first", refreshToken = "refresh-first")
        val replacement = credentials(accessToken = "access-next", refreshToken = "refresh-next")

        storage.save(first)
        assertEquals(first, storage.load())
        assertEquals(
            IosKeychainPolicy(deviceOnly = true, synchronizing = false),
            storage.policy(),
        )

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
