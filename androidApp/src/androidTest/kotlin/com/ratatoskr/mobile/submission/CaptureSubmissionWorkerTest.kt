package com.ratatoskr.mobile.submission

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueJitter
import com.ratatoskr.mobile.queue.QueueKeyGenerator
import com.ratatoskr.mobile.queue.QueueResult
import com.ratatoskr.mobile.queue.createAndroidQueuePersistence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class CaptureSubmissionWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun worker_uses_connected_unique_work_without_content_data() {
        val scheduler = WorkManagerSubmissionScheduler(context) { NOW.toEpochMilliseconds() }

        val request = scheduler.createRequest(null)

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(
            request.workSpec.input.keyValueMap
                .isEmpty(),
        )
        assertTrue(WorkManagerSubmissionScheduler.UNIQUE_WORK_NAME in request.tags)
    }

    @Test
    fun worker_honors_persisted_delay() {
        val scheduler = WorkManagerSubmissionScheduler(context) { NOW.toEpochMilliseconds() }

        val request = scheduler.createRequest(NOW + 5.minutes)

        assertEquals(5.minutes.inWholeMilliseconds, request.workSpec.initialDelay)
    }

    @Test
    fun process_recreation_resumes_queue() =
        runBlocking {
            val databaseName = "submission-worker-restart.db"
            context.deleteDatabase(databaseName)
            val first = queue(databaseName)
            val stored = first.enqueue(request()) as QueueResult.Success
            first.close()
            var observedLocalId: String? = null
            val drainer =
                QueueDrainer {
                    val reopened = queue(databaseName)
                    observedLocalId = reopened.inspect(stored.value.localId)?.localId
                    reopened.close()
                    QueueDrainOutcome.Idle
                }

            val result = worker(drainer).doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(stored.value.localId, observedLocalId)
            context.deleteDatabase(databaseName)
            Unit
        }

    @Test
    fun revocation_finishes_without_retry_storm() =
        runBlocking {
            var calls = 0
            val result =
                worker(
                    QueueDrainer {
                        calls += 1
                        QueueDrainOutcome.AuthRequired
                    },
                ).doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(1, calls)
        }

    private fun worker(drainer: QueueDrainer): CaptureSubmissionWorker =
        TestListenableWorkerBuilder
            .from(context, CaptureSubmissionWorker::class.java)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = CaptureSubmissionWorker(appContext, workerParameters, drainer)
                },
            ).build()

    private fun queue(name: String) =
        CaptureQueue(
            persistence = createAndroidQueuePersistence(context, name),
            clock = QueueClock { NOW },
            keyGenerator = QueueKeyGenerator { "key-${System.nanoTime()}" },
            jitter = QueueJitter { 0.0 },
        )

    private fun request() =
        CaptureRequest(
            owner = CaptureOwner("https://platform.example.test", "account-1"),
            source = CaptureSource.AndroidShareTarget,
            payload = CapturePayload.Url("https://example.test/article"),
            createdAt = NOW,
        )

    companion object {
        private val NOW = Instant.parse("2026-08-29T00:00:00Z")

        @JvmStatic
        @BeforeClass
        fun initializeWorkManager() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build(),
            )
        }
    }
}
