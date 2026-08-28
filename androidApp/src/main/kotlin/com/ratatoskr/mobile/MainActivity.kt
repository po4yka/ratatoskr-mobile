package com.ratatoskr.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ratatoskr.mobile.identity.createAndroidDeviceSessionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = createAndroidDeviceSessionManager(applicationContext)
        setContent {
            RatatoskrApp(sessionManager)
        }
    }
}
