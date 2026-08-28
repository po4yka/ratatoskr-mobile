package com.ratatoskr.mobile

import androidx.compose.ui.window.ComposeUIViewController
import com.ratatoskr.mobile.identity.createIosDeviceSessionManager
import platform.UIKit.UIViewController

@Suppress("ktlint:standard:function-naming")
fun MainViewController(): UIViewController {
    val sessionManager = createIosDeviceSessionManager()
    return ComposeUIViewController {
        RatatoskrApp(sessionManager)
    }
}
