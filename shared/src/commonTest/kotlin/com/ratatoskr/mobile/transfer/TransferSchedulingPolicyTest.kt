package com.ratatoskr.mobile.transfer

import kotlin.test.Test
import kotlin.test.assertEquals

class TransferSchedulingPolicyTest {
    private val policy = TransferSchedulingPolicy()

    @Test
    fun offline_defers() {
        assertEquals(
            TransferScheduleDecision.Defer(TransferDeferralReason.Offline),
            policy.decide(input(networkConnected = false)),
        )
    }

    @Test
    fun low_battery_or_low_power_defers_without_attempt() {
        assertEquals(
            TransferScheduleDecision.Defer(TransferDeferralReason.LowBattery),
            policy.decide(input(batteryNotLow = false)),
        )
        assertEquals(
            TransferScheduleDecision.Defer(TransferDeferralReason.LowPowerMode),
            policy.decide(input(platform = TransferPlatform.Ios, lowPowerMode = true)),
        )
    }

    @Test
    fun large_ios_transfer_requires_external_power() {
        assertEquals(
            TransferScheduleDecision.Defer(TransferDeferralReason.ExternalPowerRequired),
            policy.decide(input(platform = TransferPlatform.Ios, sizeBytes = 32L * 1024L * 1024L + 1)),
        )
    }

    @Test
    fun integration_pending_never_schedules() {
        assertEquals(
            TransferScheduleDecision.IntegrationPending("No public Platform blob-receipt binding is pinned"),
            policy.decide(input(availability = ProductionFileTransferAvailability().current())),
        )
    }

    @Test
    fun duplicate_wakeup_has_one_lease() {
        assertEquals(TransferScheduleDecision.RunNow, policy.decide(input()))
        assertEquals(
            TransferScheduleDecision.Defer(TransferDeferralReason.LeaseHeld),
            policy.decide(input(leaseAvailable = false)),
        )
    }

    private fun input(
        eligible: Boolean = true,
        sizeBytes: Long = 1,
        availability: FileTransferAvailability = FileTransferAvailability.Available,
        networkConnected: Boolean = true,
        batteryNotLow: Boolean = true,
        storageNotLow: Boolean = true,
        platform: TransferPlatform = TransferPlatform.Android,
        lowPowerMode: Boolean = false,
        externalPower: Boolean = false,
        leaseAvailable: Boolean = true,
    ) = TransferScheduleInput(
        eligible,
        sizeBytes,
        availability,
        networkConnected,
        batteryNotLow,
        storageNotLow,
        platform,
        lowPowerMode,
        externalPower,
        leaseAvailable,
    )
}
