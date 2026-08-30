package com.ratatoskr.mobile.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MobileDiagnosticsTest {
    @Test
    fun private_canaries_cannot_enter_event_or_outcome_records() {
        val output = mutableListOf<String>()
        val diagnostics = MobileDiagnostics(output::add)
        val record = diagnostics.record(MobileDiagnosticEvent.LinkRejected, MobileDiagnosticOutcome.Rejected)
        val privateCanaries =
            listOf(
                "private-search-canary",
                "https://private.example.test",
                "private title",
                "private note",
                "private-file.pdf",
                "private-user@example.test",
            )

        assertEquals(MobileDiagnosticEvent.LinkRejected, record.event)
        assertEquals(MobileDiagnosticOutcome.Rejected, record.outcome)
        privateCanaries.forEach { canary ->
            assertFalse(output.joinToString().contains(canary), canary)
            assertFalse(record.toString().contains(canary), canary)
        }
    }
}
