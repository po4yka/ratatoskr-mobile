# Mobile File Transfer Lifecycle Specification

## Purpose

Provide explicit mobile file intake with protected bounded staging, contract-aligned resumable delivery, truthful local usage, constrained background work, and replay-safe complete erasure.

## Requirements

### Requirement: Explicit shared files become protected app-owned staged artifacts

Ratatoskr SHALL accept at most one explicitly shared PDF, JPEG, PNG, or bounded plain-text file no
larger than 100 MiB, SHALL validate the declared type against readable content where practical, and
SHALL stream-copy it through an app-owned temporary file into a protected opaque staging path before
external access ends. The client SHALL never retain an external URI, filename-derived path, or
security-scoped capability as durable upload authority.

#### Scenario: Supported file is staged atomically
- **WHEN** a supported readable file within the size bound is explicitly shared and its copy completes
- **THEN** one protected app-owned artifact with an opaque identifier, sanitized display name, exact byte length, media type, and SHA-256 digest becomes available for confirmation while the external grant is released

#### Scenario: Copy is interrupted
- **WHEN** the process or extension stops before the staged copy is atomically published
- **THEN** no queue record references the incomplete bytes and a later cleanup classifies only that app-owned temporary file as an expirable orphan

#### Scenario: Shared file is unsafe or unreadable
- **WHEN** the input is too large, has an unsupported or mismatched type, cannot be read completely, resolves outside the granted item, or would exceed local staging capacity
- **THEN** Ratatoskr creates no durable capture, releases external access, and exposes a safe size, type, access, or capacity failure without silently omitting the file

### Requirement: Blob-transfer contracts are pinned and generated reproducibly

The client SHALL pin the exact `ratatoskr-contracts` producer revision and digests for the six
canonical upload declaration, session-opened, chunk-receipt, status, finalize, and completion
schemas and valid fixtures. Shared Kotlin wire types SHALL be generated deterministically from that
pin and SHALL reject unknown or malformed trust-boundary values rather than inventing protocol
state. Until a public transfer-failure wire schema exists, transport failures SHALL map into a
bounded local fail-closed classification rather than a fabricated contract type.

#### Scenario: Pinned transfer contracts reproduce generated types
- **WHEN** contract generation runs twice from the committed schema set and fixture lock
- **THEN** both generated trees and fixture inventories are byte-for-byte identical to the committed transfer contract tree

#### Scenario: Mutated transfer material is rejected
- **WHEN** a temporary test changes a pinned schema, fixture, lock digest, or generated transfer type without changing the complete reviewed pin
- **THEN** the contract drift check exits non-zero and identifies the mismatched transfer material

### Requirement: Resumable delivery reconciles receiver truth before sending missing chunks

For one staged artifact the client SHALL durably preserve the capture identity, whole-file digest,
declared size, media type, chunk size, opaque resumption token, expiry, and latest safe transfer
projection. After an interruption or uncertain response it SHALL query receiver status first, send
only the missing zero-based chunks with their exact derived lengths, and SHALL stream each chunk
without loading the whole file into memory. Session expiry SHALL open a replacement session for the
same immutable file declaration and capture idempotency key rather than minting a new capture.

#### Scenario: Upload resumes after acknowledged interruption
- **WHEN** two chunks were accepted, the client stopped before completing the file, and receiver status reports those indices after restart
- **THEN** the resumed attempt sends only the remaining indices in order, preserves the original digest and capture idempotency key, and records no duplicate local capture

#### Scenario: Local checkpoint missed an accepted chunk
- **WHEN** a receiver accepted a chunk but the process ended before the local checkpoint committed
- **THEN** status reconciliation treats the receiver's recorded index as authoritative and does not resend or guess a gap

#### Scenario: Session expired during suspension
- **WHEN** the stored resumption token is expired and the staged bytes still match their declaration
- **THEN** the client opens a new session for the same digest, size, media type, chunk size, and capture identity and restarts receiver reconciliation without replacing the queued capture

#### Scenario: Staged bytes changed
- **WHEN** the app-owned file no longer matches its persisted size or SHA-256 digest before a resumed chunk is sent
- **THEN** automatic transfer stops in a permanent local-integrity failure and no changed bytes are uploaded under the original declaration

### Requirement: Completion requires a verified receiver receipt and Platform acceptance

The client SHALL finalize only after receiver status reports every derived chunk, SHALL verify the
completion receipt against the original size, media type, and SHA-256 digest, and SHALL preserve the
receipt until the associated Platform capture is durably accepted. A sealed upload alone MUST NOT
be displayed as a completed Ratatoskr operation. When the current capability document or pinned
Platform contract exposes no public receipt route, production file submission SHALL remain visibly
integration-pending while the deterministic receiver harness remains test evidence only.

#### Scenario: Final receipt matches the staged file
- **WHEN** all chunks are recorded and finalize returns a stored receipt with the declared digest, size, and media type
- **THEN** the transfer becomes uploaded, the receipt is persisted, and the queued capture remains pending until Platform accepts that receipt for an operation

#### Scenario: Finalize response is uncertain
- **WHEN** finalize may have succeeded but its response was lost
- **THEN** the next attempt reconciles receiver status or completion with the same session/declaration and does not create a replacement upload or claim failure prematurely

#### Scenario: Public receipt binding is unavailable
- **WHEN** the active Platform capability and pinned public contract expose no mobile file-receipt submission route
- **THEN** Ratatoskr keeps the staged capture durable, labels live integration pending, performs no guessed internal-service request, and does not present the fixture receiver outcome as live upload success

### Requirement: Local retention is bounded, truthful, and reference safe

The client SHALL enforce a default 512 MiB and 64-artifact staging admission bound, SHALL expose
total, queued, uploading, receipt-pending, reclaimable, and capacity-limit usage without content in
diagnostics, and SHALL never auto-delete bytes still referenced by queued, in-flight, uncertain, or
receipt-pending work. It SHALL remove accepted/cancelled artifacts when no local reference needs
them, expire incomplete temporary copies after 24 hours, and retain terminal failed artifacts for a
visible seven-day recovery window. If protected content cannot be reclaimed safely, new staging
SHALL fail visibly instead of evicting unfinished content.

#### Scenario: Cleanup removes only eligible artifacts
- **WHEN** cleanup sees an accepted artifact, an old incomplete temporary copy, a seven-day terminal failure, and an unfinished queued artifact
- **THEN** it removes the first three, preserves the queued bytes and ledger entry, and reports the resulting usage exactly

#### Scenario: Capacity is exhausted by unfinished work
- **WHEN** staging a new file would exceed the artifact or byte bound and no eligible artifact can be reclaimed
- **THEN** staging is refused with current and limit usage while every existing unfinished artifact remains intact

#### Scenario: Cleanup is interrupted
- **WHEN** the process ends after bytes or ledger state are partially removed
- **THEN** the next inventory converges by app-owned opaque identifiers, never reads outside staging roots, and never makes a live queue reference silently unreadable

### Requirement: Background scheduling decisions honor connectivity and energy constraints

The shared scheduling projection SHALL derive work only from durable queue/upload eligibility and
SHALL require connectivity, sufficient local storage, and a non-low-battery state for background
file transfer. Transfers larger than 32 MiB SHALL additionally require external power on iOS and
SHALL remain deferred during iOS Low Power Mode. Native schedulers SHALL carry only opaque work
identity, SHALL stop promptly on cancellation/expiration/revocation, and SHALL be repairable from
durable state after schedule loss or duplication.

#### Scenario: Offline background work is deferred
- **WHEN** an eligible file upload has no network connectivity
- **THEN** Android and iOS scheduling decisions request no transfer attempt before connectivity returns and the durable upload remains eligible

#### Scenario: Battery constraint is mapped by platform
- **WHEN** an eligible background upload is evaluated while Android reports low battery or iOS reports Low Power Mode, or an iOS file above 32 MiB lacks external power
- **THEN** the native adapter defers transfer without incrementing the durable attempt count or changing upload identity

#### Scenario: Duplicate wake-up is harmless
- **WHEN** foreground reconciliation and a native background wake-up target the same eligible upload
- **THEN** at most one durable lease performs work and the duplicate invocation observes or repairs the persisted schedule without sending a second chunk stream

### Requirement: Proven revocation and confirmed clear-data erase every local store

Before any new repository or worker opens, the client SHALL complete a replay-safe erase marker left
by an interrupted wipe. A proven remote device revocation SHALL start that wipe automatically. A
local clear-data action SHALL show an explicit destructive confirmation naming queued captures and
staged-byte usage before starting the same wipe. Completion SHALL require cancellation of native
work/notifications and deletion of credentials, capability/session state, Room databases and
sidecars, transfer journals/receipts, staged and temporary files, App Group inbox/claims, caches,
preferences, and in-memory authorized projections. It SHALL NOT issue an incidental server-content
deletion request.

#### Scenario: Remote revocation wipes local content
- **WHEN** refresh and device-root recovery prove the device was revoked elsewhere while captures and staged files exist
- **THEN** native work stops, every listed local store and file is erased, startup exposes an unpaired empty state, and no queued capture or resumption token remains recoverable

#### Scenario: User confirms clear data
- **WHEN** the user reviews exact local queue/item/byte counts and confirms clear data
- **THEN** the same complete local wipe runs and reports completion only after a post-wipe inventory finds no owned credential, database, staged, App Group, preference, cache, or scheduled-work residue

#### Scenario: User cancels clear data
- **WHEN** the destructive confirmation is dismissed
- **THEN** no local store, credential, schedule, notification, or staged artifact is changed

#### Scenario: Process dies during wipe
- **WHEN** termination occurs after the erase marker is durable but before all local resources are deleted
- **THEN** the next launch performs no pairing, queue, upload, or library work until it finishes the same idempotent inventory-based wipe and removes the marker last
