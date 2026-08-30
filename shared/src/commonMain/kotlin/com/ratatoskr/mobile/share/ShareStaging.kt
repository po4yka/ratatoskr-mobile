package com.ratatoskr.mobile.share

import com.ratatoskr.mobile.capture.CaptureOwner
import com.ratatoskr.mobile.capture.CapturePayload
import com.ratatoskr.mobile.capture.CaptureRequest
import com.ratatoskr.mobile.capture.CaptureSource
import com.ratatoskr.mobile.queue.CaptureQueue
import com.ratatoskr.mobile.queue.QueueClock
import com.ratatoskr.mobile.queue.QueueResult
import com.ratatoskr.mobile.submission.SubmissionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun interface CurrentCaptureOwner {
    fun get(): CaptureOwner?
}

enum class ShareSubmissionAccess {
    Available,
    PairingRequired,
    CapabilityUnavailable,
}

sealed interface ShareStagingState {
    data class Ready(
        val originalText: String,
        val url: String?,
        val canSubmit: Boolean,
        val message: String?,
        val file: ShareIntake.File? = null,
    ) : ShareStagingState

    data object Saving : ShareStagingState

    data class Queued(
        val localId: String,
        val message: String,
    ) : ShareStagingState

    data object Cancelled : ShareStagingState

    data class Failed(
        val message: String,
    ) : ShareStagingState
}

sealed interface ShareStagingAction {
    data object Confirm : ShareStagingAction

    data object Cancel : ShareStagingAction
}

class ShareStagingStore(
    private val initialIntake: ShareIntake,
    private val owner: CurrentCaptureOwner,
    private val queue: CaptureQueue,
    private val scheduler: SubmissionScheduler,
    private val clock: QueueClock,
    private val scope: CoroutineScope,
    private val submissionAccess: StateFlow<ShareSubmissionAccess> =
        MutableStateFlow(ShareSubmissionAccess.Available),
    private val captureSource: CaptureSource = CaptureSource.AndroidShareTarget,
    private val captureCreatedAt: kotlin.time.Instant? = null,
    private val idempotencyKey: String? = null,
    private val onCommitted: (com.ratatoskr.mobile.queue.QueueRecord) -> Unit = {},
    private val onCancelled: () -> Unit = {},
    private val onFailure: () -> Unit = {},
) {
    private val mutableState =
        MutableStateFlow<ShareStagingState>(initialIntake.readyState(submissionAccess.value))
    val state: StateFlow<ShareStagingState> = mutableState.asStateFlow()
    private val submissionAccessJob =
        scope.launch {
            submissionAccess.collectLatest { access ->
                if (mutableState.value is ShareStagingState.Ready) {
                    mutableState.value = initialIntake.readyState(access)
                }
            }
        }

    fun close() = submissionAccessJob.cancel()

    fun dispatch(action: ShareStagingAction) {
        when (action) {
            ShareStagingAction.Cancel -> {
                if (mutableState.value is ShareStagingState.Ready) {
                    mutableState.value = ShareStagingState.Cancelled
                    onCancelled()
                }
            }
            ShareStagingAction.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val ready = mutableState.value as? ShareStagingState.Ready ?: return
        val payload =
            when (val intake = initialIntake) {
                is ShareIntake.Url -> CapturePayload.Url(intake.url)
                is ShareIntake.File ->
                    CapturePayload.FileReference(
                        stagedFileId = intake.stagedFileId,
                        displayName = intake.displayName,
                        mediaType = intake.mediaType,
                        byteSize = intake.byteSize,
                    )
                is ShareIntake.UnsupportedText -> return
            }
        val access = submissionAccess.value
        if (!ready.canSubmit || access != ShareSubmissionAccess.Available) {
            mutableState.value = initialIntake.readyState(access)
            return
        }
        val currentOwner = owner.get()
        if (currentOwner == null) {
            mutableState.value = ShareStagingState.Failed("Pair this device before submitting.")
            onFailure()
            return
        }
        mutableState.value = ShareStagingState.Saving
        scope.launch {
            when (
                val result =
                    queue.enqueue(
                        CaptureRequest(
                            owner = currentOwner,
                            source = captureSource,
                            payload = payload,
                            createdAt = captureCreatedAt ?: clock.now(),
                        ),
                        idempotencyKey = idempotencyKey,
                    )
            ) {
                is QueueResult.Failure -> {
                    mutableState.value = ShareStagingState.Failed("The capture could not be queued safely.")
                    onFailure()
                }
                is QueueResult.Success -> {
                    val isFile = initialIntake is ShareIntake.File
                    mutableState.value =
                        ShareStagingState.Queued(
                            localId = result.value.localId,
                            message =
                                if (isFile) {
                                    "Safely queued locally. File submission is integration pending."
                                } else {
                                    "Safely queued. Ratatoskr will submit it when online."
                                },
                        )
                    onCommitted(result.value)
                    if (!isFile) runCatching { scheduler.schedule(result.value.nextEligibleAt) }
                }
            }
        }
    }

    private fun ShareIntake.readyState(access: ShareSubmissionAccess): ShareStagingState.Ready =
        when (this) {
            is ShareIntake.Url ->
                ShareStagingState.Ready(
                    originalText = originalText,
                    url = url,
                    canSubmit = access == ShareSubmissionAccess.Available,
                    message = access.message(),
                )
            is ShareIntake.UnsupportedText ->
                ShareStagingState.Ready(
                    originalText = originalText,
                    url = null,
                    canSubmit = false,
                    message = "Platform does not currently accept plain text captures.",
                )
            is ShareIntake.File ->
                ShareStagingState.Ready(
                    originalText = displayName,
                    url = null,
                    canSubmit = access == ShareSubmissionAccess.Available,
                    message =
                        if (access == ShareSubmissionAccess.Available) {
                            "File submission is integration pending; the queued file stays local."
                        } else {
                            access.message()
                        },
                    file = this,
                )
        }

    private fun ShareSubmissionAccess.message(): String? =
        when (this) {
            ShareSubmissionAccess.Available -> null
            ShareSubmissionAccess.PairingRequired -> "Pair this device before submitting."
            ShareSubmissionAccess.CapabilityUnavailable ->
                "URL capture is unavailable for this Platform session."
        }
}
