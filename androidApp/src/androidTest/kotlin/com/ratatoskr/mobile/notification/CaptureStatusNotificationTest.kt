package com.ratatoskr.mobile.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Test
    fun explicit_available_enable_requests_post_notifications_once() {
        val boundary = RecordingPermissionBoundary(requestResult = true)
        val fixture = availableStore(AndroidNotificationPermissionPort(boundary))

        fixture.store.enable()
        fixture.store.enable()

        assertEquals(1, boundary.requestCalls)
        assertEquals(1, fixture.subscriptions.subscribeCalls)
        assertEquals(CompletionNotificationEffectiveState.Enabled, fixture.store.state.value.effective)
    }

    @Test
    fun denied_state_opens_settings_without_reprompt() {
        val boundary = RecordingPermissionBoundary(requestResult = false)
        val port = AndroidNotificationPermissionPort(boundary)
        val subscriptions = RecordingSubscriptions()
        val store =
            CompletionNotificationStore(
                availability = CompletionSubscriptionAvailability.Available,
                paired = true,
                permission = NativeNotificationPermissionState.Denied,
                native = port,
                subscriptions = subscriptions,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

        store.enable()
        store.enable()

        assertEquals(0, boundary.requestCalls)
        assertEquals(1, boundary.settingsCalls)
        assertEquals(0, subscriptions.subscribeCalls)
        assertEquals(CompletionNotificationEffectiveState.PermissionDenied, store.state.value.effective)
    }

    @Test
    fun notification_route_and_payload_exclude_all_private_canaries() {
        val privateCanaries =
            listOf(
                "private-search-canary",
                "https://private.example.test/article",
                "Private article title",
                "private note",
                "private-file.pdf",
                "private-user@example.test",
            )
        val plan = requireNotNull(notifier().plan(OPERATION_ID, terminal = true))
        val rendered =
            listOf(
                plan.title,
                plan.text,
                plan.detailIntent.toUri(Intent.URI_INTENT_SCHEME),
                plan.detailIntent.extras
                    ?.keySet()
                    ?.joinToString()
                    .orEmpty(),
            ).joinToString(" ")

        privateCanaries.forEach { assertFalse(it, rendered.contains(it)) }
        assertEquals(setOf(MainActivity.EXTRA_OPERATION_ID), plan.detailIntent.extras?.keySet())
    }

    private fun notifier() =
        AndroidCaptureStatusNotifier(
            context = context,
            permission = NotificationPermission { true },
            sink = NativeNotificationSink { _: Int, _: Notification -> },
        )

    private fun availableStore(port: NativeNotificationPermissionPort): AndroidFixture {
        val subscriptions = RecordingSubscriptions()
        return AndroidFixture(
            store =
                CompletionNotificationStore(
                    availability = CompletionSubscriptionAvailability.Available,
                    paired = true,
                    permission = NativeNotificationPermissionState.NotDetermined,
                    native = port,
                    subscriptions = subscriptions,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                ),
            subscriptions = subscriptions,
        )
    }

    private data class AndroidFixture(
        val store: CompletionNotificationStore,
        val subscriptions: RecordingSubscriptions,
    )

    private class RecordingPermissionBoundary(
        private val requestResult: Boolean,
    ) : AndroidNotificationPermissionBoundary {
        var requestCalls = 0
        var settingsCalls = 0

        override suspend fun requestPostNotifications(): Boolean {
            requestCalls += 1
            return requestResult
        }

        override fun openNotificationSettings() {
            settingsCalls += 1
        }

        override fun cancelNotifications() = Unit
    }

    private class RecordingSubscriptions : CompletionSubscriptionPort {
        var subscribeCalls = 0

        override suspend fun subscribe(): String {
            subscribeCalls += 1
            return "fixture-handle"
        }

        override suspend fun unsubscribe(handle: String) = Unit
    }

    private companion object {
        const val OPERATION_ID = "0198f4b0-8f9a-7000-8000-000000000001"
    }
}
