package com.ratatoskr.mobile.identity

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

fun createAndroidDeviceSessionManager(context: Context): DeviceSessionManager =
    DeviceSessionManager(
        api =
            KtorPlatformIdentityApi(
                HttpClient(OkHttp) {
                    followRedirects = false
                },
            ),
        storage = AndroidKeystoreCredentialStorage(context),
    )
