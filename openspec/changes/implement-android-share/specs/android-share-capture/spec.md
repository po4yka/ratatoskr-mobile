## Purpose

Provide Android users a safe explicit URL share path that stages untrusted `ACTION_SEND` input, persists confirmed work before networking, and returns later through truthful status and notification navigation.

## ADDED Requirements

### Requirement: Android accepts only bounded explicit text shares

The Android application SHALL advertise `ACTION_SEND` for `text/plain`, SHALL parse only the currently delivered explicit intent, and SHALL accept at most one bounded HTTP(S) URL for submission. It SHALL preserve the original shared text separately from the detected URL, SHALL preview bounded plain text that has no usable URL as unsupported by the current Platform capture contract, and SHALL reject unsupported actions, MIME types, missing payloads, multiple URLs, non-HTTP(S) schemes, and oversized text without creating queue work.

#### Scenario: Browser shares one URL
- **WHEN** another Android app sends `ACTION_SEND` with `text/plain` and one valid HTTP(S) URL in `EXTRA_TEXT`
- **THEN** Ratatoskr opens staging with that URL and preserves the original shared text for preview without enqueuing it yet

#### Scenario: Browser shares a title and one URL
- **WHEN** `EXTRA_TEXT` contains bounded display text and exactly one valid HTTP(S) URL
- **THEN** staging identifies the single URL as the submittable value and displays the original text without treating the title as canonical server content

#### Scenario: Plain text has no current submission contract
- **WHEN** `ACTION_SEND` carries bounded plain text with no valid HTTP(S) URL
- **THEN** staging displays the original text and a truthful unavailable message while submission remains disabled and no queue record is created

#### Scenario: Hostile or ambiguous share is refused
- **WHEN** an intent has an unsupported action or MIME type, no text, oversized text, a non-HTTP(S) URL, or more than one URL
- **THEN** Ratatoskr shows a safe actionable error and persists no capture

### Requirement: Staging requires explicit confirmation before durable enqueue

The shared staging surface SHALL show the detected URL, its original shared-text context, the current local/network submission state, and explicit confirm and cancel actions. Notes, tags, and collections SHALL NOT be offered while absent from the pinned Platform submission contract. Confirmation SHALL persist one Android-share-source capture before scheduling network work; cancellation SHALL persist nothing.

#### Scenario: User confirms a valid URL share
- **WHEN** a paired user confirms a valid staged URL
- **THEN** one `android_share_target` queue record is committed with the current owner scope and stable idempotency key before the surface reports it queued or requests background submission

#### Scenario: User cancels staging
- **WHEN** the user cancels a valid staged share before confirmation
- **THEN** the transient staging state closes and no capture is enqueued or submitted

#### Scenario: Device is offline after confirmation
- **WHEN** confirmation commits a valid URL while network submission cannot run
- **THEN** staging reports that the capture is safely queued for later delivery and the same queue record survives activity or process termination

### Requirement: Android submission follows durable queue and identity truth

Android background submission SHALL claim eligible work only through the durable queue, SHALL send URL captures through the authenticated public Platform capture endpoint with the record's persisted idempotency key, and SHALL persist acceptance or a classified failure before acknowledging the attempt. Work SHALL wait for connectivity and current capability authorization, SHALL retry only according to the durable queue schedule, and SHALL stop gracefully on revocation without deleting queued user content.

#### Scenario: Queued URL is accepted online
- **WHEN** connected authorized background work claims a queued URL and Platform accepts the matching idempotency key
- **THEN** the queue binds the returned operation identifier to that record and no replacement capture or key is created

#### Scenario: Connectivity fails during submission
- **WHEN** a claimed URL cannot reach Platform
- **THEN** the queue records a retryable connectivity outcome with its next eligible time and the Android scheduler arranges a later constrained attempt

#### Scenario: Platform rejects current authorization
- **WHEN** submission receives an authorization refusal and device-session refresh plus device-root recovery cannot restore authorization
- **THEN** credentials and capabilities are cleared, the queue record remains in auth-required state, automatic retries stop, and the app exposes re-pairing rather than losing the capture

#### Scenario: Permanent request failure is visible
- **WHEN** Platform definitively rejects the URL for validation or policy reasons
- **THEN** the queue records a permanent classified failure and neither Android nor the shared layer retries it automatically

### Requirement: Notifications open only validated operation detail

Android SHALL use a privacy-preserving notification channel for accepted or terminal capture work when notification permission is available. Each actionable notification SHALL contain no URL, note, title, token, or provider identity and SHALL use an explicit immutable navigation intent containing only a validated operation identifier; malformed or unauthorized identifiers SHALL not reveal status data.

#### Scenario: Accepted capture notification opens detail
- **WHEN** a submitted capture receives an operation identifier and the user taps its notification
- **THEN** Ratatoskr opens the shared operation-detail route for that identifier and loads current authorized Platform status

#### Scenario: Notification permission is unavailable
- **WHEN** Android cannot post a notification because permission is absent
- **THEN** capture submission and persisted status continue normally without claiming a notification was delivered

#### Scenario: Invalid deep link is received
- **WHEN** the activity receives a status-detail intent with a missing or malformed operation identifier
- **THEN** Ratatoskr refuses the route without issuing an operation request or exposing cached data
