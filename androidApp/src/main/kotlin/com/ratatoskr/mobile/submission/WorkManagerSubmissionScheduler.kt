package com.ratatoskr.mobile.submission

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

class WorkManagerSubmissionScheduler(
    context: Context,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : SubmissionScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(nextEligibleAt: Instant?) {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            createRequest(nextEligibleAt),
        )
    }

    internal fun createRequest(nextEligibleAt: Instant?): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CaptureSubmissionWorker>()
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setInitialDelay(
                (nextEligibleAt?.toEpochMilliseconds()?.minus(nowEpochMillis()) ?: 0L).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            ).addTag(UNIQUE_WORK_NAME)
            .build()

    companion object {
        const val UNIQUE_WORK_NAME = "ratatoskr-capture-submission"
    }
}
