## MODIFIED Requirements

### Requirement: Android accepts only bounded explicit text shares

The Android application SHALL advertise `ACTION_SEND` for `text/plain`, `application/pdf`,
`image/jpeg`, and `image/png`, SHALL parse only the currently delivered explicit intent, and SHALL
accept at most one bounded HTTP(S) URL or one supported file. It SHALL preserve original shared text
separately from a detected URL, SHALL copy a granted `content://` file into protected app-owned
staging while access is valid, and SHALL reject unsupported actions/types, missing payloads,
ambiguous URL/file combinations, non-HTTP(S) schemes, unreadable grants, unsafe files, and exceeded
text/file/capacity bounds without creating queue work.

#### Scenario: Browser shares one URL
- **WHEN** another Android app sends `ACTION_SEND` with `text/plain` and one valid HTTP(S) URL in `EXTRA_TEXT`
- **THEN** Ratatoskr opens staging with that URL and preserves the original shared text for preview without enqueuing it yet

#### Scenario: Browser shares a title and one URL
- **WHEN** `EXTRA_TEXT` contains bounded display text and exactly one valid HTTP(S) URL
- **THEN** staging identifies the single URL as the submittable value and displays the original text without treating the title as canonical server content

#### Scenario: Plain text has no current submission contract
- **WHEN** `ACTION_SEND` carries bounded plain text with no valid HTTP(S) URL and no file
- **THEN** staging displays the original text and a truthful unavailable message while submission remains disabled and no queue record is created

#### Scenario: Supported content URI is copied before confirmation
- **WHEN** `ACTION_SEND` carries one readable granted URI whose bounded content validates as PDF, JPEG, or PNG
- **THEN** Ratatoskr atomically copies it into protected opaque staging, releases the grant, and previews the sanitized name, type, size, and upload impact without enqueuing it yet

#### Scenario: Hostile or ambiguous share is refused
- **WHEN** an intent has an unsupported action or MIME type, missing content, oversized text/file, a non-HTTP(S) URL, multiple URLs/files, both a URL and file, an unreadable grant, or mismatched file evidence
- **THEN** Ratatoskr shows a safe actionable error, releases granted access, and persists no capture

### Requirement: Staging requires explicit confirmation before durable enqueue

The shared staging surface SHALL show the detected URL or staged file, original safe display context,
local usage/retention and upload impact for files, the current local/network/integration state, and
explicit confirm and cancel actions. Notes, tags, and collections SHALL NOT be offered while absent
from the pinned Platform submission contract. Confirmation SHALL persist one Android-share-source
capture before scheduling network work; cancellation SHALL persist no capture and SHALL remove only
the unreferenced app-owned staging artifact created for that draft.

#### Scenario: User confirms a valid URL share
- **WHEN** a paired user confirms a valid staged URL
- **THEN** one `android_share_target` queue record is committed with the current owner scope and stable idempotency key before the surface reports it queued or requests background submission

#### Scenario: User confirms a staged file
- **WHEN** a paired user confirms one valid protected staged file
- **THEN** one `android_share_target` staged-file queue record is committed before constrained upload work is requested and the surface does not claim Platform acceptance

#### Scenario: User cancels staging
- **WHEN** the user cancels a valid URL or file share before confirmation
- **THEN** the transient staging state closes, no capture is enqueued or submitted, and only an unreferenced app-owned draft file is removed

#### Scenario: Device is offline after confirmation
- **WHEN** confirmation commits a valid URL or file while network submission cannot run
- **THEN** staging reports that the capture is safely queued for later delivery and the same queue record survives activity or process termination

### Requirement: Android submission follows durable queue and identity truth

Android background submission SHALL claim eligible work only through the durable queue, SHALL send
URL captures through the authenticated public Platform capture endpoint, and SHALL drive staged
files only through the pinned resumable receipt contract when its public capability/binding is
available. Every request SHALL reuse persisted capture and transfer identity and record acceptance or
a classified failure before acknowledging the attempt. Background file work SHALL require connected
network, battery-not-low, and storage-not-low constraints, SHALL retry only according to durable
state, and SHALL stop and join the coordinated full local wipe on proven revocation.

#### Scenario: Queued URL is accepted online
- **WHEN** connected authorized background work claims a queued URL and Platform accepts the matching idempotency key
- **THEN** the queue binds the returned operation identifier to that record and no replacement capture or key is created

#### Scenario: Queued file resumes under constraints
- **WHEN** connected background work with sufficient battery/storage claims an interrupted staged-file capture and the public receipt binding is available
- **THEN** it reconciles receiver status, sends only missing chunks, and preserves the queue idempotency key and transfer declaration

#### Scenario: Connectivity fails during submission
- **WHEN** a claimed URL or file cannot reach Platform or its authorized receiver
- **THEN** durable state records a retryable connectivity outcome with its next eligible time and the Android scheduler arranges a later constrained attempt

#### Scenario: Platform rejects current authorization
- **WHEN** submission receives an authorization refusal and device-session refresh plus device-root recovery proves revocation
- **THEN** Android cancels scheduled/in-flight work, completes the full local wipe, and exposes an unpaired empty state without entering an authorization retry storm

#### Scenario: Permanent request failure is visible
- **WHEN** Platform or the receiver definitively rejects the capture for validation, integrity, size, or policy reasons
- **THEN** durable state records a permanent classified failure and neither Android nor the shared layer retries it automatically
