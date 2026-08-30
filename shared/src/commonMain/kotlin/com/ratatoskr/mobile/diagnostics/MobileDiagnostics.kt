package com.ratatoskr.mobile.diagnostics

import co.touchlab.kermit.Logger

enum class MobileDiagnosticEvent {
    SearchRequested,
    SearchCompleted,
    LinkAccepted,
    LinkRejected,
    NotificationStateChanged,
    OperationRefreshFailed,
}

enum class MobileDiagnosticOutcome {
    Started,
    Succeeded,
    Rejected,
    Unavailable,
}

data class MobileDiagnosticRecord(
    val event: MobileDiagnosticEvent,
    val outcome: MobileDiagnosticOutcome,
)

class MobileDiagnostics(
    private val sink: (String) -> Unit = { message -> Logger.i(tag = "MobileDiagnostics") { message } },
) {
    fun record(
        event: MobileDiagnosticEvent,
        outcome: MobileDiagnosticOutcome,
    ): MobileDiagnosticRecord {
        val record = MobileDiagnosticRecord(event, outcome)
        sink("${event.name}:${outcome.name}")
        return record
    }
}
