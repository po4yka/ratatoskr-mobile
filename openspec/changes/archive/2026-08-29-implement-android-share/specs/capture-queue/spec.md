## ADDED Requirements

### Requirement: Submission orchestration preserves durable queue decisions

Every automatic URL submission SHALL begin from an atomic queue claim and SHALL finish by recording Platform acceptance or a classified failure against that claim before additional work in the same source lane can proceed. The submitter SHALL reuse the persisted idempotency key and immutable URL, SHALL never mint replacement identity after an uncertain outcome, and SHALL schedule no attempt earlier than the queue's persisted eligibility time.

#### Scenario: Worker restarts after uncertain acceptance
- **WHEN** Android work stops after Platform may have accepted a request but before local acceptance is recorded
- **THEN** the next attempt claims the same record and resubmits the same body with the exact persisted idempotency key until Platform returns the authoritative operation

#### Scenario: Durable retry time controls native scheduling
- **WHEN** a retryable result records a future next-eligible time
- **THEN** native background work does not submit that item before the stored time and a later process can recover the schedule from queue state

### Requirement: Accepted operations remain refreshable without resubmission

The queue SHALL expose owner-scoped accepted or tracking records for operation refresh separately from records eligible for capture submission. Applying a valid newer Platform snapshot SHALL update the persisted projection and terminal state, while duplicate, older, conflicting, or cross-owner observations SHALL not trigger a replacement submission or status regression.

#### Scenario: Accepted queue record is refreshed to completion
- **WHEN** status work reads a newer terminal snapshot for an accepted record's operation identifier
- **THEN** the queue stores the terminal projection, marks the record completed, and does not resubmit its capture

#### Scenario: Refresh work restarts before completion
- **WHEN** Android process death interrupts tracking of a non-terminal accepted operation
- **THEN** a later owner-scoped refresh discovers the persisted operation identifier and continues status retrieval without changing the capture idempotency key
