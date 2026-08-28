package com.ratatoskr.mobile.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

fun createIosDeviceSessionManager(): DeviceSessionManager =
    DeviceSessionManager(
        api =
            KtorPlatformIdentityApi(
                HttpClient(Darwin) {
                    followRedirects = false
                },
            ),
        storage = IosKeychainCredentialStorage(),
    )
