package com.ratatoskr.mobile.capture

import com.ratatoskr.mobile.queue.QueueLimits
import com.ratatoskr.mobile.queue.QueueRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CaptureModelsTest {
    @Test
    fun supported_capture_kinds_round_trip() {
        val payloads =
            listOf(
                CapturePayload.Url("https://example.test/path?q=original"),
                CapturePayload.TextNote("Synthetic note"),
                CapturePayload.FileReference("stage-018f", "paper.pdf", "application/pdf", 4096),
            )

        payloads.forEach { payload ->
            val request = request(payload)
            assertNull(CaptureCodec.validate(request))
            assertEquals(request, CaptureCodec.decode(CaptureCodec.encode(request)))
        }
    }

    @Test
    fun invalid_capture_input_is_rejected_atomically() {
        val limits = QueueLimits(maxTextBytes = 4, maxStagedFileBytes = 10)
        val invalid =
            listOf(
                CapturePayload.Url("file:///private/item"),
                CapturePayload.Url("https://example.test/" + "x".repeat(2049)),
                CapturePayload.TextNote(""),
                CapturePayload.TextNote("12345"),
                CapturePayload.FileReference("../outside", "x", "text/plain", 1),
                CapturePayload.FileReference("stage-1", "x", "text/plain", 11),
            )

        invalid.forEach { payload ->
            assertEquals(QueueRejection.InvalidCapture, CaptureCodec.validate(request(payload), limits))
        }
    }

    private fun request(payload: CapturePayload) =
        CaptureRequest(
            owner = CaptureOwner("https://platform.example", "user-1"),
            source = CaptureSource.MainApp,
            payload = payload,
            createdAt = Instant.parse("2026-08-28T12:00:00Z"),
        )
}
