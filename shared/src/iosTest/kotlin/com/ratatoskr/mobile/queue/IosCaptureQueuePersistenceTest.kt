package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalForeignApi::class)
class IosCaptureQueuePersistenceTest {
    private val path = NSTemporaryDirectory() + "ratatoskr-queue-${NSUUID().UUIDString}.db"

    @AfterTest
    fun deleteDatabase() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun queue_survives_close_and_reopen() =
        runTest {
            val first = queue()
            val stored = success(first.enqueue(request()))
            first.close()

            val reopened = queue()
            assertEquals(stored, reopened.inspect(stored.localId))
            reopened.close()
        }

    @Test
    fun idempotency_key_stays_stable_across_reopen() =
        runTest {
            val first = queue()
            val stored = success(first.enqueue(request(), "idem-stable"))
            first.close()

            val reopened = queue()
            assertEquals("idem-stable", reopened.inspect(stored.localId)?.idempotencyKey)
            reopened.close()
        }

    @Test
    fun database_factory_applies_file_protection() =
        runTest {
            val protectedPaths = mutableListOf<String>()
            val queue =
                CaptureQueue(
                    persistence =
                        createIosQueuePersistence(
                            path,
                            IosQueueFileProtector { protectedPaths += it },
                        ),
                    clock = QueueClock { NOW },
                    keyGenerator = QueueKeyGenerator { "generated-key" },
                    jitter = QueueJitter { 0.0 },
                )
            queue.enqueue(request()).success()
            queue.close()

            assertEquals(path, protectedPaths.first())
        }

    private fun queue() =
        CaptureQueue(
            persistence = createIosQueuePersistence(path),
            clock = QueueClock { NOW },
            keyGenerator = QueueKeyGenerator { "generated-key" },
            jitter = QueueJitter { 0.0 },
        )

    private fun request() =
        CaptureRequest(
            CaptureOwner("https://platform.example", "user-1"),
            CaptureSource.IosShareExtension,
            CapturePayload.Url("https://example.test/article"),
            NOW,
        )

    private fun success(result: QueueResult<QueueRecord>) = (result as QueueResult.Success).value

    private companion object {
        val NOW = Instant.parse("2026-08-28T12:00:00Z")
    }
}
