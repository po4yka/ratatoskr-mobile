package com.ratatoskr.mobile.queue

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import kotlin.time.Instant

internal class MemoryQueuePersistence : QueuePersistence {
    private val stored = mutableListOf<QueueRecord>()

    override suspend fun <T> transaction(block: suspend QueueTransaction.() -> T): T {
        val before = stored.toList()
        return try {
            block(
                object : QueueTransaction {
                    override suspend fun records(): List<QueueRecord> = stored.toList()

                    override suspend fun insert(record: QueueRecord) {
                        check(stored.none { it.localId == record.localId })
                        check(stored.none { it.idempotencyKey == record.idempotencyKey })
                        stored += record
                    }

                    override suspend fun update(record: QueueRecord) {
                        val index = stored.indexOfFirst { it.localId == record.localId }
                        check(index >= 0)
                        stored[index] = record
                    }
                },
            )
        } catch (error: Throwable) {
            stored.clear()
            stored += before
            throw error
        }
    }

    override fun close() = Unit
}

internal class MutableQueueClock(
    var current: Instant = NOW,
) : QueueClock {
    override fun now(): Instant = current
}

internal class SequenceQueueKeyGenerator(
    private var next: Int = 1,
) : QueueKeyGenerator {
    override fun next(): String = "generated-${next++}"
}

internal fun testQueue(
    persistence: QueuePersistence = MemoryQueuePersistence(),
    clock: MutableQueueClock = MutableQueueClock(),
    limits: QueueLimits = QueueLimits(),
    jitter: QueueJitter = QueueJitter { 0.0 },
    keyGenerator: QueueKeyGenerator = SequenceQueueKeyGenerator(),
) = CaptureQueue(
    persistence = persistence,
    clock = clock,
    keyGenerator = keyGenerator,
    jitter = jitter,
    limits = limits,
)

internal fun captureRequest(
    owner: CaptureOwner = OWNER_A,
    source: CaptureSource = CaptureSource.MainApp,
    payload: CapturePayload = CapturePayload.Url("https://example.test/article"),
    createdAt: Instant = NOW,
) = CaptureRequest(owner, source, payload, createdAt)

internal fun QueueResult<QueueRecord>.success(): QueueRecord = (this as QueueResult.Success).value

internal fun QueueResult<QueueRecord>.failure(): QueueRejection = (this as QueueResult.Failure).reason

internal val NOW = Instant.parse("2026-08-28T12:00:00Z")
internal val OWNER_A = CaptureOwner("https://platform-a.example", "account-a")
internal val OWNER_B = CaptureOwner("https://platform-b.example", "account-b")
