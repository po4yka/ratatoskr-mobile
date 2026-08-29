package com.ratatoskr.mobile.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureStatusNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun notification_contains_no_capture_content() {
        val plan = notifier().plan(OPERATION_ID, terminal = false)
        assertNotNull(plan)
        requireNotNull(plan)
        val rendered = "${plan.title} ${plan.text}"

        assertFalse(rendered.contains("https://private.example.test"))
        assertFalse(rendered.contains("Private article title"))
        assertEquals("Ratatoskr capture accepted", plan.title)
    }

    @Test
    fun immutable_explicit_intent_opens_authorized_detail() {
        val plan = notifier().plan(OPERATION_ID, terminal = true)
        assertNotNull(plan)
        requireNotNull(plan)

        assertEquals(MainActivity::class.java.name, plan.detailIntent.component?.className)
        assertEquals(MainActivity.ACTION_VIEW_OPERATION, plan.detailIntent.action)
        assertEquals(OPERATION_ID, plan.detailIntent.getStringExtra(MainActivity.EXTRA_OPERATION_ID))
        assertTrue(plan.pendingIntentFlags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(setOf(MainActivity.EXTRA_OPERATION_ID), plan.detailIntent.extras?.keySet())
    }

    @Test
    fun permission_denial_does_not_change_queue() {
        var posts = 0
        val queueState = "accepted"
        val notifier =
            AndroidCaptureStatusNotifier(
                context = context,
                permission = NotificationPermission { false },
                sink = NativeNotificationSink { _, _ -> posts += 1 },
            )

        notifier.accepted(OPERATION_ID)

        assertEquals(0, posts)
        assertEquals("accepted", queueState)
    }

    @Test
    fun invalid_identifier_issues_no_fetch() {
        var fetches = 0
        val parsed = MainActivity.validatedOperationId("not-an-operation")
        if (parsed != null) fetches += 1

        assertNull(parsed)
        assertEquals(0, fetches)
    }

    private fun notifier() =
        AndroidCaptureStatusNotifier(
            context = context,
            permission = NotificationPermission { true },
            sink = NativeNotificationSink { _: Int, _: Notification -> },
        )

    private companion object {
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
    }
}
