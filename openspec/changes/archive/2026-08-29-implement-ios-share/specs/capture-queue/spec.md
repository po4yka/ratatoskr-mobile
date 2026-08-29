## ADDED Requirements

### Requirement: iOS handoff identity converges with durable queue identity

An imported iOS Share Extension envelope SHALL supply one stable idempotency key and immutable capture time to the queue. Re-import, confirmation retry, application restart, foreground drain, and background drain SHALL reuse that identity and payload, while a different payload observed for the same handoff identifier SHALL fail closed as an idempotency conflict.

#### Scenario: Import repeats after uncertain local commit
- **WHEN** the main app re-imports a handoff after queue commit may have completed before process death
- **THEN** enqueue returns the existing matching queue record and no second capture or idempotency key is created

#### Scenario: Reused handoff identifier carries different content
- **WHEN** an App Group envelope presents a handoff identifier already bound to a different immutable request
- **THEN** the queue records no replacement and reports an idempotency conflict for safe local handling

### Requirement: Native delivery mechanisms share one queue truth

Foreground reconciliation and platform background schedulers on Android and iOS SHALL select only owner-scoped queue records whose persisted eligibility and source ordering permit work. Neither scheduler SHALL carry capture content or credentials as task metadata, and scheduler duplication or loss SHALL be recoverable from durable queue state.

#### Scenario: iOS scheduler wakes before eligibility
- **WHEN** iOS invokes background work before the next persisted eligible time
- **THEN** no request is submitted and the next eligible queue time remains authoritative

#### Scenario: Main app repairs a lost schedule
- **WHEN** a queued or tracking record exists after the operating system discarded a prior background request
- **THEN** later main-app activation discovers the record and requests or performs bounded eligible work without changing its identity
