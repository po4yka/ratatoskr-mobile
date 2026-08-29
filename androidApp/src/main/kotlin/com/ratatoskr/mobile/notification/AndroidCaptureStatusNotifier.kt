package com.ratatoskr.mobile.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ratatoskr.mobile.MainActivity

class AndroidCaptureStatusNotifier(
    private val context: Context,
    private val permission: NotificationPermission,
    private val sink: NativeNotificationSink,
) : CaptureStatusNotifier {
    internal fun plan(
        operationId: String,
        terminal: Boolean,
    ): StatusNotificationPlan? {
        val validated = MainActivity.validatedOperationId(operationId) ?: return null
        return StatusNotificationPlan(
            notificationId = validated.hashCode(),
            title = if (terminal) "Ratatoskr capture finished" else "Ratatoskr capture accepted",
            text = if (terminal) "Open Ratatoskr to review the result." else "Processing will continue in Ratatoskr.",
            detailIntent =
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_VIEW_OPERATION)
                    .putExtra(MainActivity.EXTRA_OPERATION_ID, validated),
            pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun accepted(operationId: String) = post(operationId, terminal = false)

    override fun terminal(operationId: String) = post(operationId, terminal = true)

    private fun post(
        operationId: String,
        terminal: Boolean,
    ) {
        if (!permission.canPost()) return
        val plan = plan(operationId, terminal) ?: return
        ensureChannel()
        val contentIntent =
            PendingIntent.getActivity(
                context,
                plan.notificationId,
                plan.detailIntent,
                plan.pendingIntentFlags,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(plan.title)
                .setContentText(plan.text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
        sink.post(plan.notificationId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Capture status",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "ratatoskr_capture_status"
    }
}

fun createAndroidCaptureStatusNotifier(context: Context): CaptureStatusNotifier {
    val appContext = context.applicationContext
    val manager = appContext.getSystemService(NotificationManager::class.java)
    return AndroidCaptureStatusNotifier(
        context = appContext,
        permission =
            NotificationPermission {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            },
        sink = NativeNotificationSink(manager::notify),
    )
}
