# Device Identity Specification

## Purpose

Provide a secure mobile identity lifecycle that pairs one installation with Platform, keeps rotating credentials in operating-system secure storage, and exposes only currently authorized Platform capabilities to application layers.

## Requirements

### Requirement: A user-approved code pairs one mobile installation

The client SHALL exchange a user-approved Platform pairing code as device kind `mobile` against the explicitly configured HTTPS Platform origin, persist the returned device and session credentials before publishing paired state, and never include any credential value in diagnostics.

#### Scenario: Live matching code pairs successfully
- **WHEN** Platform accepts a live unused code for device kind `mobile` and returns the device root secret plus the first access and refresh credentials
- **THEN** the client stores one origin-bound credential set and exposes the installation as paired

#### Scenario: Malformed pairing request is rejected
- **WHEN** Platform rejects the pairing request as malformed
- **THEN** the client exposes a safe validation failure and stores no credential

#### Scenario: Unacceptable pairing code is uniformly refused
- **WHEN** Platform refuses a code because it is unknown, expired, spent, superseded, or kind-mismatched
- **THEN** the client exposes one indistinguishable pairing-refused outcome and stores no credential

#### Scenario: Pairing dependency is unavailable
- **WHEN** Platform reports that pairing wrote nothing because a dependency timed out
- **THEN** the client exposes a retryable unavailable outcome and stores no credential

#### Scenario: Pairing response is uncertain
- **WHEN** the pairing exchange ends without a trustworthy response
- **THEN** the client stores no credential, does not claim pairing succeeded, and requires a fresh user-approved code because the presented code may be spent

#### Scenario: Non-HTTPS origin is rejected locally
- **WHEN** a pairing attempt names an origin that is not a canonical HTTPS origin
- **THEN** the client refuses the attempt before sending the code or any credential

### Requirement: Device credentials remain in native secure storage

The client SHALL persist the complete origin-bound device credential set only through the native secure-storage implementation, SHALL exclude it from synchronizing backups and ordinary preferences, and SHALL replace or delete it as one logical record.

#### Scenario: Android secure-storage round trip
- **WHEN** an Android instrumentation test saves, loads, replaces, and deletes synthetic credentials
- **THEN** only ciphertext remains in app preferences, the encryption key remains in Android Keystore, replacement is complete, and deletion makes the record unavailable

#### Scenario: iOS secure-storage round trip
- **WHEN** an iOS Simulator test saves, loads, replaces, and deletes synthetic credentials
- **THEN** Keychain returns the complete current record, marks it device-only and non-synchronizing, and deletion makes the record unavailable

#### Scenario: Secure record is unreadable
- **WHEN** native secure storage reports a corrupt or inaccessible credential record
- **THEN** the client exposes a signed-out safe failure without disclosing or partially using record contents

### Requirement: Refresh credentials rotate atomically

The client SHALL serialize refresh and recovery, exchange each refresh link at most once, and persist the replacement access and refresh credentials together before publishing them.

#### Scenario: Refresh succeeds
- **WHEN** Platform accepts the current refresh link and returns replacement access and refresh credentials
- **THEN** the client atomically replaces both session credentials while retaining the same origin, user, device, and device root secret

#### Scenario: Concurrent callers need refresh
- **WHEN** multiple callers request valid authorization while the same access credential requires refresh
- **THEN** exactly one refresh exchange occurs and every caller observes the same replacement session

#### Scenario: Refresh response is uncertain
- **WHEN** the refresh exchange ends without a trustworthy response
- **THEN** the client never presents the same refresh link again and attempts one device-root session recovery instead

#### Scenario: Refresh is refused but device remains valid
- **WHEN** Platform refuses the refresh link and accepts one recovery login using the stored device identifier and root secret
- **THEN** the client replaces the session chain with the recovered credentials and remains paired

### Requirement: Revocation terminates the local session gracefully

The client SHALL treat refusal of both session refresh and device-root recovery as proven device
revocation, atomically publish an erase-pending boundary, disable authenticated work, and complete
the coordinated deletion of credentials, capabilities, queue data, transfer state, staged files,
App Group handoff data, caches, preferences, notifications, and native schedules before exposing an
unpaired empty state. An explicit local sign out SHALL still clear only credential and session-scoped
capability state unless the user separately confirms the destructive clear-data action.

#### Scenario: Device revoked elsewhere
- **WHEN** an authenticated request is refused, refresh cannot restore authorization, and Platform also refuses the stored device root secret
- **THEN** the client cancels local work, completes the replay-safe full local wipe, and exposes re-pairing with no prior account content or credential recoverable

#### Scenario: Explicit local sign out
- **WHEN** the user signs out this installation locally without confirming clear data
- **THEN** the client deletes its secure credential record and clears session-scoped capability state without claiming Platform revocation or deleting unrelated queued user data

### Requirement: Platform capabilities are session-scoped and conservative

The client SHALL fetch the authenticated public capability document for every newly paired or recovered session, cache the latest document only within the current device session, expose it to application layers, and never enable behavior from an absent, stale, or unfamiliar capability.

#### Scenario: Capability discovery succeeds
- **WHEN** Platform returns an authenticated capability document for the current session
- **THEN** application layers observe the sorted known capability set, minimum mobile client version, service staleness, and current-session freshness

#### Scenario: Familiar capability is absent
- **WHEN** a known capability name is absent from the current document
- **THEN** application layers observe that feature as unavailable

#### Scenario: Capability refresh fails
- **WHEN** a previously cached capability document cannot be refreshed for the active session
- **THEN** the client may expose the snapshot as stale context but exposes no cached capability as currently usable

#### Scenario: New session replaces capability cache
- **WHEN** pairing or device-root recovery opens a replacement session
- **THEN** the prior session's capability authorization is discarded before the new session's document is exposed
