package com.ratatoskr.mobile.notification

import android.app.Notification
import android.content.Intent

fun interface CaptureStatusNotifier {
    fun accepted(operationId: String)

    fun terminal(operationId: String) = accepted(operationId)
}

data class StatusNotificationPlan(
    val notificationId: Int,
    val title: String,
    val text: String,
    val detailIntent: Intent,
    val pendingIntentFlags: Int,
)

fun interface NotificationPermission {
    fun canPost(): Boolean
}

fun interface NativeNotificationSink {
    fun post(
        id: Int,
        notification: Notification,
    )
}
