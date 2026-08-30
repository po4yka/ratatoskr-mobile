# iOS Share Capture Specification

## Purpose

Allow an explicit iOS URL or text share to survive Share Extension termination, reach the main Ratatoskr app without hidden network work, and enter the existing durable capture and operation flow safely.

## Requirements

### Requirement: Share Extension accepts one bounded explicit URL or text item

The iOS Share Extension SHALL accept only an explicit share containing one semantic public HTTP(S)
URL, one bounded plain-text value, or one supported PDF/JPEG/PNG file no larger than 100 MiB. It
SHALL preserve original loaded values separately from detected URLs, SHALL stream-copy a file into
the protected App Group staging root while item-provider/security-scoped access is valid, and SHALL
reject missing, oversized, unsupported, unreadable, mismatched, or ambiguous input without silently
dropping part of the share. It SHALL NOT inspect clipboard, provider accounts, unrelated attachments,
or passive application state.

#### Scenario: Public URL representation is accepted
- **WHEN** one item provider supplies one absolute HTTP(S) public URL within the documented bounds
- **THEN** the extension produces one staged URL intake containing both the original representation and the validated URL

#### Scenario: Plain text containing one URL is accepted
- **WHEN** one bounded plain-text representation contains exactly one absolute HTTP(S) URL
- **THEN** the extension preserves the complete original text and stages the single detected URL

#### Scenario: Plain text without a URL is preserved
- **WHEN** one bounded plain-text representation contains no absolute URL
- **THEN** the extension stages it as preview-only text and does not claim that Platform can submit it

#### Scenario: Supported file representation is copied
- **WHEN** one item provider supplies a readable bounded PDF, JPEG, or PNG representation
- **THEN** the extension atomically publishes one protected opaque App Group artifact and envelope, releases scoped access, and records only sanitized display metadata plus size, type, and digest

#### Scenario: Ambiguous or hostile input is refused
- **WHEN** providers yield multiple semantic items, URL/file combinations, a non-HTTP(S) scheme, oversized or unsupported content, mismatched file evidence, traversal metadata, or a load/copy failure
- **THEN** the extension presents one safe failure, publishes no partial capture, releases scoped access, and completes or cancels its request accurately

### Requirement: App Group handoff is atomic, bounded, and restartable

The extension SHALL write each accepted intake as one bounded schema-validated envelope plus, for a
file, one opaque protected artifact in the configured App Group container using one stable handoff
identifier and atomic publish boundary. The main app SHALL ignore incomplete temporary files,
atomically claim both envelope and referenced artifact before presenting them, validate every path
inside reviewed roots, and retain or recover claimed data until cancellation or a matching durable
queue record is confirmed.

#### Scenario: Extension terminates after publishing
- **WHEN** the extension atomically publishes a valid URL/text envelope or file envelope/artifact and exits before the main app runs
- **THEN** a later main-app launch imports the same handoff identifier, payload metadata, protected file bytes when present, and capture time exactly once

#### Scenario: Write is interrupted before publish
- **WHEN** extension execution ends while only a temporary envelope or file copy exists
- **THEN** the main app imports no capture from it and cleanup later treats only that app-owned temporary material as an orphan

#### Scenario: Main app dies while committing the queue record
- **WHEN** the app is interrupted after enqueue may have succeeded but before the claimed envelope/artifact is reconciled
- **THEN** restart reuses the handoff identifier and capture time so confirmation converges on the same queue record, idempotency key, and staged artifact

#### Scenario: Envelope is malformed or exceeds its bound
- **WHEN** the App Group inbox contains a malformed, path-invalid, unknown-kind, unsupported-schema, oversized, digest-mismatched, or missing-artifact envelope
- **THEN** the importer refuses it without reading outside app-owned roots, exposes a safe local error, and leaves unrelated valid envelopes importable

### Requirement: Main-app confirmation owns durable submission

The extension SHALL perform no network request and SHALL NOT open the shared queue database. The
main app SHALL present imported input through shared staging, create no queue record before explicit
confirmation, and durably enqueue supported URLs or protected files with iOS Share Extension
provenance before requesting native background work. Text-only input SHALL remain visible but
non-submittable until the public Platform contract supports it. Cancellation SHALL remove only the
unreferenced claimed envelope/artifact.

#### Scenario: Confirmed URL enters the durable queue
- **WHEN** a paired user confirms an imported URL while the device is online or offline
- **THEN** one iOS Share Extension queue record with the handoff-derived idempotency key is committed before scheduling and the surface reports durable queued state

#### Scenario: Confirmed file enters the durable queue
- **WHEN** a paired user confirms one valid imported protected file
- **THEN** one staged-file queue record retains the handoff-derived identity and artifact digest before constrained transfer scheduling is requested

#### Scenario: User cancels imported input
- **WHEN** the user cancels a staged handoff before confirmation
- **THEN** no queue or Platform capture is created and only that app-owned handoff and unreferenced file are removed

#### Scenario: Queue commit fails
- **WHEN** local capacity, validation, protection, digest, or database failure prevents durable enqueue
- **THEN** no submission is scheduled, the handoff remains recoverable, and the user sees a safe retryable or permanent local error

### Requirement: iOS scheduling remains a wake-up mechanism

After a main-app queue commit, Ratatoskr SHALL submit, upload, and refresh through the existing
owner-scoped queue/device-session contracts during bounded foreground reconciliation or reviewed iOS
background execution. Background file work SHALL require network connectivity, SHALL defer in Low
Power Mode, and SHALL require external power above 32 MiB. Cancellation, expiration, duplication,
revocation, or process death SHALL NOT replace durable queue/upload identity or ordering.

#### Scenario: Offline capture is submitted later
- **WHEN** a confirmed URL or file is queued without connectivity and a later foreground or background opportunity occurs at or after persisted eligibility
- **THEN** the main app reuses stored capture/transfer identity and records Platform/receiver acceptance or a classified durable failure

#### Scenario: Background execution expires
- **WHEN** iOS expires submission work while a queue or transfer lease is active
- **THEN** execution stops promptly and a later invocation reconciles and resumes the same record after its persisted lease without minting replacement identity

#### Scenario: Battery policy defers file work
- **WHEN** iOS is in Low Power Mode or an eligible file above 32 MiB lacks external power
- **THEN** background transfer remains durably pending without incrementing attempts or changing the receipt declaration

#### Scenario: Device session is revoked
- **WHEN** submission or operation refresh proves that the paired device is revoked
- **THEN** iOS cancels background work and notifications, wipes Keychain plus app/App Group local stores and files, and exposes an unpaired empty state without an authorization retry storm

### Requirement: iOS app exposes shared operation status behavior

The iOS main app SHALL expose the existing shared operation list and detail presentation, including bounded visible-only polling, terminal-state monotonicity, offline and re-pairing states, and navigation from a newly accepted capture to its authoritative operation. Extension completion or upload progress SHALL NOT be presented as Platform operation success.

#### Scenario: Accepted capture opens operation detail
- **WHEN** a queued iOS capture receives a valid Platform acceptance with an operation identifier
- **THEN** the app can open the shared detail surface and render fixture progress through its distinct terminal outcome

#### Scenario: App leaves the foreground
- **WHEN** an iOS scene resigns active while a non-terminal operation detail is visible
- **THEN** automatic detail polling stops and later activation performs a bounded refresh

### Requirement: App Group and Keychain entitlements are consistent and privacy preserving

The application and Share Extension SHALL be embedded with the same reviewed App Group and Keychain access-group identifiers required by their handoff and signing configuration. Device credentials SHALL remain device-only and non-synchronizing, SHALL never be written to the App Group, and SHALL not be queried by the Share Extension's parse-stage-complete flow.

#### Scenario: Built targets carry matching groups
- **WHEN** the simulator application and embedded extension are built and their effective entitlements are inspected
- **THEN** both targets contain the configured App Group and Keychain access group while no wildcard or unrelated group is present

#### Scenario: Extension stages without credential access
- **WHEN** a synthetic URL share completes through the extension flow
- **THEN** the App Group envelope contains no origin, account identifier, token, device secret, capability document, or other credential material and no Keychain operation is required

#### Scenario: Shared Keychain policy remains device bound
- **WHEN** the app-hosted simulator storage suite saves and replaces a synthetic credential in the configured access group
- **THEN** the main app reads the replacement, synchronization remains disabled, and clear removes the item
