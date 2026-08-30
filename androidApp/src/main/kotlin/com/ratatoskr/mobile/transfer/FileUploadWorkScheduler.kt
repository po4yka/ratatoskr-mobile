package com.ratatoskr.mobile.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class FileUploadWorkScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(
        opaqueOwnerWorkKey: String,
        generation: String,
    ) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(opaqueOwnerWorkKey),
            ExistingWorkPolicy.KEEP,
            createRequest(opaqueOwnerWorkKey, generation),
        )
    }

    internal fun createRequest(
        opaqueOwnerWorkKey: String,
        generation: String,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<FileUploadWorker>()
            .setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            ).setInputData(
                Data
                    .Builder()
                    .putString(INPUT_OWNER_WORK_KEY, opaqueOwnerWorkKey)
                    .putString(INPUT_GENERATION, generation)
                    .build(),
            ).addTag(FILE_UPLOAD_TAG)
            .build()

    fun cancel(opaqueOwnerWorkKey: String) {
        workManager.cancelUniqueWork(uniqueWorkName(opaqueOwnerWorkKey))
    }

    private fun uniqueWorkName(opaqueOwnerWorkKey: String) = "$FILE_UPLOAD_TAG:$opaqueOwnerWorkKey"

    companion object {
        const val FILE_UPLOAD_TAG = "ratatoskr-file-upload"
        const val INPUT_OWNER_WORK_KEY = "owner_work_key"
        const val INPUT_GENERATION = "erase_generation"
    }
}

class FileUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val ownerWorkKey = inputData.getString(FileUploadWorkScheduler.INPUT_OWNER_WORK_KEY) ?: return Result.failure()
        val capturedGeneration = inputData.getString(FileUploadWorkScheduler.INPUT_GENERATION) ?: return Result.failure()
        val provider = applicationContext as? FileUploadDrainerProvider ?: return Result.failure()
        if (provider.currentEraseGeneration() != capturedGeneration) return Result.success()
        val outcome = provider.drainFileUpload(ownerWorkKey)
        if (provider.currentEraseGeneration() != capturedGeneration) return Result.success()
        return when (outcome) {
            FileUploadDrainOutcome.Complete,
            FileUploadDrainOutcome.IntegrationPending,
            -> Result.success()

            FileUploadDrainOutcome.Retry -> Result.retry()
        }
    }
}

interface FileUploadDrainerProvider {
    fun currentEraseGeneration(): String

    suspend fun drainFileUpload(opaqueOwnerWorkKey: String): FileUploadDrainOutcome
}

enum class FileUploadDrainOutcome {
    Complete,
    Retry,
    IntegrationPending,
}

class FileUploadWorkGenerationFence(
    private val currentGeneration: () -> String,
) {
    fun accepts(capturedGeneration: String): Boolean = capturedGeneration == currentGeneration()
}
