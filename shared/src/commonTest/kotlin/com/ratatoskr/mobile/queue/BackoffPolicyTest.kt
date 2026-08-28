package com.ratatoskr.mobile.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BackoffPolicyTest {
    private val now = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun retry_delay_progresses_with_equal_jitter_and_caps() {
        val policy = BackoffPolicy(maxAttempts = 100, jitter = QueueJitter { 0.0 })

        assertEquals(now + 15.seconds, policy.schedule(1, QueueFailure.Connectivity, now).nextEligibleAt)
        assertEquals(now + 30.seconds, policy.schedule(2, QueueFailure.Connectivity, now).nextEligibleAt)
        assertEquals(now + 60.seconds, policy.schedule(3, QueueFailure.Connectivity, now).nextEligibleAt)
        val upperBoundPolicy = BackoffPolicy(maxAttempts = 100, jitter = QueueJitter { 1.0 })
        assertEquals(now + 6.hours, upperBoundPolicy.schedule(40, QueueFailure.Connectivity, now).nextEligibleAt)
    }

    @Test
    fun later_server_hint_delays_retry_within_bound() {
        val policy = BackoffPolicy(jitter = QueueJitter { 0.0 })

        assertEquals(
            now + 4.hours,
            policy.schedule(1, QueueFailure.RateLimited, now, now + 4.hours).nextEligibleAt,
        )
        assertEquals(
            now + 24.hours,
            policy.schedule(1, QueueFailure.RateLimited, now, now + 48.hours).nextEligibleAt,
        )
    }

    @Test
    fun permanent_failure_has_no_retry_time() {
        val policy = BackoffPolicy(jitter = QueueJitter { 0.0 })

        listOf(QueueFailure.Validation, QueueFailure.Policy, QueueFailure.Size, QueueFailure.LocalFile).forEach {
            assertNull(policy.schedule(1, it, now).nextEligibleAt)
        }
        assertNull(policy.schedule(1, QueueFailure.Authentication, now).nextEligibleAt)
        assertTrue(policy.schedule(12, QueueFailure.Connectivity, now).exhausted)
    }
}
