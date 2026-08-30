package com.ratatoskr.mobile.transfer

enum class TransferPlatform {
    Android,
    Ios,
}

enum class TransferDeferralReason {
    NotEligible,
    Offline,
    LowBattery,
    LowPowerMode,
    ExternalPowerRequired,
    LowStorage,
    LeaseHeld,
}

data class TransferScheduleInput(
    val eligible: Boolean,
    val sizeBytes: Long,
    val availability: FileTransferAvailability,
    val networkConnected: Boolean,
    val batteryNotLow: Boolean,
    val storageNotLow: Boolean,
    val platform: TransferPlatform,
    val lowPowerMode: Boolean = false,
    val externalPower: Boolean = false,
    val leaseAvailable: Boolean = true,
)

sealed interface TransferScheduleDecision {
    data object RunNow : TransferScheduleDecision

    data class Defer(
        val reason: TransferDeferralReason,
    ) : TransferScheduleDecision

    data class IntegrationPending(
        val reason: String,
    ) : TransferScheduleDecision
}

class TransferSchedulingPolicy {
    fun decide(input: TransferScheduleInput): TransferScheduleDecision =
        when {
            input.availability == FileTransferAvailability.IntegrationPending ->
                TransferScheduleDecision.IntegrationPending("No public Platform blob-receipt binding is pinned")
            !input.eligible -> TransferScheduleDecision.Defer(TransferDeferralReason.NotEligible)
            !input.networkConnected -> TransferScheduleDecision.Defer(TransferDeferralReason.Offline)
            !input.storageNotLow -> TransferScheduleDecision.Defer(TransferDeferralReason.LowStorage)
            !input.batteryNotLow -> TransferScheduleDecision.Defer(TransferDeferralReason.LowBattery)
            input.platform == TransferPlatform.Ios && input.lowPowerMode ->
                TransferScheduleDecision.Defer(TransferDeferralReason.LowPowerMode)
            input.platform == TransferPlatform.Ios &&
                input.sizeBytes > LARGE_IOS_TRANSFER_BYTES &&
                !input.externalPower ->
                TransferScheduleDecision.Defer(TransferDeferralReason.ExternalPowerRequired)
            !input.leaseAvailable -> TransferScheduleDecision.Defer(TransferDeferralReason.LeaseHeld)
            else -> TransferScheduleDecision.RunNow
        }

    private companion object {
        const val LARGE_IOS_TRANSFER_BYTES = 32L * 1024L * 1024L
    }
}
