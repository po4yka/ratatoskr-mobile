## Purpose

Provide the shared, durable source of truth for explicit mobile captures before native share surfaces and submission workers depend on it.

## ADDED Requirements

### Requirement: Shared capture records preserve bounded explicit input

The client SHALL represent each capture as one immutable URL, text-note, or app-owned staged-file-reference payload with its original user input, capture source, creation time, owner scope, stable local identifier, and stable idempotency key. It SHALL reject non-HTTP(S) URLs, URLs longer than 2048 characters, empty or oversized text, and file references that are not opaque app-owned identifiers or that declare invalid sizes.

#### Scenario: Supported capture kinds round trip
- **WHEN** valid URL, text-note, and staged-file-reference captures are persisted and read through the capture queue
- **THEN** each returned record preserves its kind, original explicit content or opaque staged reference, source, owner scope, creation time, local identifier, and idempotency key

#### Scenario: Invalid capture input is rejected
- **WHEN** a caller enqueues a non-HTTP(S) URL, an over-2048-character URL, empty or over-limit text, or a file reference with a non-opaque identifier or invalid byte size
- **THEN** the queue rejects the capture without persisting a partial record or displacing an existing record

### Requirement: Enqueue is durable and idempotent

The client SHALL atomically assign and persist one local identifier, request fingerprint, source sequence, and idempotency key before enqueue succeeds. Reopening the same local store SHALL expose exactly the same values, and retrying an enqueue with an explicitly supplied existing key SHALL return the existing record only when the immutable request fingerprint matches.

#### Scenario: Queue survives a simulated process restart
- **WHEN** a capture is enqueued, the queue and database handles are closed, and a new queue instance opens the same store
- **THEN** the capture remains queued with the same payload, source sequence, local identifier, and idempotency key

#### Scenario: Idempotency key remains stable across restart and retry
- **WHEN** a restarted client reopens a queued capture and later retries its submission
- **THEN** every observation and claim presents the exact idempotency key assigned by the original committed enqueue

#### Scenario: Repeated matching enqueue converges
- **WHEN** an enqueue supplies an idempotency key already stored with the same immutable request fingerprint
- **THEN** the queue returns the existing capture and creates no second record

#### Scenario: Reused key with different content is refused
- **WHEN** an enqueue supplies an idempotency key already stored with a different immutable request fingerprint
- **THEN** the queue reports an idempotency conflict and leaves the existing record unchanged

### Requirement: Dequeue preserves source order and recovers abandoned work

The client SHALL make at most the oldest unfinished item in each source lane eligible for submission, SHALL select eligible lane heads deterministically, and SHALL claim an item atomically with a finite lease. A later item from one source MUST NOT pass its unfinished predecessor, while a delayed item MUST NOT block an eligible item from another source.

#### Scenario: Dequeue preserves FIFO within one source
- **WHEN** three captures from one source are enqueued in order and the queue claims ready work repeatedly
- **THEN** it returns them in source-sequence order and never claims a later item while its predecessor is unfinished

#### Scenario: Backoff in one source does not block another source
- **WHEN** the head item for one source is waiting for retry and another source has an eligible head item
- **THEN** the queue claims the eligible item from the other source without reordering either source lane

#### Scenario: Expired claim is recovered after restart
- **WHEN** a process terminates after claiming an item and a restarted queue observes that its lease has expired
- **THEN** the same item becomes eligible again with its original idempotency key and no duplicate record

### Requirement: Retry scheduling is bounded and durable

The client SHALL classify failures before retry, persist attempt count and next-eligible time transactionally, use capped exponential backoff with jitter for retryable failures, honor a later bounded server retry hint, and never automatically retry a permanent failure.

#### Scenario: Retry delay progresses and caps
- **WHEN** successive retryable attempts are recorded with deterministic time and jitter inputs
- **THEN** the persisted next-eligible delays follow the documented exponential progression and do not exceed the configured cap

#### Scenario: Server retry hint delays eligibility
- **WHEN** a retryable response supplies a valid later retry time within the configured bound
- **THEN** the item remains ineligible until at least that persisted retry time

#### Scenario: Permanent failure is not dequeued
- **WHEN** an item records a permanent validation, policy, size, or non-recoverable file failure
- **THEN** later dequeue calls do not return it unless an explicit future user action creates or requeues work

### Requirement: Server idempotency resolution converges or fails closed

The client SHALL bind an accepted queue item to the authoritative Platform operation returned for its persisted idempotency key. Repeating the same binding SHALL be a no-op; observing a different operation identifier for the same key SHALL stop automatic processing in an explicit conflict state without discarding either observed identifier.

#### Scenario: Previously accepted request converges after uncertain response
- **WHEN** a restarted client resubmits the same idempotency key and Platform resolves it to an existing operation
- **THEN** the queue binds the local capture to that operation and never creates or submits a replacement capture

#### Scenario: Repeated identical acceptance is idempotent
- **WHEN** the queue records the same operation identifier more than once for one idempotency key
- **THEN** one accepted binding remains and its state does not regress

#### Scenario: One key resolves to conflicting operations
- **WHEN** the queue has bound an idempotency key to one operation and later observes a different operation identifier for that key
- **THEN** it records an explicit resolution conflict, preserves both identifiers for diagnosis, and returns the item from no automatic dequeue path

### Requirement: Operation projections are monotonic and conservative

The client SHALL project generated Platform operation snapshots onto their bound captures using Platform-observed ordering fields, SHALL treat duplicate or older snapshots idempotently, and SHALL never replace a terminal projection with a non-terminal or conflicting projection. Unknown or invalid operation status data MUST fail closed instead of being guessed.

#### Scenario: Newer operation progress advances the projection
- **WHEN** a bound operation receives valid accepted, queued, running, and terminal snapshots with increasing Platform-observed change times
- **THEN** the capture exposes the newest valid status and terminal outcome

#### Scenario: Duplicate and out-of-order progress does not regress
- **WHEN** a terminal projection later receives a duplicate or older non-terminal snapshot
- **THEN** the stored terminal projection remains unchanged

#### Scenario: Conflicting equal-time progress is refused
- **WHEN** two snapshots for one operation carry the same Platform-observed change time but different statuses
- **THEN** the queue preserves the existing projection and reports a projection conflict without guessing which status is newer

### Requirement: Queue storage is bounded and owner scoped

The client SHALL enforce configured record and payload bounds without silently deleting unfinished user content, and SHALL return ready work only for the explicitly selected Ratatoskr instance and account scope.

#### Scenario: Full queue refuses a new capture safely
- **WHEN** an enqueue would exceed the configured unfinished-record or payload bound
- **THEN** the enqueue fails visibly and every previously stored capture remains unchanged

#### Scenario: Owner scope isolates ready work
- **WHEN** the store contains captures for more than one instance or account scope
- **THEN** a dequeue request for one scope returns no capture owned by another scope
