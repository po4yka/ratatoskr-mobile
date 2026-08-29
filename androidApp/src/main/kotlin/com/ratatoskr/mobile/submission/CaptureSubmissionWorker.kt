package com.ratatoskr.mobile.submission

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

enum class QueueDrainOutcome {
    Idle,
    MoreWork,
    AuthRequired,
}

fun interface QueueDrainer {
    suspend fun drain(): QueueDrainOutcome
}

interface QueueDrainerProvider {
    val queueDrainer: QueueDrainer
}

class CaptureSubmissionWorker(
    appContext: Context,
    params: WorkerParameters,
    private val drainer: QueueDrainer,
) : CoroutineWorker(appContext, params) {
    constructor(appContext: Context, params: WorkerParameters) :
        this(
            appContext,
            params,
            (appContext.applicationContext as QueueDrainerProvider).queueDrainer,
        )

    override suspend fun doWork(): Result {
        drainer.drain()
        return Result.success()
    }
}
