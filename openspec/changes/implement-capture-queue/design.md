## Context

See `proposal.md` for motivation and `specs/capture-queue/spec.md` for behavior. ADR-0001 already assigns capture models, queue rules, operation projection, and common persistence to KMP while keeping share intake, staging, file authority, and schedulers native. The shared module currently has generated Platform models and identity behavior but no database or capture code. Ratatoskr's development status permits one current schema only and forbids migrations.

The cross-repository `operation-progress` specification remains authoritative for the Platform snapshot. This change consumes the pinned generated `OperationSnapshot` and `OperationStatus` types and adds only a minimized local projection; it does not change the producer contract.

## Goals / Non-Goals

**Goals:**

- Give Android and iOS one public `CaptureQueue` behavior seam for enqueue, ready-work claims, retry outcomes, Platform acceptance, operation projection, and inspection by local identifier.
- Make every state-changing command transactional and safe to repeat after process death.
- Exercise the same queue contract against the real current-schema store on Android and iOS Simulator targets.
- Leave a narrow native database-path/file-protection seam that later share-target and Share Extension work can use without moving OS lifecycle into common code.

**Non-Goals:**

- Platform HTTP submission, upload, WorkManager/background URLSession, connectivity monitoring, or current-session authorization orchestration.
- Android Share Target, iOS Share Extension/App Group coordination, file staging, file cleanup, or feature UI.
- Collection/tag editing, social/GitHub routing, or any new wire contract.
- Database migration or backward-compatible schema support.

## Decisions

### 1. Test the queue through one public command/query seam

`CaptureQueue` is the behavior seam. It accepts immutable domain values and exposes enqueue,
claim-ready, failure outcome, Platform acceptance, operation-snapshot application, and inspection.
Callers never receive a DAO or issue SQL. Every claimed item carries an opaque claim token; failure
outcomes require that token so a late worker cannot overwrite a newer attempt. User-driven retry,
cancel, and deletion remain later application orchestration rather than implied methods in this
change.

Time, UUID/idempotency generation, and retry jitter are injected system-boundary inputs. Tests use known literals and deterministic clocks/entropy. The storage port is replaceable for fast common behavior tests, while Android instrumentation and iOS Simulator tests run the same public queue contract against the actual file-backed database and prove close/reopen behavior.

Alternatives considered:

- DAO-level tests would be smaller but couple the suite to tables and would not verify queue invariants.
- A coordinator composed from separately public transition helpers would expose too much state and make atomicity a caller responsibility.

### 2. Use Room 3.0.1 KMP as the current-schema store

ADR-0001 and the pinned version catalog select Room KMP. ADR-0004 will record this concrete queue decision. KSP generates the database implementation from common entities/DAOs, and the bundled SQLite driver gives Android and Kotlin/Native the same SQL behavior. Native factories provide an explicit file path and apply platform protection: an Android private, backup-disabled database and an iOS protected database path. A future Share Extension may pass an App Group path, but cross-process App Group coordination is plan item 5 and is not claimed here.

The database declares version 1 only, exports no migration history, installs no migration objects, and is always tested by creating the current schema from an empty file. During development, schema changes edit the current entities/DAO definition and recreate test databases.

Alternatives considered:

- SQLDelight also provides strong KMP persistence but conflicts with the already accepted Room stack and would add a second schema tool.
- DataStore is appropriate for small settings, not transactional multi-row ordering, leases, uniqueness, and indexed readiness queries.
- Platform-specific databases would duplicate the queue state machine and weaken identical restart behavior.

### 3. Store one normalized queue aggregate with database-enforced identity

The current schema stores the complete queue aggregate as deterministic JSON and mirrors the local
ID, idempotency key, instance/account scope, source lane, source sequence, state, and next-eligible
time as constrained/indexed columns. The serialized immutable portion includes creation time,
canonical equality representation, and exactly one payload variant; the mutable portion includes
attempt count, lease token/expiry, safe failure class, authoritative operation ID, conflicting
operation ID, and the minimized operation projection. The queue is bounded to 500 unfinished
records by default, so current common transitions deliberately materialize that bounded set inside
one transaction rather than duplicating transition logic in target-specific SQL.

Unique constraints cover idempotency key and `(owner scope, source lane, source sequence)`. Payload checks are also enforced by domain decoding so a malformed row fails closed. Transactions implement enqueue, claim, retry, acceptance, conflict, and projection updates. A process-local mutex prevents competing commands from racing within one app process; SQLite transactions and unique constraints remain the correctness boundary across connections.

The equality representation is deterministic canonical serialization of the immutable request fields, used only for exact comparison when a caller supplies an existing idempotency key. It is not presented as a cryptographic content hash and is not logged.

Alternatives considered:

- Separate draft, attempt, binding, and projection tables are useful once uploads and historical attempts exist, but add joins and lifecycle states not needed before plan items 4, 5, and 8.
- A blob-only table would make schema creation easy but prevent indexed ordering and database
  constraints; the mirrored columns retain those boundaries while keeping the aggregate decoder
  common across Android and iOS.

### 4. Define source lanes and deterministic ready-work selection

The common source vocabulary starts with `main_app`, `android_share_target`, and `ios_share_extension`. A source lane is that source plus owner scope. Enqueue assigns its next sequence inside the transaction. Only the oldest unfinished record in a lane can be ready; a retry wait therefore preserves FIFO for that source. Other lanes continue independently.

Among eligible lane heads, selection sorts by `nextEligibleAt`, then creation time, then local ID for a stable total order. Claiming changes the row to in-flight and writes a finite lease and claim token atomically. Expired in-flight work is eligible for reclaim with the original idempotency key. Completion from an expired token is rejected as stale.

Alternative considered: one global FIFO would be simpler but lets one unavailable source block unrelated explicit captures and does not meet the per-source requirement.

### 5. Use bounded equal-jitter exponential retry

The default retry policy uses a 30-second exponential ceiling for the first retry, doubles by attempt, and caps the local ceiling at six hours. Equal jitter chooses a delay in `[ceiling / 2, ceiling]`, which spreads clients while keeping the minimum progression non-decreasing. Tests inject the lower-bound jitter and assert 15 seconds, 30 seconds, 60 seconds, and the six-hour cap. Arithmetic saturates before overflow.

A valid server retry time later than the local schedule wins, bounded to 24 hours from the recorded attempt so corrupt input cannot park work indefinitely. Retry count is bounded (default 12); exhaustion becomes an explicit non-automatic failure. Validation, policy, size, and unrecoverable local-file failures never enter automatic backoff. Auth-required is durable but awaits the identity layer rather than consuming retries.

Alternative considered: full jitter permits a later attempt to have a shorter delay than an earlier attempt, making progression less predictable for this user-facing queue.

### 6. Treat transport as at-least-once and the server key as effect identity

The queue cannot guarantee one network invocation across a crash after Platform accepts a request but before the local commit. It guarantees reuse of the persisted idempotency key. A submitter in plan item 4 will retry that key; if Platform returns an existing operation, `recordAccepted` binds the capture to it and ends submission.

Recording the same key/operation pair again is a no-op. A different operation ID for the same key violates the expected Platform idempotency contract, so the item enters `resolution_conflict`, retains the original and conflicting IDs, and is excluded from automatic work. The client does not silently choose or manufacture another capture.

### 7. Persist only a conservative operation projection

The queue maps a valid generated snapshot into local operation ID, closed status, progress, retryable flag, safe stage, status-changed time, terminal time, and warning/error/result counts. It does not persist full result bodies or error detail. A snapshot must match the bound operation. Older or duplicate snapshots are ignored; different statuses at the same Platform-observed change time are a conflict; a terminal projection never regresses. Generated decoding rejects unknown closed statuses, so common code does not guess.

### 8. Bound content without silent eviction

`QueueLimits` centralizes maximum unfinished records, inline payload bytes, text bytes, URL length, staged-file declared size, and retry count. Production defaults are documented and tests use smaller injected values. When a limit would be exceeded, enqueue fails atomically and keeps all prior unfinished content. This item does not implement age-based deletion or staged-file retention; those require explicit user/cleanup policy in plan item 8.

The queue is always queried with an explicit canonical HTTPS instance origin and account identifier. Sign-out/revocation does not erase queued content, but a session for another scope cannot claim it.

## Risks / Trade-offs

- [Room/KSP or bundled SQLite behavior differs across targets] → pin versions, compile generated code on both targets, and run the file-backed contract suite on Android Emulator and iOS Simulator.
- [A crash can repeat an HTTP call] → document at-least-once transport, persist the key before work, and require Platform idempotency convergence before claiming exactly one effect.
- [A future iOS extension opens the database concurrently] → keep transactions and uniqueness in SQLite, accept an explicit database path, and defer App Group multi-process lifecycle proof to plan item 5.
- [The database contains user text and URLs] → keep it in OS-private/protected storage, exclude Android backup, minimize operation/error data, never log payloads or equality representations, and reserve stronger local encryption policy for an explicit security decision if OS protection proves insufficient.
- [Wall-clock changes affect persisted eligibility] → compute with saturating durations, never make a permanent failure ready, and prefer delaying work over early retry when stored time is suspicious.
- [A conflict state can require later user/support action] → fail closed and preserve safe identifiers; UI and manual resolution remain out of scope rather than silently discarding evidence.

## Migration Plan

1. Add Room/KSP/SQLite dependencies and the single current database definition.
2. Add common models/policies and the queue command layer in vertical red-green slices.
3. Add Android and iOS database factories plus file-backed close/reopen tests.
4. Extend the documented and hosted gates, add ADR-0004, and update architecture/data/testing documentation.
5. Sync the OpenSpec deltas and archive only after all tasks and the full gate are green.

There is no production data migration or server rollout. During the development phase, rollback is the previous commit plus recreation of the development database; no compatibility schema or fallback reader is retained.
