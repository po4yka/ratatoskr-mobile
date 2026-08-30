package com.ratatoskr.mobile.queue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.transfer.UploadCheckpoint
import com.ratatoskr.mobile.transfer.generated.TransferBlobRef
import com.ratatoskr.mobile.transfer.generated.TransferContentDigest
import com.ratatoskr.mobile.transfer.generated.UploadSessionRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours
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

    @Test
    fun file_transfer_survives_database_reopen_with_same_identity() =
        runBlocking {
            val first = queue()
            val stored = success(first.enqueue(fileRequest(), "idem-file"))
            first.recordUploadCheckpoint(stored.localId, checkpoint(stored))
            first.recordUploadReceipt(stored.localId, receipt())
            first.close()

            val reopened = queue()
            val restored = reopened.inspect(stored.localId)!!
            assertEquals(stored.localId, restored.uploadCheckpoint?.captureLocalId)
            assertEquals("idem-file", restored.uploadCheckpoint?.captureIdempotencyKey)
            assertEquals(setOf(0), restored.uploadCheckpoint?.receivedChunks)
            assertEquals(receipt(), restored.uploadReceipt)
            assertEquals(stored.sourceSequence, restored.sourceSequence)
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

    private fun fileRequest() =
        CaptureRequest(
            CaptureOwner("https://platform.example", "user-1"),
            CaptureSource.AndroidShareTarget,
            CapturePayload.FileReference("artifact-1", "synthetic.pdf", "application/pdf", 3),
            NOW,
        )

    private fun checkpoint(record: QueueRecord) =
        UploadCheckpoint(
            record.localId,
            record.idempotencyKey,
            UploadSessionRequest(
                chunkSizeBytes = 65_536,
                declaredSizeBytes = 3,
                digest = DIGEST,
                mediaType = "application/pdf",
            ),
            "resume-token",
            NOW + 1.hours,
            setOf(0),
        )

    private fun receipt() =
        TransferBlobRef(
            digest = DIGEST,
            lengthBytes = 3,
            mediaType = "application/pdf",
            ownerService = "blob-store",
        )

    private fun success(result: QueueResult<QueueRecord>) = (result as QueueResult.Success).value

    private companion object {
        val NOW = Instant.parse("2026-08-28T12:00:00Z")
        val DIGEST = TransferContentDigest("sha256", "a".repeat(64))
    }
}
