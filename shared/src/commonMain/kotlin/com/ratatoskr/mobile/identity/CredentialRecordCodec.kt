package com.ratatoskr.mobile.identity

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object CredentialRecordCodec {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    fun encode(credentials: DeviceCredentials): ByteArray = json.encodeToString(credentials).encodeToByteArray()

    fun decode(bytes: ByteArray): DeviceCredentials =
        try {
            json.decodeFromString(bytes.decodeToString())
        } catch (_: SerializationException) {
            throw SecureCredentialStorageException()
        } catch (_: IllegalArgumentException) {
            throw SecureCredentialStorageException()
        }
}
