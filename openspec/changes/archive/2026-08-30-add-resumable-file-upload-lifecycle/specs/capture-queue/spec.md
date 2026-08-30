## ADDED Requirements

### Requirement: Staged-file transfers extend queue identity without replacing it

A staged-file capture SHALL bind at most one durable transfer checkpoint and verified receipt to
its existing local identifier, request fingerprint, source sequence, owner scope, and idempotency
key. Transfer interruption, session replacement, receipt reconciliation, Platform acceptance, and
process restart SHALL NOT mint a replacement capture or allow a later source item to pass the file.

#### Scenario: Restart preserves file transfer identity
- **WHEN** a staged-file upload is interrupted after one or more chunks and the queue store is reopened
- **THEN** the queue exposes the same capture, source sequence, staged artifact identifier, digest, transfer checkpoint, and idempotency key for resumption

#### Scenario: File receipt is accepted after upload
- **WHEN** a verified receipt is persisted and Platform later accepts it for an operation
- **THEN** that operation binds to the original queue record and cleanup can reclaim local bytes only after the durable binding no longer requires them

### Requirement: Complete local erasure invalidates all queue and transfer work

The queue SHALL participate in the replay-safe local erase lifecycle by rejecting new claims,
invalidating outstanding lease tokens, closing database handles, and deleting the current database
and sidecars before erasure completes. No stale foreground or background callback SHALL recreate a
record, checkpoint, or receipt from pre-erasure state.

#### Scenario: In-flight claim returns after erasure starts
- **WHEN** a worker holding a valid queue or transfer lease completes after the erase marker became durable
- **THEN** its callback is refused, no store is recreated, and no staged bytes or operation binding reappear

#### Scenario: Queue opens after interrupted erasure
- **WHEN** startup finds a pending erase marker and an old queue database or sidecar still exists
- **THEN** it completes erasure before constructing the queue and the resulting queue is empty
