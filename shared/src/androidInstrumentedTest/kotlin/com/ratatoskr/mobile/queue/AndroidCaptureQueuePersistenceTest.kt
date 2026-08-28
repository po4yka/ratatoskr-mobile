package com.ratatoskr.mobile.queue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidCaptureQueuePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "capture-queue-instrumentation.db"

    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun queue_survives_close_and_reopen() =
        runBlocking {
            val first = queue()
            val stored = success(first.enqueue(request()))
            first.close()

            val reopened = queue()
            assertEquals(stored, reopened.inspect(stored.localId))
            reopened.close()
        }

    @Test
    fun idempotency_key_stays_stable_across_reopen() =
        runBlocking {
            val first = queue()
            val stored = success(first.enqueue(request(), "idem-stable"))
            first.close()

            val reopened = queue()
            assertEquals("idem-stable", reopened.inspect(stored.localId)?.idempotencyKey)
            reopened.close()
        }

    private fun queue() =
        CaptureQueue(
            persistence = createAndroidQueuePersistence(context, databaseName),
            clock = QueueClock { NOW },
            keyGenerator = QueueKeyGenerator { "generated-key" },
            jitter = QueueJitter { 0.0 },
        )

    private fun request() =
        CaptureRequest(
            CaptureOwner("https://platform.example", "user-1"),
            CaptureSource.AndroidShareTarget,
            CapturePayload.Url("https://example.test/article"),
            NOW,
        )

    private fun success(result: QueueResult<QueueRecord>) = (result as QueueResult.Success).value

    private companion object {
        val NOW = Instant.parse("2026-08-28T12:00:00Z")
    }
}
